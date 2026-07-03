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
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** api-spec.md §7.1 구현 - AI API의 POST /internal/translate/stream(§9.2)을 호출해 SSE를 그대로 릴레이한다. */
@Service
public class TranslateService {

    // 사용자가 모델을 지정하지 않으면(설정 미지정=자동) 이 순서로 시도한다 - 무료 티어에서 안정적으로
    // 쓸 수 있는 정식 출시(비-preview) 모델 우선. preview 모델은 보통 결제(billing)가 켜져 있어야 해서
    // 자동 목록에서 제외한다.
    private static final List<String> DEFAULT_MODEL_FALLBACK =
            List.of("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-3.5-flash");

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
    // HTTP/2 cleartext 업그레이드 시도가 uvicorn(HTTP/1.1 전용)과 충돌하는 문제 회피 (HttpCrawlClient 참고).
    private final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

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
            emitter.completeWithError(e);
        }
    }

    private void relaySse(SseEmitter emitter, HttpResponse<java.io.InputStream> response, UUID novelId, UUID chapterId)
            throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
            String eventName = null;
            StringBuilder dataBuffer = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("event:")) {
                    eventName = line.substring(6).trim();
                } else if (line.startsWith("data:")) {
                    dataBuffer.append(line.substring(5).trim());
                } else if (line.isEmpty() && eventName != null && !dataBuffer.isEmpty()) {
                    String dataJson = dataBuffer.toString();
                    emitter.send(SseEmitter.event().name(eventName).data(dataJson, MediaType.APPLICATION_JSON));

                    if ("done".equals(eventName)) {
                        persistTranslation(novelId, chapterId, dataJson);
                        emitter.complete();
                        return;
                    }
                    if ("error".equals(eventName)) {
                        emitter.complete();
                        return;
                    }
                    eventName = null;
                    dataBuffer.setLength(0);
                }
            }
        }
        emitter.complete();
    }

    private void persistTranslation(UUID novelId, UUID chapterId, String doneDataJson) {
        try {
            JsonNode node = objectMapper.readTree(doneDataJson);
            String translatedText = node.path("translatedText").asText("");
            chapterRepository.findByIdAndNovelId(chapterId, novelId).ifPresent(chapter -> {
                chapter.setTranslatedText(translatedText);
                chapter.setUpdatedAt(OffsetDateTime.now());
                chapterRepository.save(chapter);
            });
        } catch (Exception ignored) {
            // done 이벤트 파싱/저장 실패는 스트림 자체의 성공 여부와 무관하므로 조용히 무시한다.
        }
    }

    private void sendError(SseEmitter emitter, String code, String message) {
        try {
            emitter.send(SseEmitter.event().name("error").data(Map.of("code", code, "message", message),
                    MediaType.APPLICATION_JSON));
        } catch (IOException ignored) {
        }
    }
}
