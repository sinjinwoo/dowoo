# Docker 빌드 시 "Spring Boot plugin requires Gradle 8.x (8.14+) or 9.x" 에러

## 증상

`dowoo-back` Dockerfile로 `docker compose up --build`를 실행하면 첫 빌드 단계에서 실패한다.

```
Failed to apply plugin 'org.springframework.boot'.
> Spring Boot plugin requires Gradle 8.x (8.14 or later) or 9.x. The current version is Gradle 8.11.1
```

## 원인

Dockerfile의 빌드 스테이지가 `gradle:8.11-jdk21`처럼 특정 Gradle 버전이 미리 설치된 베이스 이미지를 사용했다. 하지만 프로젝트의 `gradle/wrapper/gradle-wrapper.properties`는 Gradle 9.5.1을 쓰도록 지정되어 있었고, Spring Boot 4.1.0의 Gradle 플러그인은 Gradle 8.14+ 또는 9.x를 요구한다. 이미지에 박힌 8.11.1과 프로젝트가 원하는 9.5.1이 어긋난 것이다.

## 해결

이미지에 내장된 `gradle` 커맨드 대신, 프로젝트에 이미 있는 Gradle Wrapper(`./gradlew`)를 그대로 사용하도록 Dockerfile을 수정했다. Wrapper는 `gradle-wrapper.properties`에 지정된 버전을 스스로 다운로드하므로 베이스 이미지의 Gradle 버전과 무관해진다.

```dockerfile
# Before
FROM gradle:8.11-jdk21 AS build
...
RUN gradle bootJar --no-daemon -x test

# After
FROM eclipse-temurin:21-jdk-jammy AS build
...
RUN chmod +x gradlew && ./gradlew --version
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test
```

베이스 이미지를 플레인 JDK 이미지(`eclipse-temurin:21-jdk-jammy`)로 바꾸고, `./gradlew --version`을 별도 레이어로 먼저 실행해 Gradle 배포판 다운로드를 소스 코드 변경과 분리된 캐시 레이어로 만들었다.

## 참고

- Docker에서 Gradle/Maven 프로젝트를 빌드할 때는 고정된 버전의 빌드 도구 이미지보다 **프로젝트가 커밋한 wrapper**를 쓰는 쪽이 항상 안전하다. 로컬 개발 환경과 CI/CD, 컨테이너 빌드가 전부 같은 버전을 쓰게 보장되기 때문이다.
- 관련 파일: `dowoo-back/Dockerfile`, `dowoo-back/gradle/wrapper/gradle-wrapper.properties`
