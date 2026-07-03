# 무료 API 키로 번역이 계속 실패 - preview 모델은 결제가 있어야 함, SDK가 예외 없이 조용히 실패하는 경우도 있음

## 증상

프론트에서 번역을 요청하면 키가 3개나 등록되어 있는데도 계속 실패하거나, 빈 번역 결과로 끝나버렸다. 사용자가 등록한 키는 무료(free tier) 키였다.

## 원인

두 가지 문제가 겹쳐 있었다.

1. **모델 설정이 preview 모델로 되어 있었다.** 이 세션 중 개발자가 `PUT /api/v1/settings/api`를 curl로 직접 테스트하면서 실수로 라이브 설정의 `model`을 `gemini-2.5-pro`로 바꿔놨었고, 그 이후 사용자가 프론트 UI에서 직접 `gemini-3-flash-preview`(당시 프론트 드롭다운의 기본 옵션 중 하나)로 바꿨다. Google 공식 문서를 확인해보니 **preview 모델은 "보통 결제(billing)가 켜져 있어야 한다"**고 명시되어 있다 - 순수 무료 티어 키로는 애초에 쓸 수 없는 모델이었다.
2. **일부 Gemini SDK 오류는 예외(exception)를 던지지 않고 빈 스트림만 반환한다.** 그래서 `translate_stream`의 키 로테이션 판단 로직(`_is_auth_or_quota_error`)이 애초에 실행될 기회조차 없이, 그냥 빈 번역 결과로 "성공"해버리는 경우가 있었다. 이 경우 다음 키로 넘어가지도 않고 에러 이벤트도 안 나가서, 사용자 입장에서는 원인 파악이 아예 불가능했다.

## 해결

1. **모델 자동 폴백 기능 추가.** 사용자가 모델을 명시적으로 지정하지 않으면(설정에서 "자동" 선택 = 빈 값) Core API가 `gemini-2.5-flash → gemini-2.5-flash-lite → gemini-3.5-flash` 순서로 시도하도록 `TranslateService`에 `DEFAULT_MODEL_FALLBACK` 리스트를 추가했다. 사용자가 특정 모델을 명시하면 그 모델만 시도하고 실패하면 그대로 끝낸다(자동으로 다른 모델로 안 바뀜 - 사용자가 원하는 모델을 확실히 쓰도록).
2. **"키 우선, 모델 다음" 순서로 이중 폴백 구현.** `dowoo-python/app/translate/gemini_client.py`의 `translate_stream`을 모델 리스트를 받도록 바꾸고, 모델마다 기존 키 로테이션 로직을 전부 소진한 뒤에만 다음 모델로 넘어가도록 재구성했다(`_try_one_key` 헬퍼로 한 번의 (모델,키) 시도를 분리).
3. **빈 스트림도 실패로 취급.** `_try_one_key`가 예외 없이 끝났는데 `translated_lines`가 비어 있으면, 인증/키 오류로 간주해 다음 키로 넘어가도록 명시적으로 처리했다.
4. **preview 모델을 프론트 선택지에서 제거.** `ApiSettingsPanel`의 모델 드롭다운에서 `gemini-3-flash-preview`를 없애고, 확인된 정식 출시(stable) 모델만 남긴 뒤 맨 위에 "자동" 옵션을 추가했다.
5. 라이브 설정을 즉시 `gemini-2.5-flash`로 되돌렸다.

## 참고

- **preview/experimental 모델은 무료 티어 키의 기본 후보에서 제외할 것.** 결제 요구사항이 모델마다 다르고, 이름만 봐서는 구분이 안 된다. 공식 문서에서 상태(stable/preview)를 반드시 확인한다.
- **서드파티 SDK가 "실패했는데 예외를 안 던지는" 경우를 항상 의심할 것.** 이번 케이스처럼 `except Exception`만 믿고 로테이션/폴백 로직을 짜면, SDK가 조용히 빈 응답만 주는 실패 모드를 완전히 놓친다. "성공"으로 판단하기 전에 실제로 의미 있는 결과(빈 문자열이 아닌)가 왔는지 별도로 검증하는 방어 코드가 필요하다.
- Python에서 `for i in range(n):` 루프 안에서 `i = n`처럼 루프 변수를 재할당해도 **다음 반복이 멈추지 않는다** (range는 이미 고정된 이터레이터라 변수 재할당은 다음 값에 영향을 주지 못함). 루프를 조기 종료하려면 반드시 `break`를 써야 한다 - 이번 구현 중 직접 이 실수를 했다가 코드 리뷰 단계에서 잡았다.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/TranslateService.java`, `dowoo-python/app/translate/gemini_client.py`, `dowoo-python/app/schemas.py`, `dowoo/src/components/settings/ApiSettingsPanel.tsx`
