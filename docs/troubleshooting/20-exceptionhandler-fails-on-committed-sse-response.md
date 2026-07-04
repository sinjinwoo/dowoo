# 번역 스트리밍 중 다음 편으로 이동하면 GlobalExceptionHandler에서 2차 예외 로그

## 증상

번역이 스트리밍(SSE)되는 도중에 "다음 편" 버튼을 눌러 프론트에서 스트림을 중단시키면, Core API 로그에 다음 경고가 남았다.

```
WARN ... ExceptionHandlerExceptionResolver : Failure in @ExceptionHandler
io.dedyn.jwlabs.dowoo.common.exception.GlobalExceptionHandler#handleUnexpected(Exception)

org.springframework.http.converter.HttpMessageNotWritableException:
No converter for [class io.dedyn.jwlabs.dowoo.common.response.ApiResponse]
with preset Content-Type 'text/event-stream'
```

## 원인

번역 스트리밍 엔드포인트는 응답을 `Content-Type: text/event-stream`으로 커밋한 채 데이터를 조금씩 흘려보낸다. 번역 도중 사용자가 다음 편으로 이동하면 프론트가 `abortControllerRef.current?.abort()`로 연결을 끊는데, 이 시점에 Spring이 비동기 처리 중 발생한 예외를 감지해 `GlobalExceptionHandler`의 catch-all 핸들러(`handleUnexpected(Exception)`)로 넘긴다.

문제는 이 핸들러가 **응답이 이미 어떤 상태로 커밋되어 있는지 확인하지 않고** 항상 JSON `ApiResponse` 바디를 쓰려고 한다는 점이다. 응답이 이미 `text/event-stream`으로 커밋된 뒤라 JSON을 쓸 수 있는 `HttpMessageConverter`가 없어서, 원래 예외를 처리하려던 시도 자체가 `HttpMessageNotWritableException`이라는 두 번째 예외를 낳았다.

## 해결

`GlobalExceptionHandler.handleUnexpected`에 `HttpServletResponse`를 인자로 받아 `response.isCommitted()`를 확인하도록 수정했다. 응답이 이미 커밋된 상태라면(스트리밍 도중 클라이언트가 연결을 끊은 경우 등) JSON 바디를 쓰려는 시도 자체를 하지 않고 디버그 로그만 남긴 뒤 `null`을 반환한다 - `@ExceptionHandler`가 `null`을 반환하면 Spring이 추가로 응답 바디를 쓰지 않고 요청 처리를 종료한다.

```java
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex, HttpServletResponse response) {
    if (response.isCommitted()) {
        log.debug("Exception after response committed (likely client-aborted stream): {}", ex.getMessage());
        return null;
    }
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.failure(500, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.", ex.getMessage()));
}
```

## 참고

- SSE/스트리밍 응답을 다루는 프로젝트에서는 전역 예외 핸들러가 **하나의 Content-Type만 가정하고 항상 같은 바디를 쓰려 하면 안 된다.** 응답이 이미 다른 Content-Type으로 커밋됐을 가능성을 항상 열어두고 `HttpServletResponse.isCommitted()`로 분기해야 한다.
- 이 문제는 [17-sse-close-exception-after-successful-done.md](17-sse-close-exception-after-successful-done.md)와 증상은 다르지만 뿌리는 비슷하다 - 둘 다 "스트림이 이미 커밋/완료된 뒤에 뭔가를 또 하려는" 타이밍 문제다. 스트리밍 응답을 만질 때는 정상 종료 경로뿐 아니라 "이미 끝난 뒤에 예외 처리 등 후속 동작이 끼어드는 경우"까지 항상 의심해야 한다.
- `@ExceptionHandler` 메서드는 `HttpServletRequest`/`HttpServletResponse` 등 서블릿 인자를 그대로 받을 수 있다 - 컨트롤러 메서드와 동일한 인자 리졸버가 적용된다.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/common/exception/GlobalExceptionHandler.java`
