-- 사용자가 "자동" 모드에서 시도할 무료 모델의 순서를 커스터마이즈할 수 있게 저장 컬럼 추가.
-- 콤마로 구분된 모델 id 목록(DefaultModelOrder.serialize/parseOrDefault 참고). NULL이면 기본 순서 사용.
ALTER TABLE api_key_settings ADD COLUMN model_order TEXT;
