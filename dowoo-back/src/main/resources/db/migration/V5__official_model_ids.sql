-- 2026-11-13: 실제 API 모델 목록(models.list)과 공식 문서에 맞춰 저장된 "직접 선택" 모델 ID를 정리한다.
-- gemini-3.1-pro, gemini-3-flash-preview는 API에서 404가 나는 ID라 "자동"으로 되돌린다.
UPDATE api_key_settings SET model = NULL WHERE model IN ('gemini-3.1-pro', 'gemini-3-flash-preview');
-- gemini-2.5-flash는 지원 종료 예정이며 2026-12-01부터 Google이 gemini-3.5-flash-lite로 라우팅한다 - 같은 대상으로 미리 옮긴다.
UPDATE api_key_settings SET model = 'gemini-3.5-flash-lite' WHERE model = 'gemini-2.5-flash';
-- 자동 순서(model_order)에 남은 제거된 모델은 DefaultModelOrder.parseOrDefault가 읽을 때 걸러낸다.
