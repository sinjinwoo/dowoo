# 잘못된 형식의 UUID를 경로 변수로 넘기면 400이 아니라 500이 발생

## 증상

`GET /api/v1/novels/1`처럼 UUID 자리에 UUID가 아닌 값을 넣어 테스트하면, 의미 있는 `400 VALIDATION_ERROR` 대신 `500 INTERNAL_ERROR`가 반환된다.

## 원인

`NovelController`의 `@PathVariable UUID novelId`는 Spring MVC가 문자열 `"1"`을 `UUID` 타입으로 변환하려다 실패하면서 `MethodArgumentTypeMismatchException`을 던진다. 그런데 `GlobalExceptionHandler`에는 이 예외 전용 핸들러가 없었고, `@ExceptionHandler(Exception.class)`로 잡히는 최후의 catch-all(500 INTERNAL_ERROR)로 흘러갔다.

## 해결

`MethodArgumentTypeMismatchException` 전용 핸들러를 추가해 400으로 명확하게 매핑했다. 겸사겸사 JSON 본문 자체를 파싱할 수 없는 경우(`HttpMessageNotReadableException`)도 500이 아니라 400으로 떨어지도록 같이 추가했다.

```java
@ExceptionHandler(MethodArgumentTypeMismatchException.class)
public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    String details = "%s 값 '%s'이(가) 올바른 형식이 아닙니다.".formatted(ex.getName(), ex.getValue());
    return ResponseEntity.badRequest()
            .body(ApiResponse.failure(400, "VALIDATION_ERROR", "요청 값이 올바르지 않습니다.", details));
}

@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ApiResponse<Void>> handleUnreadableBody(HttpMessageNotReadableException ex) {
    return ResponseEntity.badRequest()
            .body(ApiResponse.failure(400, "VALIDATION_ERROR", "요청 본문을 읽을 수 없습니다.",
                    ex.getMostSpecificCause().getMessage()));
}
```

## 참고

- ID 타입을 자동 증가 `Long`이 아니라 `UUID`로 설계했을 때(이 프로젝트는 [[../api-spec.md]] §0.6에서 전 테이블 UUID PK로 결정) 흔히 놓치는 예외 종류. `Long`이었다면 "1"도 유효한 값이라 이 문제 자체가 발생하지 않았을 것이다.
- `GlobalExceptionHandler`처럼 catch-all(`Exception.class`) 핸들러를 두더라도, 프레임워크가 흔히 던지는 예외(타입 변환 실패, 본문 파싱 실패, 검증 실패 등)는 각각 전용 핸들러로 먼저 잡아 의미 있는 상태 코드/에러 코드를 내려주는 게 좋다. catch-all은 정말 "예상 못 한" 에러를 위한 최후 방어선으로만 쓴다.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/common/exception/GlobalExceptionHandler.java`
