# Docker 로그에 Spring Boot가 생성한 비밀번호가 그대로 노출됨

## 증상

백엔드 기동 로그에 다음이 매번 찍혔다.

```
WARN ... UserDetailsServiceAutoConfiguration :

Using generated security password: a3b83b85-f3f2-418b-9f3b-9e3381e3cf70

This generated password is for development use only. Your security configuration must be updated before running your application in production.
```

## 원인

Spring Boot는 `UserDetailsService` 빈이 하나도 없으면 `UserDetailsServiceAutoConfiguration`이
자동으로 인메모리 계정("user" + 랜덤 비밀번호)을 만들고 그 비밀번호를 로그에 남긴다. 이
프로젝트는 로그인/인증을 전부 커스텀 JWT로 처리한다.

- `AuthService.login()`은 `UserRepository` + `PasswordEncoder.matches()`로 직접 자격 증명을 검증
- `JwtAuthenticationFilter`는 JWT를 파싱해 `userId`(UUID)만 꺼내 `SecurityContext`에 직접 채워 넣음
- `SecurityConfig`에는 `httpBasic()`/`formLogin()`이 없어서, 저 자동 생성 계정으로 실제 로그인할
  수 있는 경로 자체가 없음

즉 이 계정은 완전히 죽은 크리덴셜이었지만, Spring Boot는 `UserDetailsService` 빈의 존재
여부만 보고 자동 생성 여부를 결정하기 때문에 계속 만들어져서 로그에 찍혔다.

## 해결

단순히 자동 설정을 꺼버리는 대신, 확장성을 고려해 Spring Security의 표준 방식(`UserDetailsService`
+ `AuthenticationManager`)을 실제로 도입했다.

1. `UserPrincipal`(신규, `auth/security/`) - `User` 엔티티를 감싸는 `UserDetails` 구현체.
   `isEnabled()`는 `withdrawnAt == null`로 매핑해 탈퇴 계정을 자동으로 걸러내게 했다.
2. `UserDetailsServiceImpl`(신규) - `UserRepository.findByUsername`으로 `UserPrincipal`을 로드.
3. `SecurityConfig`에 `AuthenticationManager` 빈 추가 - `DaoAuthenticationProvider(userDetailsService)` +
   `setPasswordEncoder(passwordEncoder)`를 `ProviderManager`로 감쌌다(Spring Security 7.1 기준
   `DaoAuthenticationProvider`는 생성자로 `UserDetailsService`를 받아야 하며 no-args 생성자가 없다).
4. `AuthService.login()`을 `authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(...))`로
   교체 - 아이디 없음/비밀번호 불일치/탈퇴 계정을 모두 `AuthenticationException`으로 받아서 기존과
   동일하게 `INVALID_CREDENTIALS` 하나로 응답(계정 존재 여부가 클라이언트에 노출되지 않도록 유지).
5. `AuthServiceTest`를 `AuthenticationManager` 목(mock) 기반으로 갱신.

`JwtAuthenticationFilter`(매 요청 인증)는 일부러 손대지 않았다 - 이미 `UserDetails`/역할(authority)
없이 JWT의 `userId`만으로 충분히 동작하고 있고, 매 요청마다 `UserDetailsService`를 다시 태우면
불필요한 DB 조회만 늘어난다. `UserDetailsService`/`AuthenticationManager`가 실제로 필요한 지점은
로그인(자격 증명 검증) 하나뿐이었다.

이제 `UserDetailsService` 빈이 실제로 존재하므로 `UserDetailsServiceAutoConfiguration`이 더
이상 인메모리 계정을 만들지 않는다 - 별도의 `exclude` 설정 없이 원인 자체가 해소됐다.

## 참고

- "표준 방식으로 확장성 있게" 갈지 "당장 증상만 없앨지"는 트레이드오프 문제다 - 이번엔 이미
  실제로 쓰이는 인증 경로가 전부 커스텀이라 표준 방식 도입이 로그인 한 곳(`AuthService`)만
  건드리면 됐지만, 만약 역할 기반 권한(`@PreAuthorize` 등)이나 `@AuthenticationPrincipal` 주입이
  이미 여러 곳에 퍼져 있었다면 훨씬 큰 리팩터링이 됐을 것이다. 도입 전에 "이걸 실제로 쓰는
  지점이 몇 곳인가"부터 파악할 것.
- Spring Security 버전이 오르면서(6.x → 7.x) `DaoAuthenticationProvider`의 생성자/세터 API가
  바뀐다 - 튜토리얼 코드를 그대로 베끼기 전에 실제 사용 중인 버전의 클래스 시그니처를
  `javap` 등으로 직접 확인할 것.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/auth/security/UserPrincipal.java`,
  `UserDetailsServiceImpl.java`, `SecurityConfig.java`, `auth/service/AuthService.java`,
  `src/test/.../AuthServiceTest.java`
