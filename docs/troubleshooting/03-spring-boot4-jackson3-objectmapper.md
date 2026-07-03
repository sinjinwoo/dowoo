# `ObjectMapper` 빈을 찾을 수 없음 (Spring Boot 4의 Jackson 3 전환)

## 증상

`TranslateService`에 `ObjectMapper`를 생성자 주입하는 코드를 추가하고 빌드하면 컴파일부터 실패한다.

```
package com.fasterxml.jackson.databind does not exist
import com.fasterxml.jackson.databind.ObjectMapper;
```

`com.fasterxml.jackson.core:jackson-databind`를 `build.gradle`에 직접 추가하면 컴파일은 되지만, 이번엔 런타임에 빈을 못 찾는다.

```
Parameter 7 of constructor in ...TranslateService required a bean of type
'com.fasterxml.jackson.databind.ObjectMapper' that could not be found.
```

## 원인

Spring Boot 4는 JSON 처리 라이브러리를 **Jackson 2에서 Jackson 3로 전면 교체**했다. Jackson 3에서는 가변(mutable) `ObjectMapper` 대신 불변(immutable) `JsonMapper`를 쓰고, 패키지 루트 자체가 `com.fasterxml.jackson.*`에서 `tools.jackson.*`로 바뀌었다. 그래서:

1. `com.fasterxml.jackson.databind.ObjectMapper`는 Jackson 3 세계에 아예 존재하지 않는 클래스라 컴파일이 안 된다.
2. Jackson 2의 `jackson-databind`를 억지로 추가해도, Spring Boot가 자동 설정으로 등록해주는 빈은 (Jackson 3의) `JsonMapper`뿐이라 여전히 주입할 대상이 없다.

## 해결

의존성과 코드를 Jackson 3 방식으로 맞췄다.

```diff
# build.gradle
- implementation 'com.fasterxml.jackson.core:jackson-databind'
+ implementation 'org.springframework.boot:spring-boot-starter-json'
```

```diff
// TranslateService.java
- import com.fasterxml.jackson.databind.JsonNode;
- import com.fasterxml.jackson.databind.ObjectMapper;
+ import tools.jackson.databind.JsonNode;
+ import tools.jackson.databind.json.JsonMapper;

- private final ObjectMapper objectMapper;
+ private final JsonMapper objectMapper;
```

`writeValueAsString`, `readTree` 등 자주 쓰는 메서드 이름은 Jackson 2와 동일하게 유지되어 있어 호출부 코드는 거의 바꿀 게 없었다. 참고로 Jackson 3부터는 `JacksonException`이 `RuntimeException`을 상속하는 unchecked 예외가 되어, `writeValueAsString`/`readTree`에 대한 checked exception 처리가 더 이상 필요 없다.

## 참고

- Jackson 2용 커스텀 `ObjectMapper` 빈을 재정의하고 싶다면 Boot 4에서는 `ObjectMapper` 빈이 아니라 `JsonMapper`(또는 XML이면 `XmlMapper`) 빈을 정의해야 한다.
- `spring.jackson.use-jackson2-defaults=true` 프로퍼티로 Jackson 2와 최대한 비슷한 기본 동작으로 맞출 수 있다(마이그레이션 과도기용).
- [[02-spring-boot4-flyway-not-running.md]]와 같은 패턴: Boot 4에서는 "라이브러리가 클래스패스에 있으면 자동 설정된다"는 가정이 깨졌으므로, 기능별 `spring-boot-starter-*`를 우선 확인할 것.
- 관련 파일: `dowoo-back/build.gradle`, `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/TranslateService.java`
