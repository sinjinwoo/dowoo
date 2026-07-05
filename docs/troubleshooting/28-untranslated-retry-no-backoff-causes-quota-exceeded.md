# 미번역 재시도가 딜레이 없이 연달아 나가서 구글 사용량 한도 초과가 자주 뜸

## 증상

번역 도중 "구글 서버 사용량 한도를 초과했습니다"(429 QUOTA_EXCEEDED) 안내가 종종 떴다.
재시도/키 로테이션 로직([21](21-chunk-translation-key-rpm-concentration.md),
[23](23-gemini-flash-lite-untranslated-passthrough.md))은 이미 있었는데도 발생 빈도가 낮지 않았다.

## 원인

`_try_one_key`의 미번역 감지 재시도(같은 모델·키로 최대 `UNTRANSLATED_MAX_ATTEMPTS`(3)번)와,
`translate_stream`의 키/모델 로테이션 모두 실패와 다음 시도 사이에 **딜레이가 전혀 없었다.**
특히 같은 키로 3연타를 순식간에 쏘는 부분이 문제였다 - 무료 티어처럼 RPM(분당 요청 수) 한도가
낮은 경우, 짧은 시간에 같은 키로 여러 번 요청하면 실제 쿼터가 남아있어도 RPM 버스트 자체에
걸려 429가 난다.

반면 다른 키로 넘어가는 로테이션은 (일반적으로) 별도의 쿼터 버킷이라 딜레이 없이 바로
요청해도 문제가 없다 - 여기까지 딜레이를 넣으면 정상 번역 속도만 불필요하게 늦춘다.

## 해결

`dowoo-python/app/translate/gemini_client.py`의 `_try_one_key`에서, 같은 (모델, 키)로
재시도하기 직전에만 지수적으로 늘어나는 시간(`UNTRANSLATED_RETRY_BACKOFF_SECONDS = (5, 10, 15)`)만큼
`asyncio.sleep`으로 대기하도록 했다. 키 로테이션(`translate_stream`)에는 손대지 않았다 -
그쪽은 서로 다른 자원이라 딜레이가 필요 없다는 [21번 문서](21-chunk-translation-key-rpm-concentration.md)의
결론과 일관된다.

테스트(`tests/test_gemini_client.py`)에서는 `asyncio.sleep`을 autouse fixture로 무력화해서
백오프 시간 때문에 테스트가 실제로 느려지지 않게 했고, 백오프가 재시도 횟수에 따라 늘어나는지
검증하는 테스트를 추가했다.

## 참고

- 같은 실패라도 "같은 자원으로 재시도"와 "다른 자원으로 넘어가기"는 딜레이가 필요한 이유가
  다르다 - 전자는 그 자원의 순간 요청 속도(RPM)를 늦추기 위함이고, 후자는 애초에 그럴 필요가
  없다. 백오프를 넣을 땐 어느 쪽인지부터 구분할 것.
- 관련 파일: `dowoo-python/app/translate/gemini_client.py`, `dowoo-python/tests/test_gemini_client.py`
