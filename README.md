# dowoo

사용자가 직접 Google Gemini API 키와 웹소설 사이트 주소(또는 텍스트)를 입력하면, AI가 실시간으로 번역해서 보여주는 웹소설 리더.

[syosetu.colomo.dev](https://syosetu.colomo.dev/)를 참고해 기능을 발전시켜 개발 중.

## 주요 기능

- **Gemini API 연동**: API 키 여러 개를 줄 단위로 등록하면 요청마다 무작위로 순환 사용(한도 분산), 번역 모델 선택
- **실시간 스트리밍 번역**: 번역문이 도착하는 대로 줄 단위로 화면에 표시, 언제든 취소 가능(취소해도 그때까지 번역된 내용은 저장됨)
- **웹소설 크롤링**: URL 또는 직접 붙여넣은 텍스트 모두 입력 가능. 지원 사이트: ixdzs8.com, m.xsw.tw (69shuba.com, twkan.com은 Cloudflare 봇 차단으로 현재 미지원 — 진행 상황은 아래 참고)
- **내 서재**: 번역한 소설을 목록으로 관리, 드래그 앤 드롭으로 순서 변경, 소설별 시스템 프롬프트/번역 노트 편집
- **번역 노트(용어집)**: 소설별로 고유명사/인물 이름/말투를 지정해두면 번역 시 자동 반영 (`{{memo}}` 플레이스홀더)
- **읽기 테마**: 폰트/글자색/배경색/크기/줄간격/들여쓰기 조절, 원클릭 테마 프리셋 + 커스텀 테마 저장(미리보기 포함)
- **원문 대조**: 번역 문단을 클릭하면 원문이 작은 글씨로 펼쳐짐
- **모바일 최적화**: 반응형 햄버거 메뉴, 스크롤/탭으로 상단바 숨김·노출

## 기술 스택

- React 19 + TypeScript + Vite + Tailwind CSS v4
- [Dexie.js](https://dexie.org/) (IndexedDB) — 서재/설정/캐시 저장 *(→ 셀프호스팅 전환에 따라 MongoDB로 이전 예정)*
- [@google/genai](https://www.npmjs.com/package/@google/genai) — Gemini 스트리밍 번역
- [@dnd-kit](https://dndkit.com/) — 서재 드래그 앤 드롭 정렬
- cheerio — 사이트별 HTML 파싱

## 현재 상태 / 진행 중인 방향 전환

지금까지는 Vercel류 정적 배포 + 브라우저 저장(IndexedDB/localStorage) 구조로 만들어왔으나, **셀프호스팅 서비스(Docker 컨테이너 + MongoDB)** 로 전환하기로 결정한 상태. 다음 작업 예정:

- [ ] 백엔드 서버 구축 및 MongoDB 스키마 설계
- [ ] 프런트엔드의 Dexie 호출을 백엔드 API 호출로 교체
- [ ] Docker / docker-compose 구성
- [ ] (선택) 헤드리스 브라우저 기반 크롤링으로 69shuba.com/twkan.com 지원 추가

## 개발 환경 실행

```bash
cd dowoo
npm install
npm run dev
```

`.env` 등 별도 설정 없이 앱 내 설정 화면에서 Gemini API 키를 입력하면 바로 사용 가능.
