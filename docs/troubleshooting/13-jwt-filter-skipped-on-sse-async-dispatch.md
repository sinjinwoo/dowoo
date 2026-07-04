# 번역 중지 시 로그에 AuthorizationDeniedException (SseEmitter의 ASYNC 재디스패치에서 JWT 필터가 스킵됨)

## 증상

로그인 기능을 붙인 뒤, 사용자가 번역 스트리밍 중 "중지" 버튼을 눌러 클라이언트가 연결을 먼저 끊으면 Spring Boot 로그에 다음 스택트레이스가 남았다.

```
core-api-1  | ERROR ... Servlet.service() for servlet [dispatcherServlet] threw exception
core-api-1  |
core-api-1  | org.springframework.security.authorization.AuthorizationDeniedException: Access Denied
```

번역 자체는 정상 동작했고, 이 에러는 오직 스트림이 끝나는 시점(특히 클라이언트가 먼저 끊었을 때)에만 로그에 나타났다.

## 원인

`TranslateController.translateStream()`은 `SseEmitter`를 반환한다. 이 방식은 컨트롤러 스레드가 즉시 반환되고, 실제 릴레이 작업은 별도 스레드(`TranslateService`의 가상 스레드 executor)에서 진행된다. 이 별도 스레드가 `emitter.complete()`/`completeWithError()`를 호출하면, 서블릿 컨테이너(Tomcat)는 원래의 HTTP 요청을 **ASYNC 디스패치**로 재실행해 응답을 최종 마무리한다.

문제는 커스텀 인증 필터 `JwtAuthenticationFilter`가 `OncePerRequestFilter`를 상속하고 있었다는 것이다. `OncePerRequestFilter`는 기본적으로 `shouldNotFilterAsyncDispatch()`가 `true`를 반환해서, **ASYNC 디스패치에서는 필터 자신을 건너뛴다.** 반면 Spring Security의 `AuthorizationFilter`는 ASYNC 디스패치에서도 항상 실행된다. 그 결과:

1. 원래 요청 처리 시 `JwtAuthenticationFilter`가 Authorization 헤더를 파싱해 `SecurityContextHolder`(스레드로컬)에 인증 정보를 채워 넣는다.
2. 컨트롤러가 `SseEmitter`를 반환하고 요청 스레드는 반환된다(이 시점에 스레드로컬은 정리됨).
3. 나중에 emitter가 완료되어 ASYNC 재디스패치가 일어나면, 이번엔 `JwtAuthenticationFilter`가 스킵되어 SecurityContext가 채워지지 않는다.
4. 그런데도 `AuthorizationFilter`는 이 재디스패치에서 여전히 `/api/**`.authenticated() 규칙을 검사하고, 빈 SecurityContext를 보고 `AuthorizationDeniedException`을 던진다.

번역이 자연스럽게 끝날 때(`done` 이벤트)도 원리상 같은 재디스패치가 발생하지만, 그 순간에는 이미 전체 스트림이 클라이언트로 다 전달된 뒤라 겉으로 드러나는 부작용이 적었다. 반대로 사용자가 "중지"를 눌러 클라이언트가 먼저 연결을 끊으면 서버 쪽 emitter도 곧이어 완료되며 같은 재디스패치가 일어나고, 이때 로그에 에러가 남는다.

## 해결

`JwtAuthenticationFilter`에 `shouldNotFilterAsyncDispatch()`를 오버라이드해서 `false`를 반환하도록 했다. ASYNC 디스패치에서도 필터가 다시 실행되어, 여전히 요청 객체에 남아있는 원본 Authorization 헤더로 SecurityContext를 재구성한다.

```java
/**
 * SseEmitter는 컨트롤러 스레드가 즉시 반환되고 별도 스레드에서 emitter를 완료/에러 처리하는데,
 * 그때 서블릿 컨테이너가 원래 요청을 ASYNC로 재디스패치한다. OncePerRequestFilter는 기본적으로
 * ASYNC 디스패치에서 스스로를 건너뛰므로(SecurityContext가 스레드로컬이라 재디스패치를 처리하는
 * 스레드에는 없음), AuthorizationFilter(ASYNC에서도 항상 실행됨)가 빈 SecurityContext를 보고
 * AuthorizationDeniedException을 던진다. ASYNC 디스패치에서도 이 필터가 다시 실행되도록 강제해
 * Authorization 헤더로 SecurityContext를 재구성한다.
 */
@Override
protected boolean shouldNotFilterAsyncDispatch() {
    return false;
}
```

## 참고

- `OncePerRequestFilter`를 상속한 커스텀 필터가 `SecurityContextHolder`처럼 스레드로컬 상태를 채우는 역할을 한다면, 컨트롤러가 `SseEmitter`/`DeferredResult`/`Callable` 같은 비동기 반환 타입을 쓰는 한 항상 이 문제를 의심해야 한다.
- Spring의 `WebAsyncManagerIntegrationFilter`는 `Callable` 기반 비동기 처리에 대해서만 SecurityContext를 자동으로 전파해준다. 우리처럼 직접 관리하는 `ExecutorService`로 `SseEmitter`를 다루는 경우는 이 자동 전파 대상이 아니므로 커스텀 필터가 ASYNC 디스패치를 직접 처리해야 한다.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/auth/security/JwtAuthenticationFilter.java`, `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/TranslateService.java`
