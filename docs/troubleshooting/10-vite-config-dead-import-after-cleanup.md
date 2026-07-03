# 백엔드 마이그레이션 후 `npm run dev`가 vite.config.ts에서 바로 죽음

## 증상

프론트엔드를 Core API 호출 방식으로 마이그레이션하고 `src/crawl/`(클라이언트 크롤링 파서 전체)을 삭제한 뒤 `npm run dev`를 실행하면 Vite 설정 로딩 자체가 실패한다.

```
failed to load config from vite.config.ts
error when starting dev server:
Error: Build failed with 1 error:
[UNRESOLVED_IMPORT] Could not resolve './src/crawl/proxyFetch' in vite.config.ts
```

## 원인

`vite.config.ts`가 `src/crawl/proxyFetch.ts`의 `fetchViaProxy`를 직접 import해서, `npm run dev` 실행 시 `/api/proxy` 경로를 Vercel 서버리스 함수(`api/proxy.ts`)와 똑같이 흉내내는 개발 전용 미들웨어(`apiProxyDevMiddleware`)를 등록하고 있었다. 이건 "프론트가 직접 대상 사이트를 fetch하고, CORS만 우회 서버를 거친다"는 옛 아키텍처의 흔적이다. `src/crawl/`, `src/ai/`, `api/proxy.ts`를 지우면서 이 의존성도 같이 끊길 것을 놓치고 `vite.config.ts` 자체는 그대로 뒀다.

`tsc -b --noEmit`으로 사전에 타입 체크를 했을 때도 이 에러(`vite.config.ts(5,31): Relative import paths need explicit file extensions...`)가 이미 떠 있었는데, 다른 무관한 사전 존재 에러(`ToggleSwitch.tsx`)와 같은 위치에서 나와서 "이것도 그냥 기존에 있던 무관한 에러겠지"라고 잘못 판단하고 넘어갔다. 실제로는 내가 이번에 만든 회귀였다.

## 해결

크롤링이 이제 전부 서버(Core API → AI API)에서 처리되므로, 이 개발용 프록시 미들웨어 자체가 필요 없어졌다. `vite.config.ts`에서 관련 코드를 통째로 제거했다.

```diff
- import { defineConfig, type Plugin } from 'vite'
+ import { defineConfig } from 'vite'
  import react, { reactCompilerPreset } from '@vitejs/plugin-react'
  import babel from '@rolldown/plugin-babel'
  import tailwindcss from '@tailwindcss/vite'
- import { fetchViaProxy } from './src/crawl/proxyFetch'
-
- function apiProxyDevMiddleware(): Plugin { /* ... 전체 삭제 ... */ }

  export default defineConfig({
-   plugins: [react(), babel({ presets: [reactCompilerPreset()] }), tailwindcss(), apiProxyDevMiddleware()],
+   plugins: [react(), babel({ presets: [reactCompilerPreset()] }), tailwindcss()],
  })
```

## 참고

- 어떤 디렉터리를 삭제할 때는 `grep -rn "해당경로"`로 소스 코드(`src/`)뿐 아니라 **설정 파일**(`vite.config.ts`, `eslint.config.js` 등)까지 검색 범위에 넣어야 한다. 이번엔 `src` 안에서는 참조가 없는 걸 확인했지만 루트의 `vite.config.ts`는 검색하지 않았다.
- 사전 타입 체크(`tsc -b --noEmit`)에서 에러가 여러 개 뜰 때, "이건 내가 안 건드린 파일이니 상관없겠지"라고 넘겨짚지 말고 **각 에러가 정말 내 변경과 무관한지 하나씩 확인**해야 한다. `git diff --stat <파일>`로 실제로 그 세션에서 건드리지 않았는지 확인하는 것과, 그 에러가 내가 지운/바꾼 다른 파일 때문에 간접적으로 발생하지 않았는지는 별개의 질문이다.
- 관련 파일: `dowoo/vite.config.ts`
