# "Gemini 3 Flash" 선택/자동 폴백 시 404 에러

## 증상

설정에서 "Gemini 3 Flash"를 직접 선택하거나, 자동 모드의 폴백 목록이 이 모델 차례에 도달하면
404 에러가 났다.

## 원인

2026-07-05 기준 Gemini API에는 `gemini-3-flash`라는 stable(비-preview) 이름의 모델이 실제로
존재하지 않는다 - 현재 API에 노출된 이름은 `gemini-3-flash-preview`뿐이다.

[11번 문서](11-preview-model-billing-and-silent-sdk-failures.md)에서 "preview 모델은 보통
결제가 켜져 있어야 한다"는 이유로 `gemini-3-flash-preview`를 자동 목록/드롭다운에서 의도적으로
빼고 `gemini-3-flash`(stable 이름)로 남겨뒀었는데, 정작 stable 이름 쪽이 아직 API에 출시되지
않은 상태였던 것이다. 이름만 보고 "preview가 아니니 안전하겠지"라고 판단한 게 오히려 존재하지도
않는 모델을 가리키게 만든 셈이다.

## 해결

`gemini-3-flash` → `gemini-3-flash-preview`로 수정했다.

- `TranslateService.DEFAULT_MODEL_FALLBACK` (자동 모드 폴백 순서)
- `ApiSettingsPanel.tsx`의 모델 드롭다운 옵션

preview 모델이 무료 티어 키에서 결제 없이 동작하지 않을 가능성은 여전히 남아있지만,
[23번 문서](23-gemini-flash-lite-untranslated-passthrough.md)에서 추가한 빈 응답/미번역
감지·폴백 로직이 이 모델에서 실패해도 자동으로 다음 모델(`gemini-2.5-flash`)로 넘어가 주므로,
자동 목록에 그대로 남겨둬도 안전하다고 판단했다.

## 참고

- Gemini 모델 ID는 (특히 preview → stable 전환 시기에) 공식 발표에서 예고된 이름과 실제 API에
  노출된 이름이 다를 수 있다. 404가 나면 "이름을 잘못 썼나"보다 먼저 "이 모델이 API에 실제로
  존재하는가"부터 의심할 것.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/TranslateService.java`,
  `dowoo/src/components/settings/ApiSettingsPanel.tsx`
