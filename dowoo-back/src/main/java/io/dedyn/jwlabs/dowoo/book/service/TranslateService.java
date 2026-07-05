package io.dedyn.jwlabs.dowoo.book.service;

import io.dedyn.jwlabs.dowoo.auth.service.CurrentUserProvider;
import io.dedyn.jwlabs.dowoo.book.entity.Chapter;
import io.dedyn.jwlabs.dowoo.book.repository.ChapterRepository;
import io.dedyn.jwlabs.dowoo.common.exception.ApiException;
import io.dedyn.jwlabs.dowoo.library.entity.Novel;
import io.dedyn.jwlabs.dowoo.library.entity.NovelPrompt;
import io.dedyn.jwlabs.dowoo.library.repository.NovelPromptRepository;
import io.dedyn.jwlabs.dowoo.library.repository.NovelRepository;
import io.dedyn.jwlabs.dowoo.library.support.DefaultPrompts;
import io.dedyn.jwlabs.dowoo.settings.crypto.ApiKeyCipher;
import io.dedyn.jwlabs.dowoo.settings.entity.ApiKey;
import io.dedyn.jwlabs.dowoo.settings.entity.ApiKeySetting;
import io.dedyn.jwlabs.dowoo.settings.repository.ApiKeyRepository;
import io.dedyn.jwlabs.dowoo.settings.repository.ApiKeySettingRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** api-spec.md §7.1 구현 - AI API의 POST /internal/translate/stream(§9.2)을 호출해 SSE를 그대로 릴레이한다. */
@Service
public class TranslateService {

    // 사용자가 모델을 지정하지 않으면(설정 미지정=자동) 이 순서로 시도한다 - 무료 티어에서 안정적으로
    // 쓸 수 있는 모델 우선. 유료 전용 모델(Pro 계열)은 자동 목록에서 제외한다.
    // 주의: "gemini-3-flash"(비-preview, stable 이름)는 아직 API에 실제로 존재하지 않아 404가 나므로
    // 반드시 "gemini-3-flash-preview"를 써야 한다(2026-07-05 확인). preview 모델이 무료 티어에서
    // 결제 없이도 동작하는지는 키마다 다를 수 있는데, 여기서 실패해도 gemini_client의 빈 응답/미번역
    // 감지 로직이 자동으로 다음 모델(gemini-2.5-flash)로 넘어가므로 자동 목록에 남겨도 안전하다.
    private static final List<String> DEFAULT_MODEL_FALLBACK =
            List.of("gemini-3.1-flash-lite", "gemini-3-flash-preview", "gemini-2.5-flash", "gemini-3.5-flash");

    // 내부망 커넥션 연결이라 짧게 잡아도 충분하다.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    // 번역 스트림 유휴(idle) 타임아웃 - 전체 응답 시간이 아니라 "이 시간 동안 새 데이터가 하나도
    // 안 오면" 끊는다(긴 챕터는 정상적으로 몇 분씩 걸릴 수 있어 전체 시간에는 상한을 못 둠). 본문이
    // 아주 길면 Gemini가 첫 줄을 스트리밍하기 전까지 "생각"만 하는 구간(하트비트 없음)이 몇 분씩
    // 걸릴 수 있어 넉넉하게 잡는다(docs/troubleshooting/18 참고).
    private static final Duration IDLE_TIMEOUT = Duration.ofSeconds(300);

    private final ChapterRepository chapterRepository;
    private final NovelRepository novelRepository;
    private final NovelPromptRepository novelPromptRepository;
    private final ApiKeySettingRepository apiKeySettingRepository;
    private final ApiKeyRepository apiKeyRepository;
    private final ApiKeyCipher apiKeyCipher;
    private final CurrentUserProvider currentUserProvider;
    private final JsonMapper objectMapper;
    private final String aiApiBaseUrl;
    private final String internalToken;

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService watchdogScheduler = Executors.newSingleThreadScheduledExecutor();
    // HTTP/2 cleartext 업그레이드 시도가 uvicorn(HTTP/1.1 전용)과 충돌하는 문제 회피 (HttpCrawlClient 참고).
    // 번역은 스트리밍이라 전체 응답 시간에 상한을 걸 수 없으므로(긴 챕터는 정상적으로 몇 분씩 걸림),
    // connectTimeout만 짧게 걸고 "얼마나 오래 걸리는지"가 아니라 "얼마나 오래 새 데이터가 안 오는지"는
    // relaySse의 유휴(idle) 타임아웃 워치독으로 별도 처리한다.
    private final HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    public TranslateService(
            ChapterRepository chapterRepository,
            NovelRepository novelRepository,
            NovelPromptRepository novelPromptRepository,
            ApiKeySettingRepository apiKeySettingRepository,
            ApiKeyRepository apiKeyRepository,
            ApiKeyCipher apiKeyCipher,
            CurrentUserProvider currentUserProvider,
            JsonMapper objectMapper,
            @Value("${app.ai-api-base-url}") String aiApiBaseUrl,
            @Value("${app.internal-token}") String internalToken) {
        this.chapterRepository = chapterRepository;
        this.novelRepository = novelRepository;
        this.novelPromptRepository = novelPromptRepository;
        this.apiKeySettingRepository = apiKeySettingRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.apiKeyCipher = apiKeyCipher;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.aiApiBaseUrl = aiApiBaseUrl;
        this.internalToken = internalToken;
    }

    public SseEmitter translate(UUID novelId, UUID chapterId) {
        UUID userId = currentUserProvider.currentUserId();

        novelRepository.findByIdAndUserId(novelId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "소설을 찾을 수 없습니다."));
        Chapter chapter = chapterRepository.findByIdAndNovelId(chapterId, novelId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "챕터를 찾을 수 없습니다."));

        List<ApiKey> apiKeys = apiKeyRepository.findByUserIdOrderByKeyOrderAsc(userId);
        if (apiKeys.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NO_API_KEY",
                    "번역에 사용할 API 키가 없습니다. 설정 화면에서 API 키를 입력해주세요.");
        }
        ApiKeySetting setting = apiKeySettingRepository.findByUserId(userId).orElse(null);
        // 사용자가 모델을 명시했으면 그 모델만 시도(실패하면 그대로 실패) - 아니면 무료 친화적 모델을 순서대로 시도.
        List<String> models = (setting != null && StringUtils.hasText(setting.getModel()))
                ? List.of(setting.getModel())
                : DEFAULT_MODEL_FALLBACK;
        Integer thinkingBudget = setting != null ? setting.getThinkingBudget() : null;

        NovelPrompt prompt = novelPromptRepository.findByNovelId(novelId).orElse(null);
        String systemPrompt = (prompt != null && StringUtils.hasText(prompt.getSystemPrompt()))
                ? prompt.getSystemPrompt() : DefaultPrompts.SYSTEM_PROMPT;
        String translationNote = prompt != null && prompt.getTranslationNote() != null ? prompt.getTranslationNote() : "";

        List<String> decryptedKeys = apiKeys.stream().map(k -> apiKeyCipher.decrypt(k.getEncryptedKey())).toList();

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("apiKeys", decryptedKeys);
        requestBody.put("models", models);
        requestBody.put("thinkingBudget", thinkingBudget);
        requestBody.put("systemPrompt", systemPrompt);
        requestBody.put("translationNote", translationNote);
        requestBody.put("originalText", chapter.getOriginalText());

        SseEmitter emitter = new SseEmitter(0L);
        executor.submit(() -> streamFromAiApi(emitter, requestBody, novelId, chapterId));
        return emitter;
    }

    private void streamFromAiApi(SseEmitter emitter, Map<String, Object> requestBody, UUID novelId, UUID chapterId) {
        try {
            String json = objectMapper.writeValueAsString(requestBody);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(aiApiBaseUrl + "/internal/translate/stream"))
                    .header("Content-Type", "application/json")
                    .header("X-Internal-Token", internalToken)
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<java.io.InputStream> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() != 200) {
                sendError(emitter, "AI_API_UNAVAILABLE", "AI API 응답 오류: " + response.statusCode());
                emitter.complete();
                return;
            }

            relaySse(emitter, response, novelId, chapterId);
        } catch (Exception e) {
            sendError(emitter, "AI_API_UNAVAILABLE", "번역 중 오류가 발생했습니다: " + e.getMessage());
            try {
                emitter.completeWithError(e);
            } catch (IllegalStateException ignored) {
                // 프론트 연결이 이미 끊겨 emitter가 onError 콜백으로 먼저 완료 처리된 경우 -
                // 중복 완료 호출이라 무시해도 안전하다(정상 완료 후 completeWithError가 다시
                // 불리는 문제를 막은 troubleshooting 17/20과 같은 종류의 방어).
            }
        }
    }

    /**
     * 번역 스트림은 긴 챕터일수록 정상적으로 몇 분씩 걸릴 수 있어 전체 응답 시간에 상한을 걸 수 없다.
     * 대신 "마지막으로 데이터를 받은 이후 얼마나 지났는지"를 워치독으로 감시하다가, idleTimeout을
     * 넘기면 응답 스트림을 강제로 닫아 readLine()을 깨우고 TRANSLATE_TIMEOUT 에러로 마무리한다.
     */
    private void relaySse(SseEmitter emitter, HttpResponse<java.io.InputStream> response, UUID novelId, UUID chapterId)
            throws IOException {
        AtomicLong lastActivityNanos = new AtomicLong(System.nanoTime());
        AtomicBoolean timedOut = new AtomicBoolean(false);
        // 클라이언트가 "중지"를 누르거나 idle 타임아웃/네트워크 오류로 스트림이 done 이벤트 없이 끊겨도,
        // 그때까지 도착한 줄들을 그대로 저장한다 - 다음에 이 챕터로 돌아왔을 때 처음부터 다시 요청하는 대신
        // 중단된 지점까지의 번역이 그대로 보이게 하기 위함.
        List<String> translatedLines = new ArrayList<>();

        // 프론트가 "정지"를 누르거나 연결이 끊기면 서블릿 컨테이너가 이 콜백들을 호출한다 - AI API
        // 응답 스트림을 즉시 닫아서 reader.readLine()에서 블로킹 중이던 릴레이 스레드를 바로 깨운다.
        // 이 콜백이 없으면 AI API가 다음 SSE 이벤트를 보낼 때까지(느린 모델이거나 청크 하나를
        // 여러 번 재시도 중이면 수십 초) 연결 끊김을 전혀 눈치채지 못해 "정지"가 한참 뒤에야
        // 반영되는 것처럼 보인다 - 특히 gemini-3.1-flash-lite처럼 미번역 재시도가 잦은 모델에서 심함.
        emitter.onCompletion(() -> closeQuietly(response.body()));
        emitter.onTimeout(() -> closeQuietly(response.body()));
        emitter.onError(ex -> closeQuietly(response.body()));

        ScheduledFuture<?> watchdog = watchdogScheduler.scheduleWithFixedDelay(() -> {
            if (System.nanoTime() - lastActivityNanos.get() > IDLE_TIMEOUT.toNanos()) {
                timedOut.set(true);
                closeQuietly(response.body());
            }
        }, 5, 5, TimeUnit.SECONDS);

        // BufferedReader를 try-with-resources로 감싸면 done/error 분기의 return이 암묵적으로
        // reader.close()를 트리거하는데, 이때 발생하는 IOException(예: 원격이 이미 커넥션을 정리해
        // close 도중 broken pipe성 예외가 나는 경우)이 아래 catch로 잡혀 "번역은 이미 성공했는데도"
        // completeWithError가 다시 호출되는 문제가 있었다(응답이 이미 커밋된 뒤라 Spring이 "Cannot
        // render error page ... response has already been committed"만 로그에 남기고 프론트에는
        // network error로 보임). close()는 항상 별도로, 예외를 삼키며 수행한다.
        BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
        try {
            String eventName = null;
            StringBuilder dataBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                lastActivityNanos.set(System.nanoTime());
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    dataBuffer.append(line.substring(5).trim());
                } else if (line.isEmpty() && eventName != null && !dataBuffer.isEmpty()) {
                    String dataJson = dataBuffer.toString();
                    emitter.send(SseEmitter.event().name(eventName).data(dataJson, MediaType.APPLICATION_JSON));

                    if ("line".equals(eventName)) {
                        collectLine(translatedLines, dataJson);
                    } else if ("done".equals(eventName)) {
                        persistTranslation(novelId, chapterId, dataJson);
                        emitter.complete();
                        return;
                    } else if ("error".equals(eventName)) {
                        persistPartialTranslation(novelId, chapterId, translatedLines);
                        emitter.complete();
                        return;
                    }
                    eventName = null;
                    dataBuffer.setLength(0);
                }
            }
        } catch (IOException e) {
            persistPartialTranslation(novelId, chapterId, translatedLines);
            if (timedOut.get()) {
                sendError(emitter, "TRANSLATE_TIMEOUT", "번역 응답이 지연되어 중단되었습니다. 잠시 후 다시 시도해주세요.");
                emitter.complete();
                return;
            }
            throw e;
        } finally {
            watchdog.cancel(true);
            try {
                reader.close();
            } catch (IOException ignored) {
            }
        }
        persistPartialTranslation(novelId, chapterId, translatedLines);
        emitter.complete();
    }

    private void collectLine(List<String> translatedLines, String lineDataJson) {
        try {
            JsonNode node = objectMapper.readTree(lineDataJson);
            int index = node.path("index").asInt(translatedLines.size());
            String text = node.path("text").asText("");
            while (translatedLines.size() <= index) {
                translatedLines.add("");
            }
            translatedLines.set(index, text);
        } catch (Exception ignored) {
            // 파싱 실패한 줄 하나 때문에 스트림 전체를 실패시키지 않는다.
        }
    }

    private void persistPartialTranslation(UUID novelId, UUID chapterId, List<String> translatedLines) {
        if (translatedLines.isEmpty()) {
            return;
        }
        saveTranslatedText(novelId, chapterId, String.join("\n", translatedLines));
    }

    private void persistTranslation(UUID novelId, UUID chapterId, String doneDataJson) {
        try {
            JsonNode node = objectMapper.readTree(doneDataJson);
            saveTranslatedText(novelId, chapterId, node.path("translatedText").asText(""));
        } catch (Exception ignored) {
            // done 이벤트 파싱/저장 실패는 스트림 자체의 성공 여부와 무관하므로 조용히 무시한다.
        }
    }

    private void saveTranslatedText(UUID novelId, UUID chapterId, String translatedText) {
        chapterRepository.findByIdAndNovelId(chapterId, novelId).ifPresent(chapter -> {
            chapter.setTranslatedText(translatedText);
            chapter.setUpdatedAt(OffsetDateTime.now());
            chapterRepository.save(chapter);
        });
    }

    private void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
        }
    }

    private void sendError(SseEmitter emitter, String code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "message", message),
                    MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ignored) {
            // IllegalStateException: 프론트 연결 끊김으로 emitter가 onError 콜백을 통해 이미
            // 완료 처리된 뒤 호출된 경우 - send() 자체가 무의미하므로 조용히 무시한다.
        }
    }
}
