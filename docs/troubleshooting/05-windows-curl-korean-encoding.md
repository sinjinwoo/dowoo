# Windows에서 curl로 한글 JSON을 보내면 "요청 본문을 읽을 수 없습니다"

## 증상

Windows 환경에서 한글이 포함된 JSON을 커맨드라인 인자로 바로 넘겨 테스트하면 서버가 400을 반환한다.

```powershell
curl -X POST http://localhost:8080/api/v1/read -H "Content-Type: application/json" -d '{"pastedText":"테스트 원문입니다."}'
# → {"status":400, ..., "message":"요청 본문을 읽을 수 없습니다."}
```

처음에는 서버가 UTF-8을 제대로 처리 못 하는 것으로 의심했으나, `GlobalExceptionHandler`가 실제 원인 메시지를 숨기고 있어([[04-uuid-path-variable-500-error.md]] 참고) 처음엔 원인을 알 수 없었다.

## 원인

Jackson(JSON `@RequestBody`)은 항상 UTF-8로 바이트 스트림을 읽으므로 서버 쪽 인코딩 설정 문제가 아니다. 진짜 원인은 **Windows 콘솔이 한글 커맨드라인 인자를 시스템 코드페이지(한국어 Windows 기본값 CP949)로 인코딩해서 자식 프로세스(curl.exe)에 넘기는 것**이었다. curl은 그 깨진 바이트를 그대로 전송하고, 서버는 그걸 UTF-8로 파싱하려다 실패한다. `GlobalExceptionHandler.handleUnreadableBody`가 원인 메시지를 `null`로 버리고 있어서 처음엔 이 사실을 확인할 수 없었는데, `ex.getMostSpecificCause().getMessage()`를 노출하도록 고치자 정확한 원인이 드러났다.

```json
"details": "Invalid UTF-8 middle byte 0xd7\n at [...] byte offset: #17 ..."
```

## 해결

1. **원인 진단**을 위해 예외 핸들러가 실제 메시지를 감추지 않도록 수정 (부수적으로 영구히 유지할 가치가 있는 개선):

   ```diff
   - .body(ApiResponse.failure(400, "VALIDATION_ERROR", "요청 본문을 읽을 수 없습니다.", null));
   + .body(ApiResponse.failure(400, "VALIDATION_ERROR", "요청 본문을 읽을 수 없습니다.",
   +         ex.getMostSpecificCause().getMessage()));
   ```

2. **실제 우회**는 클라이언트 쪽 문제이므로 서버 코드가 아니라 테스트 방법을 바꿔야 한다. 한글 포함 JSON은 커맨드라인 인자로 직접 넘기지 말고 UTF-8로 저장한 파일을 통해 전송한다.

   ```powershell
   '{"pastedText":"테스트 원문입니다."}' | Out-File -Encoding utf8 test.json
   curl.exe -X POST http://localhost:8080/api/v1/read -H "Content-Type: application/json" --data-binary "@test.json"
   ```

   Git Bash에서도 동일한 현상이 재현됐다(`printf '...' > file.json` 후 `curl --data-binary @file.json`으로 우회) — Git Bash 역시 네이티브 Windows 프로세스(curl.exe)를 실행할 때는 같은 코드페이지 변환 경로를 타기 때문이다.

## 참고

- 에러 메시지를 일부러 감추면(`null`, "알 수 없는 오류" 등) 디버깅 속도가 크게 느려진다. self-hosted/개발 단계 API라면 예외의 실제 원인을 `details` 필드에 그대로 노출하는 편이 유리하다. (민감한 운영 서비스라면 별도 로깅 + 클라이언트에는 일반화된 메시지를 주는 절충이 필요할 수 있다.)
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/common/exception/GlobalExceptionHandler.java`
