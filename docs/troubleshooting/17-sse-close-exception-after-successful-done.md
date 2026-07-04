# 번역이 성공적으로 끝났는데도 프론트에 네트워크 에러 모달이 뜸

## 증상

번역이 화면에 끝까지 정상적으로 표시된 직후, 프론트에 network error 모달이 떴다. 같은 시각 Core API 로그에는 다음이 남았다.

```
ERROR ... a.e.ErrorMvcAutoConfiguration$StaticView : Cannot render error page for request [null] as the response has already been committed. As a result, the response may have the wrong status code.
```

번역 결과 자체는 DB에 정상 저장돼 있었다 - 데이터 유실은 없고, 스트림이 끝나는 마무리 단계에서만 문제가 생겼다.

## 원인

`TranslateService.relaySse()`가 AI API의 SSE 응답을 읽는 `BufferedReader`를 **try-with-resources**로 감싸고 있었다.

```java
// 수정 전
try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
    ...
    if ("done".equals(eventName)) {
        persistTranslation(novelId, chapterId, dataJson);
        emitter.complete();
        return;   // <- 여기서 암묵적으로 reader.close()가 실행됨
    }
    ...
} catch (IOException e) {
    ...
    throw e;   // timedOut이 아니면 그대로 다시 던짐
}
```

`done` 분기에서 `return`하면 try-with-resources가 **암묵적으로 `reader.close()`를 호출**한다. 이때 AI API(uvicorn) 쪽 커넥션이 이미 정리되고 있는 타이밍과 겹치면 `close()` 자체가 `IOException`을 던질 수 있는데, 이 예외가 (자바 언어 스펙상) 같은 `catch (IOException e)`로 잡힌다. `timedOut`이 아니므로 그대로 `throw e`되어 `streamFromAiApi`의 바깥 catch까지 전파됐고, 거기서 **번역이 이미 성공적으로 끝났음에도** `emitter.completeWithError(e)`가 다시 호출됐다. 하지만 emitter는 몇 줄 전 `emitter.complete()`로 이미 정상 완료된 뒤였고, 응답도 이미 커밋된 상태라 Spring이 이 두 번째 완료 시도를 에러 페이지로 렌더링하려다 실패하면서 저 로그를 남긴 것 - 그리고 이 부적절한 이중 완료 시도가 프론트에서는 스트림이 비정상 종료된 것처럼 보여 network error 모달로 이어졌다.

## 해결

`reader.close()`를 try-with-resources에 맡기지 않고 `finally` 블록에서 **직접, 예외를 삼키며** 호출하도록 바꿨다. 이러면 스트림을 다 읽은 뒤 정리하다가 나는 예외가 "번역이 실패했다"는 신호로 잘못 해석되는 일이 없다.

```java
BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8));
try {
    ...
} catch (IOException e) {
    persistPartialTranslation(novelId, chapterId, translatedLines);
    if (timedOut.get()) { ... }
    throw e;
} finally {
    watchdog.cancel(true);
    try {
        reader.close();
    } catch (IOException ignored) {
    }
}
```

## 참고

- try-with-resources의 암묵적 `close()`가 던지는 예외는 **바로 그 try 블록의 `catch`에 잡힌다**는 걸 잊기 쉽다. "이미 성공적으로 처리를 끝내고 반환하는" 경로에서 리소스 정리 실패가 성공 처리 자체를 덮어써버릴 수 있으니, "정상 처리"와 "리소스 정리"의 예외 처리 범위를 분리해야 한다.
- `SseEmitter`처럼 응답을 점진적으로 커밋하는 비동기 응답은 **이미 성공적으로 `complete()`한 뒤에 `completeWithError()`를 다시 호출하면** 응답이 이미 커밋돼 있어 Spring이 정상적으로 에러를 반영하지 못하고 "Cannot render error page ... response has already been committed" 경고만 남긴다 - 이 로그 문구 자체가 "이미 끝난 응답에 뒤늦게 뭔가를 하려 했다"는 신호이니, 원인을 스트림 종료 이후의 후처리(리소스 정리 등)에서 찾아야 한다.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/TranslateService.java`
