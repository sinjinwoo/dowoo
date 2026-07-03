# Flyway 마이그레이션이 조용히 실행되지 않고 Hibernate 스키마 검증에서 실패

## 증상

`docker compose up`으로 `core-api`를 띄우면 애플리케이션이 기동 도중 죽는다.

```
org.springframework.beans.factory.BeanCreationException: Error creating bean with name 'entityManagerFactory' ...
org.hibernate.tool.schema.spi.SchemaManagementException: Schema validation: missing table [api_key_settings]
```

로그를 전부 확인해도 Flyway 관련 로그(`Flyway Community Edition`, `Migrating schema` 등)가 **한 줄도 없다** — 즉 Flyway가 아예 실행 시도조차 하지 않은 채 Hibernate가 곧바로 스키마 검증(`ddl-auto=validate`)에 들어가 실패한다.

## 원인

`build.gradle`에 `org.flywaydb:flyway-core`(+`flyway-database-postgresql`)를 raw 의존성으로만 추가했다. Spring Boot 3.x까지는 이것만으로 `flyway-core`가 클래스패스에 있다는 조건(`@ConditionalOnClass`)만으로 Flyway 자동 설정이 동작했지만, **Spring Boot 4는 거대한 단일 autoconfigure 모듈을 기능별로 잘게 쪼갰다**(`org.springframework.boot.<기능>.autoconfigure` 패키지 구조). Flyway의 자동 설정 클래스(`FlywayAutoConfiguration`)도 별도 모듈로 분리되어, 이제는 `org.springframework.boot:spring-boot-starter-flyway`를 명시적으로 추가해야만 Boot가 Flyway를 자동으로 인식하고 기동 시점에 migrate를 호출한다. raw `flyway-core`만으로는 라이브러리가 클래스패스에 존재할 뿐, Spring이 그것을 자동 설정으로 연결해주지 않는다.

## 해결

`build.gradle`에서 `flyway-core`를 `spring-boot-starter-flyway`로 교체했다(PostgreSQL 방언 지원용 `flyway-database-postgresql`은 Flyway 자체의 확장이라 별도로 유지).

```diff
- implementation 'org.flywaydb:flyway-core'
+ implementation 'org.springframework.boot:spring-boot-starter-flyway'
  implementation 'org.flywaydb:flyway-database-postgresql'
```

재빌드 후 기동 로그에 `Successfully validated N migrations`, `Current version of schema "public": N` 등 Flyway 로그가 정상적으로 나타나고, 이어서 Hibernate 스키마 검증도 통과했다.

## 참고

- 이 문제는 **에러 메시지가 원인을 직접 가리키지 않는** 케이스였다. 에러 자체는 Hibernate/JPA 쪽에서 나서(`missing table`) 처음엔 마이그레이션 SQL 파일 자체를 의심하기 쉽지만, 진짜 원인은 "Flyway가 아예 실행되지 않음"이었다. Flyway 관련 로그가 한 줄도 없다면 SQL 문법보다 먼저 자동 설정 자체가 켜져 있는지부터 의심할 것.
- Spring Boot 4로 다른 라이브러리(예: Redis, Elasticsearch, Security 등)를 붙일 때도 "raw 의존성만으로 예전처럼 자동 설정되겠지"라고 가정하지 말고, 해당 기능의 `spring-boot-starter-*`가 존재하는지 먼저 확인할 것. [[03-spring-boot4-jackson3-objectmapper.md]]에서 같은 패턴이 Jackson(JSON)에서도 재발했다.
- 관련 파일: `dowoo-back/build.gradle`
