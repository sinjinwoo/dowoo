# "정지" 버튼을 눌러도 한참 있다가 반영됨 (gemini-3.1-flash-lite에서 특히 심함)

## 증상

번역 중 "정지" 버튼을 눌러도 바로 멈추지 않고 한참(때로는 수십 초) 뒤에야 멈췄다.
`gemini-3.1-flash-lite`를 쓸 때 이 증상이 특히 두드러졌다.

## 원인

`TranslateService.relaySse`는 AI API(FastAPI)가 보내는 SSE 이벤트를 한 줄씩 읽어(`reader.readLine()`)
프론트로 그대로 릴레이한다. 그런데 이 `SseEmitter`에는 `onCompletion`/`onTimeout`/`onError`
콜백이 전혀 등록돼 있지 않았다 - 즉 Core API가 프론트 연결이 끊겼다는 걸 알아챌 방법이 "다음
이벤트를 릴레이하려고 `emitter.send()`를 호출했다가 실패하는 것"뿐이었다. AI API가 다음 이벤트를
보낼 때까지는(그 사이 릴레이 스레드는 `reader.readLine()`에서 그냥 블로킹) 프론트가 이미
연결을 끊었어도 전혀 눈치채지 못했다.

평소엔 이 지연이 짧아서 잘 안 보였지만, [23번 문서](23-gemini-flash-lite-untranslated-passthrough.md)에서
`gemini-3.1-flash-lite`의 미번역(원문 그대로 반환) 문제를 해결하기 위해 같은 (모델, 키)로
최대 3번까지 조용히 재시도하는 로직을 추가하면서, 한 청크 시도 안에서 AI API가 아무 이벤트도
내보내지 않는 "침묵 구간"이 최대 3배까지 길어질 수 있게 됐다. 이 모델은 애초에 미번역 재시도가
자주 걸리는 모델이라, 하필 사용자가 "정지"를 누르는 순간이 이 침묵 구간과 겹칠 확률이 다른
모델보다 훨씬 높아졌다 - 그래서 유독 flash-lite에서 정지가 안 되는 것처럼 보였다.

## 해결

`relaySse`에서 `HttpResponse`를 받은 직후 `SseEmitter`에 `onCompletion`/`onTimeout`/`onError`
콜백을 등록해, 서블릿 컨테이너가 프론트 연결 끊김을 감지하는 즉시(릴레이 스레드가 무엇을 하고
있든 상관없이) AI API 응답 스트림(`response.body()`)을 강제로 닫도록 했다. 이렇게 하면
`reader.readLine()`이 즉시 `IOException`으로 깨어나 기존의 "부분 번역 저장 후 종료" 경로를
그대로 타게 된다 - idle 타임아웃 워치독이 하던 것과 같은 메커니즘을 프론트 연결 끊김에도
적용한 것이다.

콜백이 우리 자신의 정상적인 `emitter.complete()`/`completeWithError()` 호출 때도 같이
발동되므로(정상 완료도 "완료"의 일종이라 `onCompletion`이 따라 불림), 이로 인해 발생할 수 있는
중복 완료 호출(`IllegalStateException`)도 `sendError`와 `streamFromAiApi`의 catch 블록에서
추가로 방어했다.

## 참고

- Spring MVC의 `SseEmitter`는 클라이언트 연결 끊김을 **콜백을 등록해야만** 알 수 있다 - 등록하지
  않으면 다음 `send()` 시도가 실패할 때까지 전혀 알 방법이 없다. 스트리밍 응답을 릴레이하는
  코드를 새로 짤 때는 항상 `onCompletion`/`onTimeout`/`onError`부터 등록할 것.
- **재시도/안전장치를 추가할 때는 "이 재시도가 다른 시간 민감 로직(타임아웃, 연결 끊김 감지 등)의
  전제를 깨뜨리지 않는지"도 같이 확인할 것.** 23번 문서의 재시도 로직 자체는 올바른 판단이었지만,
  "AI API가 침묵할 수 있는 최대 시간"이라는 암묵적 전제를 건드렸다는 걸 그 작업 당시엔 놓쳤다.
  [22번 문서](22-chunk-buffering-broke-realtime-streaming.md)와 같은 계열의 교훈이다.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/TranslateService.java`
