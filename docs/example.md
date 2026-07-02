# API 설명

특정 tryId의 trace 데이터를 trace storage에서 삭제합니다. in-memory storage를 사용할 때 trace 데이터가 계속 쌓이는 것을 방지하기 위해 사용합니다. 이 엔드포인트를 호출하면 해당 tryId에 대한 trace 데이터가 trace storage에서 완전히 제거됩니다.

---

# 📤 Request

---

**PathVariable**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| tryId | String (UUID) | 삭제할 Try 세션 ID. UUID 형식이어야 합니다. | ✅ |

**Query Parameter**

없음

**Request Body**

없음

**설명**

- `tryId`는 UUID 형식이어야 하며, 그렇지 않으면 `400 Bad Request` 에러가 반환됩니다.
- 이 엔드포인트는 trace storage(in-memory 또는 기타 구현체)에 저장된 trace 데이터를 삭제합니다.
- trace가 존재하지 않는 경우에도 `200 OK`를 반환하며, 메시지만 다릅니다.
- 삭제된 trace는 이후 조회할 수 없습니다.

# 📥 Response

---

**HTTP Status Code**: `200 OK`

**Response Body (삭제 성공)**

```json
{
  "status": 200,
  "data": null,
  "message": "Trace deleted successfully for tryId: 550e8400-e29b-41d4-a716-446655440000",
  "error": null
}

```

**Response Body (Trace 없음)**

```json
{
  "status": 200,
  "data": null,
  "message": "Trace not found for tryId: 550e8400-e29b-41d4-a716-446655440000",
  "error": null
}

```

**필드 설명**

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| status | Integer | HTTP 상태 코드 (200) |
| data | null | 응답 데이터 (항상 null) |
| message | String | 삭제 성공 또는 trace 없음 메시지 |
| error | null | 에러 정보 (성공 시 null) |

**설명**

- trace가 존재하고 삭제되면 "Trace deleted successfully for tryId: {tryId}" 메시지를 반환합니다.
- trace가 없으면 "Trace not found for tryId: {tryId}" 메시지를 반환합니다.
- 두 경우 모두 `200 OK`를 반환하며, `data`는 항상 `null`입니다.
- 삭제 후에는 해당 tryId로 trace를 조회할 수 없습니다.

# 🔥 ERROR

---

### 1️⃣ `400 Bad Request` — Invalid tryId format

| 항목 | 내용 |
| --- | --- |
| **에러 코드** | `INVALID_TRY_ID` |
| **발생 조건** | `tryId`가 유효한 UUID 형식이 아닌 경우 |

**응답 예시**

```json
{
    "status": 400,
    "data": null,
    "message": "Invalid tryId format",
    "error": {
        "code": "INVALID_TRY_ID",
        "details": "Invalid tryId format: 'invalid-uuid'. tryId must be a valid UUID."
    }
}
```

---

### 2️⃣ `500 Internal Server Error` — Internal Error

| 항목 | 내용 |
| --- | --- |
| **에러 코드** | `INTERNAL_ERROR` |
| **발생 조건** | 예상치 못한 서버 내부 에러 |

**응답 예시**

```json
{
    "status": 500,
    "data": null,
    "message": "Failed to process request",
    "error": {
        "code": "INTERNAL_ERROR",
        "details": "An internal error occurred"
    }
}
```

---

**추가 참고사항**

1. 메모리 관리
    - in-memory storage를 사용할 때 trace 데이터가 계속 쌓이면 메모리 누수가 발생할 수 있습니다.
    - 이 엔드포인트를 사용하여 불필요한 trace 데이터를 삭제하여 메모리를 확보할 수 있습니다.
2. 삭제 동작
    - trace가 존재하는지와 상관없이 항상 `200 OK`를 반환합니다.
    - 삭제 후에는 해당 tryId로 trace를 조회할 수 없습니다 (`GET /ouro/tries/{tryId}/trace` 등).
3. Trace Storage
    - 다른 storage 구현체(예: in-memory, tempo)를 사용하는 경우에도 동일하게 동작합니다.
4. 동시성
    - 삭제 중에도 다른 요청이 해당 trace를 조회하려고 시도할 수 있습니다.
    - 삭제 후에는 즉시 조회할 수 없게 됩니다.
5. 권장 사용법
    - 분석이 완료된 trace 데이터는 삭제하는 것을 권장합니다.
    - 주기적으로 오래된 trace 데이터를 정리하는 스케줄러와 함께 사용할 수 있습니다.