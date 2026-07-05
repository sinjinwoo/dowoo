# 뷰어 커스텀 폰트가 실제로 적용되지 않음 (아이폰에서 특히 두드러짐)

## 증상

테마 설정에서 폰트(프리텐다드/노토 산스/노토 세리프/나눔고딕/나눔명조)를 선택해도 실제 렌더링에
반영되지 않는 것처럼 보였다. 특히 아이폰에서 이 문제가 두드러졌다.

## 원인

`dowoo/src/data/fontOptions.ts`에 등록된 폰트 이름들은 CSS `font-family` 값으로만 쓰이고
있었고, 정작 그 폰트를 실제로 내려받는 `@font-face`나 Google Fonts 같은 스타일시트 선언이
프로젝트 어디에도 없었다. 즉 이 기능은 애초에 **모든 플랫폼에서** 동작한 적이 없었다.

- Windows/Android에서는 브라우저가 폰트를 못 찾으면 조용히 다음 후보(`system-ui`, `sans-serif`
  등)로 대체하는데, 이 대체 폰트가 우연히 지정 폰트와 비슷하게 생겨서 차이가 잘 안 보였다.
- iOS는 대체 시스템 폰트(San Francisco 계열)가 확연히 달라서 문제가 뚜렷하게 드러났다.

CSS `font-family`에 이름을 지정하는 것과 그 폰트를 실제로 로드하는 것은 완전히 별개의 일이며,
브라우저는 폰트를 못 찾아도 아무 에러 없이 조용히 폴백하기 때문에 지금까지 발견되지 않았다.

## 해결

`dowoo/index.html`의 `<head>`에 실제 웹폰트 로딩을 추가했다.

- Google Fonts: Noto Sans KR, Noto Serif KR, Nanum Gothic, Nanum Myeongjo
  (`fonts.googleapis.com` / `fonts.gstatic.com` 프리커넥트 + 스타일시트 링크)
- Pretendard: Google Fonts에 없어서 jsdelivr CDN(`orioncactus/pretendard`)의 static CSS를
  별도로 링크

`ThemeSettingsPanel.tsx`/`fontOptions.ts` 쪽 코드는 수정하지 않았다 - 문제는 폰트 이름 선택
로직이 아니라 그 폰트를 로드하는 수단이 아예 없었다는 것뿐이었다.

## 참고

- 이런 종류의 버그("폰트가 다르게 보이긴 하는데 확신은 안 선다" 수준)는 데스크톱에서는 놓치기
  쉽고, 대체 폰트가 확연히 다른 플랫폼(iOS)에서야 확실히 드러난다. 플랫폼별 기본 폰트가 서로
  비슷하게 생겼을 가능성이 있는 기능은 한 플랫폼에서만 확인하고 넘어가지 말 것.
- CLAUDE.md의 "폰트 지정(URL 연동)" 요구사항(사용자가 폰트 URL을 직접 입력하는 기능)은 아직
  구현되지 않았다 - 지금은 5종 프리셋만 있고, 로딩도 이번에 고정 링크로 넣은 것이라 사용자 지정
  URL은 별도 작업이 필요하다.
- 관련 파일: `dowoo/index.html`, `dowoo/src/data/fontOptions.ts`
