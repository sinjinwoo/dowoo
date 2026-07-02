# dowoo API 명세서

## 0. 문서 개요

### 0.1 목적

기존 프론트엔드(React + Vite, IndexedDB/LocalStorage 기반)를 백엔드 연동 구조로 전환하기 위한 API 명세서. 프론트엔드의 기존 타입(`src/types/novel.ts`, `src/types/settings.ts`)과 필드명을 최대한 맞춰서 마이그레이션 비용을 줄인다.

### 0.2 서비스 구성

| 서비스 | 기술 스택 | 담당 영역 | Base URL (개발) |
| --- | --- | --- | --- |
| **Core API** | Spring Boot + Spring Security OAuth2 Client | 회원가입/로그인(OAuth2), 서재(소설/챕터), 용어집, API 키·테마 설정, **AI API로의 내부 프록시** | `http://localhost:8080` |
| **AI API** | FastAPI (Python) | 웹소설 크롤링/파싱, Gemini 번역 스트리밍 (Core API 전용, 외부 비공개) | `http://localhost:8000` (사설 네트워크 전용) |

- 두 서비스는 **동일한 PostgreSQL 인스턴스**를 데이터 저장소로 공유한다. 단, 실제로 DB에 접근하는 것은 Core API뿐이다. AI API는 DB 접근 권한이 없는 완전한 stateless 계산 서비스로, 크롤링/번역에 필요한 모든 입력값(API 키, 프롬프트, 원문 등)을 Core API가 매 요청마다 body에 담아 전달한다.
- **프론트엔드는 Core API만 호출한다.** AI API는 외부에 노출되지 않고 Core API가 사설 네트워크에서만 호출하는 내부 전용 서비스다. 이렇게 하면 로그인/토큰 검증 로직이 Spring Boot(Core API) 한 곳에만 존재하면 되고, FastAPI 쪽에는 JWT 검증을 별도로 구현할 필요가 없다.
- 소설/챕터처럼 스키마가 고정된 데이터는 PostgreSQL의 일반 컬럼(RDB)으로, 테마 프리셋처럼 사용자가 자유롭게 구성하는 값(`Partial<ThemeSettings>`)은 PostgreSQL의 `JSONB` 컬럼으로 저장한다. 문서 구조가 자주 바뀌지 않는 이 프로젝트 규모에서는 별도 NoSQL(MongoDB 등) 없이 PostgreSQL 단일 DB + JSONB 조합으로 충분하다.

### 0.3 공통 응답 포맷

모든 REST 엔드포인트(SSE 제외)는 아래 포맷으로 응답한다.

```json
{
  "status": 200,
  "data": {},
  "message": "요청이 성공적으로 처리되었습니다.",
  "error": null
}
```

| 필드명 | 타입 | 설명 |
| --- | --- | --- |
| status | Integer | HTTP 상태 코드와 동일한 값 |
| data | Object \| Array \| null | 성공 시 실제 응답 데이터, 실패 시 `null` |
| message | String | 사람이 읽을 수 있는 처리 결과 메시지 |
| error | Object \| null | 실패 시 `{ code, details }`, 성공 시 `null` |

### 0.4 인증 방식

- Access Token: JWT(RS256), `Authorization: Bearer {accessToken}` 헤더로 전달. 만료 30분. **검증은 Core API에서만 수행**한다.
- Refresh Token: `httpOnly`, `Secure`, `SameSite=Lax` 쿠키(`refresh_token`)로 전달. 만료 14일. JS에서 접근 불가.
- AI API는 JWT를 전혀 검증하지 않는다 (사용자 인증 개념 자체가 없음). 대신 Core API → AI API 요청에 고정 서비스 시크릿인 `X-Internal-Token` 헤더를 실어 보내고, AI API는 이 값이 일치하는지만 확인한다. 자세한 내부 엔드포인트는 9장 참고.

### 0.5 공통 에러 코드

| 에러 코드 | HTTP 상태 | 설명 |
| --- | --- | --- |
| `UNAUTHORIZED` | 401 | Access Token이 없거나 유효하지 않음 |
| `TOKEN_EXPIRED` | 401 | Access Token 만료 (프론트는 `/auth/refresh` 호출 후 재시도) |
| `FORBIDDEN` | 403 | 본인 소유가 아닌 리소스 접근 |
| `RESOURCE_NOT_FOUND` | 404 | 요청한 리소스가 존재하지 않음 |
| `VALIDATION_ERROR` | 400 | 요청 값 검증 실패 |
| `RATE_LIMITED` | 429 | 요청 빈도 초과 |
| `INTERNAL_ERROR` | 500 | 서버 내부 오류 |

도메인 전용 에러 코드는 각 엔드포인트 섹션에서 별도 기술한다.

### 0.6 데이터 모델 요약 (PostgreSQL)

| 테이블 | 주요 컬럼 | 비고 |
| --- | --- | --- |
| `users` | id(uuid, PK), email, oauth_provider, oauth_id, created_at, withdrawn_at | `(oauth_provider, oauth_id)` 유니크. 커뮤니티 기능이 없어 닉네임/프로필 이미지 등 표시용 필드는 두지 않는다 — 계정은 API 키·서재를 묶는 식별자 역할만 한다 |
| `refresh_tokens` | id, user_id(FK), token_hash, expires_at, created_at | 로그아웃/재발급 시 갱신 |
| `novels` | id(uuid, PK), user_id(FK), title, original_title, cover_url, source_url, site_name, last_read_chapter_index, last_read_scroll_pos, order_index, created_at, updated_at | 사용자별 서재 |
| `novel_prompts` | id(uuid, PK), novel_id(FK, **유니크**), system_prompt, translation_note, updated_at | `novels`와 1:1. 소설마다 바뀌는 프롬프트/용어집만 따로 분리해 히스토리 관리·캐싱을 novels 본문과 독립적으로 할 수 있게 함 |
| `chapters` | id(uuid, PK), novel_id(FK), source_url, title, original_text, translated_text, prev_url, next_url, chapter_index, created_at, updated_at | `(novel_id, source_url)` 유니크 |
| `api_key_settings` | id, user_id(FK, 유니크), model, thinking_budget, updated_at | |
| `api_keys` | id, user_id(FK), encrypted_key, key_order, created_at | 로테이션 순서 = `key_order` |
| `theme_settings` | id, user_id(FK, 유니크), font_family, font_color, bg_color, font_size, font_weight, line_height, text_indent, updated_at | |
| `theme_presets` | id, user_id(FK), name, theme(**JSONB**), created_at | `theme` 컬럼이 `Partial<ThemeSettings>` 자유 형식을 그대로 저장 |

---

## 1. 인증(Auth) API — Core API

### 1.1 개요

OAuth2 Authorization Code 흐름을 사용한다. 최초 로그인 시 자동 회원가입되며, 별도의 이메일/비밀번호 가입 절차는 없다.

**로그인 시퀀스**

1. 프론트엔드가 브라우저를 `GET /oauth2/authorization/{provider}` 로 이동시킴 (Spring Security 기본 제공 엔드포인트).
2. 사용자가 Google/GitHub 동의 화면에서 로그인.
3. 제공자가 `GET /login/oauth2/code/{provider}` 로 콜백 (Spring Security 기본 제공, 프론트가 직접 호출하지 않음).
4. Core API는 최초 로그인이면 `users` 테이블에 신규 유저를 생성하고, 1회용 인가 코드(`code`)를 발급하여 프론트엔드 redirect URI로 302 리다이렉트 (`{FRONTEND_URL}/oauth/callback?code=xxxx`).
5. 프론트엔드는 전달받은 `code`를 `POST /api/v1/auth/exchange` 로 교환하여 Access Token을 받는다 (Refresh Token은 이 응답에서 `Set-Cookie`로 함께 내려감).
6. 이후 요청은 `Authorization: Bearer {accessToken}` 헤더 사용, 만료 시 `POST /api/v1/auth/refresh` 로 재발급.

### 1.2 `POST /api/v1/auth/exchange`

**설명**: 1회용 인가 코드를 Access Token으로 교환한다.

**📤 Request**

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| code | String | 4단계에서 발급받은 1회용 코드 | ✅ |

```json
{ "code": "8f3a2e1c-..." }
```

**📥 Response**

**HTTP Status Code**: `200 OK`

```json
{
  "status": 200,
  "data": {
    "accessToken": "eyJhbGciOi...",
    "accessTokenExpiresIn": 1800,
    "user": {
      "id": "5e1c...",
      "email": "user@example.com",
      "oauthProvider": "google"
    }
  },
  "message": "로그인에 성공했습니다.",
  "error": null
}
```

응답 헤더에 `Set-Cookie: refresh_token=...; HttpOnly; Secure; SameSite=Lax; Max-Age=1209600` 포함.

**🔥 ERROR**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `INVALID_AUTH_CODE` | 400 | code가 존재하지 않거나 이미 사용됨/만료됨(5분) |

### 1.3 `POST /api/v1/auth/refresh`

**설명**: 쿠키의 Refresh Token으로 새 Access Token을 발급한다. Body 없음, 쿠키만 사용.

**📥 Response**

**HTTP Status Code**: `200 OK`

```json
{
  "status": 200,
  "data": { "accessToken": "eyJhbGciOi...", "accessTokenExpiresIn": 1800 },
  "message": "토큰이 재발급되었습니다.",
  "error": null
}
```

**🔥 ERROR**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `REFRESH_TOKEN_INVALID` | 401 | 쿠키가 없거나, DB에 없거나(로그아웃됨), 만료됨 |

### 1.4 `POST /api/v1/auth/logout`

**설명**: 현재 Refresh Token을 DB에서 무효화하고 쿠키를 삭제한다. (인증 필요)

**📥 Response**: `200 OK`, `data: null`, `message: "로그아웃되었습니다."`

### 1.5 `GET /api/v1/users/me`

**설명**: 로그인된 사용자 정보 조회. 커뮤니티 기능이 없어 닉네임/프로필 이미지 같은 표시용 필드는 응답에 없다 — 계정은 API 키·서재를 묶는 식별자 역할만 한다. (인증 필요)

**📥 Response**

```json
{
  "status": 200,
  "data": {
    "id": "5e1c...",
    "email": "user@example.com",
    "oauthProvider": "google",
    "createdAt": "2026-01-10T09:00:00Z"
  },
  "message": "조회 성공",
  "error": null
}
```

### 1.6 `DELETE /api/v1/users/me`

**설명**: 회원 탈퇴. 사용자 레코드에 `withdrawn_at`을 기록하는 소프트 삭제 후, 소유한 `novels`/`chapters`/`api_keys`/`theme_*` 데이터를 비동기로 정리한다. Refresh Token도 즉시 무효화된다. (인증 필요)

---

## 2. 서재(Library) - 소설(Novel) API — Core API

프론트엔드 `Novel` 타입과 1:1로 매핑된다. 모든 엔드포인트는 인증 필요하며, 본인 소유 소설만 조회/수정 가능(`FORBIDDEN`).

`systemPrompt`/`translationNote`는 API 요청·응답 상에서는 지금까지처럼 `Novel` 객체의 필드로 그대로 노출되지만, 서버 내부적으로는 `novels`와 1:1인 `novel_prompts` 테이블에 별도 저장된다(0.6 참고). Core API가 조회 시 두 테이블을 조인해 하나의 객체로 합쳐서 내려준다.

### 2.1 `GET /api/v1/novels`

**설명**: 내 서재의 소설 목록을 `order_index` 오름차순으로 조회한다. 목록 조회 시 챕터 본문(`original_text`, `translated_text`)은 포함하지 않고 챕터 개수만 반환해 응답 크기를 줄인다.

**Query Parameter**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| keyword | String | 제목/원제목 부분 검색 | ❌ |

**📥 Response**

```json
{
  "status": 200,
  "data": [
    {
      "id": "n_01",
      "title": "이세계 転生 이야기",
      "originalTitle": "異世界転生物語",
      "coverUrl": "https://...",
      "sourceUrl": "https://ixdzs8.com/read/562760/p601.html",
      "siteName": "ixdzs8.com",
      "chapterCount": 42,
      "lastReadChapterIndex": 12,
      "order": 0,
      "updatedAt": "2026-07-01T10:00:00Z"
    }
  ],
  "message": "조회 성공",
  "error": null
}
```

### 2.2 `POST /api/v1/novels`

**설명**: URL 또는 붙여넣은 텍스트로 새 소설을 서재에 등록한다. `title`을 비워두면 크롤링 결과에서 감지한 원문 제목을 그대로 사용한다.

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| sourceUrl | String | 소설(첫/현재 회차) URL | ✅ |
| siteName | String | 사이트 호스트명 (예: `ixdzs8.com`) | ✅ |
| title | String | 미입력 시 원문 제목 자동 감지 | ❌ |
| originalTitle | String | | ❌ |
| coverUrl | String | | ❌ |
| systemPrompt | String | 미입력 시 기본 시스템 프롬프트 사용 | ❌ |
| translationNote | String | 등장인물/고유명사 번역 노트 | ❌ |

**📥 Response**: `201 Created`, `data`는 생성된 `Novel` 객체 (2.1과 동일 형태 + `chapters: []`).

**🔥 ERROR**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `DUPLICATE_NOVEL` | 409 | 동일 `sourceUrl`의 소설이 이미 서재에 존재 |
| `VALIDATION_ERROR` | 400 | `sourceUrl`이 URL 형식이 아님 |

### 2.3 `GET /api/v1/novels/{novelId}`

**설명**: 소설 상세 + 전체 챕터 목록(본문 제외, `id/title/sourceUrl/chapterIndex` 등 메타만) 조회.

### 2.4 `PATCH /api/v1/novels/{novelId}`

**설명**: 제목/표지/시스템 프롬프트/번역 노트 등 메타데이터 수정. (`NovelMetaEditModal`에 대응)

**Request Body**: 2.2의 필드 중 변경할 값만 포함 (부분 수정).

### 2.5 `DELETE /api/v1/novels/{novelId}`

**설명**: 소설과 하위 챕터 전체 삭제 (cascade).

### 2.6 `PATCH /api/v1/novels/reorder`

**설명**: 서재 내 소설 정렬 순서를 일괄 변경한다.

**Request Body**

```json
{ "orderedIds": ["n_03", "n_01", "n_02"] }
```

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| orderedIds | String[] | 원하는 순서대로 나열한 소설 id 배열 (본인 소유 전체와 개수 일치해야 함) | ✅ |

**🔥 ERROR**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `VALIDATION_ERROR` | 400 | `orderedIds`가 본인 소유 novel id 집합과 일치하지 않음 |

### 2.7 `PATCH /api/v1/novels/{novelId}/last-read`

**설명**: 자동 이어보기용 마지막 읽은 위치 저장. 뷰어에서 스크롤/챕터 변경 시 짧은 주기로 호출됨 (디바운스는 프론트 책임).

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| lastReadChapterIndex | Integer | | ✅ |
| lastReadScrollPos | Number | | ❌ |

### 2.8 `GET /api/v1/novels/{novelId}/export`

**설명**: 번역 완료된 챕터를 순서대로 이어 붙여 `txt` 파일로 다운로드한다.

**Query Parameter**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| lang | String | `translated`(기본) \| `original` \| `both` | ❌ |

**📥 Response**: `200 OK`, `Content-Type: text/plain; charset=utf-8`, `Content-Disposition: attachment; filename="{title}.txt"` — 이 엔드포인트만 예외적으로 JSON 봉투를 쓰지 않고 파일 스트림을 그대로 반환한다.

**🔥 ERROR**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `NO_TRANSLATED_CHAPTERS` | 400 | 번역된 챕터가 하나도 없음 |

---

## 3. 챕터(Chapter) API — Core API

프론트엔드 `Chapter` 타입과 매핑. 크롤링/번역의 실제 계산은 AI API가 수행하지만, 프론트가 직접 호출하는 창구는 항상 Core API다(6장/7장). 이 3장은 그 결과의 **저장/조회** 엔드포인트다.

### 3.1 `GET /api/v1/novels/{novelId}/chapters/{chapterId}`

**설명**: 챕터 원문/번역문 조회 (뷰어 진입 시 호출).

**📥 Response**

```json
{
  "status": 200,
  "data": {
    "id": "c_012",
    "novelId": "n_01",
    "title": "12화. 각성",
    "sourceUrl": "https://ixdzs8.com/read/562760/p612.html",
    "originalText": "……",
    "translatedText": "……",
    "prevUrl": "https://ixdzs8.com/read/562760/p611.html",
    "nextUrl": "https://ixdzs8.com/read/562760/p613.html",
    "chapterIndex": 12
  },
  "message": "조회 성공",
  "error": null
}
```

### 3.2 `POST /api/v1/novels/{novelId}/chapters`

**설명**: 6장에서 크롤링한(또는 사용자가 직접 붙여넣은) 원문 결과를 새 챕터로 저장한다 (원문만 먼저 저장, 번역문은 7장 번역 완료 후 자동 반영). 이미 같은 `sourceUrl`의 챕터가 있으면 갱신 없이 기존 챕터를 그대로 반환한다(캐시 히트).

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| sourceUrl | String | | ✅ |
| title | String | | ✅ |
| originalText | String | | ✅ |
| prevUrl | String | | ❌ |
| nextUrl | String | | ❌ |

**📥 Response**: `201 Created` (신규) 또는 `200 OK` (기존 캐시 히트), `data`는 3.1과 동일 형태(`translatedText`는 신규 시 `""`).

### 3.3 `PATCH /api/v1/novels/{novelId}/chapters/{chapterId}`

**설명**: 번역문을 수동으로 덮어쓴다. 7장의 스트리밍 번역이 끝나면(`done` 이벤트) Core API가 이미 자동으로 저장하므로, 이 엔드포인트는 사용자가 번역 결과를 직접 수정하거나 재번역 이전 상태로 되돌리는 등 **수동 편집 용도**로만 쓰인다.

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| translatedText | String | | ✅ |

### 3.4 `DELETE /api/v1/novels/{novelId}/chapters/{chapterId}`

**설명**: 챕터 삭제 (재번역을 위해 캐시를 지우는 용도).

---

## 4. 설정 - API 키 API — Core API

### 4.1 `GET /api/v1/settings/api`

**설명**: 저장된 Gemini API 키 설정을 조회한다. 원문 키는 절대 반환하지 않고 마스킹된 값만 내려준다.

**📥 Response**

```json
{
  "status": 200,
  "data": {
    "model": "gemini-2.5-flash",
    "thinkingBudget": null,
    "apiKeys": [
      { "id": "k_1", "masked": "AIza********f92k", "order": 0 },
      { "id": "k_2", "masked": "AIza********a10x", "order": 1 }
    ]
  },
  "message": "조회 성공",
  "error": null
}
```

### 4.2 `PUT /api/v1/settings/api`

**설명**: API 키 목록(로테이션 순서 포함)과 모델 설정을 전체 교체 저장한다. 키는 서버에서 AES-256-GCM으로 암호화되어 `api_keys.encrypted_key`에 저장된다.

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| apiKeys | String[] | 줄 단위로 입력된 키 목록, 배열 순서 = 로테이션 순서 | ✅ |
| model | String | 예: `gemini-2.5-flash`, `gemini-2.5-pro` | ✅ |
| thinkingBudget | Integer | | ❌ |

**🔥 ERROR**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `INVALID_API_KEY_FORMAT` | 400 | 키에 ASCII 출력 가능 문자(`\x21`-`\x7E`) 이외 문자가 포함됨 |

### 4.3 `DELETE /api/v1/settings/api/keys/{keyId}`

**설명**: 개별 키 하나만 삭제 (전체 교체 없이).

---

## 5. 설정 - 테마 API — Core API

### 5.1 `GET /api/v1/settings/theme`

**설명**: 현재 사용자의 테마 설정 조회. 저장된 값이 없으면 기본값(`defaults.ts`의 `defaultTheme`)을 반환한다.

### 5.2 `PUT /api/v1/settings/theme`

**Request Body**: 프론트엔드 `ThemeSettings` 타입과 동일 (`fontFamily`, `fontColor`, `bgColor`, `fontSize`, `fontWeight`, `lineHeight`, `textIndent`).

### 5.3 `GET /api/v1/settings/theme/presets`

**설명**: 사용자가 저장한 커스텀 테마 프리셋 목록 조회.

**📥 Response**

```json
{
  "status": 200,
  "data": [
    { "name": "미디엄 그린", "theme": { "bgColor": "#eef3ec", "fontColor": "#1f2a1f" } }
  ],
  "message": "조회 성공",
  "error": null
}
```

`theme` 필드는 `theme_presets.theme`(JSONB) 컬럼 값을 그대로 반환한다 — 프리셋마다 저장하는 속성 개수가 달라도(`Partial<ThemeSettings>`) 스키마 변경 없이 저장 가능한 것이 JSONB를 쓰는 이유다.

### 5.4 `POST /api/v1/settings/theme/presets`

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| name | String | 프리셋 이름 (사용자별 유니크) | ✅ |
| theme | Object | `Partial<ThemeSettings>` | ✅ |

### 5.5 `DELETE /api/v1/settings/theme/presets/{name}`

---

## 6. 크롤링(Crawl) API — Core API (내부적으로 AI API 위임)

### 6.1 `POST /api/v1/crawl`

**설명**: 소설 사이트 URL을 받아 광고/스크립트를 제거한 본문과 제목, 이전/다음 화 URL을 반환한다. 69shuba.com·twkan.com은 Cloudflare 챌린지로 프론트엔드 `fetch` 직접 호출이 막혀 있어 반드시 서버를 경유해야 한다. 프론트는 이 Core API 엔드포인트만 호출하며, Core API가 내부망에서 AI API의 `POST /internal/crawl`(9.1)을 대신 호출해 결과를 그대로 감싸서 돌려준다. (인증 필요, `Authorization: Bearer`)

**지원 사이트**: `ixdzs8.com`, `m.xsw.tw`, `69shuba.com`(`www.69shuba.com` 포함), `twkan.com`

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| url | String | 회차 URL | ✅ |

**📥 Response**

```json
{
  "status": 200,
  "data": {
    "title": "12화. 각성",
    "content": "……원문 본문……",
    "prevUrl": "https://ixdzs8.com/read/562760/p611.html",
    "nextUrl": "https://ixdzs8.com/read/562760/p613.html",
    "siteName": "ixdzs8.com"
  },
  "message": "크롤링 성공",
  "error": null
}
```

**🔥 ERROR**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `UNSUPPORTED_SITE` | 400 | 등록된 파서가 없는 호스트 |
| `INVALID_URL` | 400 | URL 형식이 아니거나 http/https가 아님 |
| `CRAWL_TARGET_ERROR` | 502 | 대상 사이트가 4xx/5xx를 반환 (Cloudflare 차단 포함) |
| `PARSE_FAILED` | 502 | 응답은 받았지만 파서가 본문/제목을 추출하지 못함 (사이트 마크업 변경 추정) |
| `AI_API_UNAVAILABLE` | 503 | Core API가 AI API 내부 호출(9.1)에 실패 (네트워크/타임아웃) |

**추가 참고사항**

- 텍스트 직접 붙여넣기(URL 없이 본문만 입력)의 경우, 이 API를 호출하지 않고 프론트에서 곧바로 3.2(`POST .../chapters`)로 저장한다. 이 경우 `sourceUrl`은 프론트가 생성한 로컬 식별자(`pasted:{uuid}`)를 사용하고 `prevUrl`/`nextUrl`은 `null`이다.

---

## 7. 번역(Translate) API — Core API (내부적으로 AI API 위임)

### 7.1 `POST /api/v1/novels/{novelId}/chapters/{chapterId}/translate/stream`

**설명**: 이미 저장된 챕터의 원문(`chapters.original_text`)을 번역한다. Body 없이 path의 `novelId`/`chapterId`만으로 동작한다.

동작 순서:
1. Core API가 자기 DB에서 해당 챕터의 `original_text`, 소설의 `novel_prompts`(시스템 프롬프트/번역 노트), 로그인 사용자의 `api_keys`(복호화)·`model`·`thinking_budget`을 모두 조회한다.
2. 조회한 값을 그대로 body에 담아 AI API의 `POST /internal/translate/stream`(9.2)을 호출한다.
3. AI API가 돌려주는 SSE 이벤트(`start`/`line`/`progress`/`done`/`error`)를 프론트에 그대로 릴레이(relay)한다.
4. `done` 이벤트를 받으면 Core API가 즉시 `chapters.translated_text`를 갱신해 저장한다 (프론트가 3.3을 별도로 호출할 필요 없음).

이 흐름 덕분에 AI API는 DB나 사용자 인증을 전혀 몰라도 되는 순수 계산 서비스로 남는다. 이 요청 자체는 인증 필요(`Authorization: Bearer`).

**📥 Response**: `Content-Type: text/event-stream`. 이벤트 타입별 `data`는 JSON (AI API가 보낸 이벤트를 그대로 전달).

| event | data 예시 | 설명 |
| --- | --- | --- |
| `start` | `{ "totalLines": 84 }` | 원문을 줄 단위로 나눈 총 라인 수 (진행률 계산 기준) |
| `line` | `{ "index": 0, "text": "그날 하늘은 유난히 붉었다." }` | 번역된 한 줄. 도착 즉시 화면에 줄 단위로 표시 |
| `progress` | `{ "percent": 42 }` | 원형 로딩 인디케이터용 진행률(0~100). `receivedLines / totalLines` 기준, 완료 전까지 최대 95%로 캡핑 |
| `done` | `{ "translatedText": "……전체 번역문……" }` | 스트림 종료. Core API에 저장(3.3)은 이 이벤트 수신 후 프론트가 수행 |
| `error` | `{ "code": "INVALID_API_KEY", "message": "API 키가 올바르지 않습니다. 설정 화면에서 API 키를 다시 확인해주세요." }` | 스트림 도중 실패 |

**🔥 ERROR (스트림 시작 전 즉시 실패하는 경우, 일반 JSON 응답)**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `NO_API_KEY` | 400 | 사용자가 등록한 API 키가 하나도 없음 (Core API가 AI API 호출 전에 자체 확인) |
| `INVALID_API_KEY` | 502 | 모든 로테이션 키가 401/403 반환 |
| `QUOTA_EXCEEDED` | 502 | 모든 로테이션 키가 429(사용량 한도) 반환 |
| `UPSTREAM_ERROR` | 502 | Gemini 서버 5xx |
| `AI_API_UNAVAILABLE` | 503 | Core API가 AI API 내부 호출(9.2)에 실패 (네트워크/타임아웃) |

키 로테이션 정책, `{{memo}}` 치환 등 실제 번역 동작의 세부 사항은 9.2(AI API가 수행하는 실제 처리)에 정리되어 있다.

---

## 8. 인증/인가 요약

| 구분 | 대상 API |
| --- | --- |
| 인증 불필요 | `POST /api/v1/auth/exchange`, `POST /api/v1/auth/refresh`, OAuth2 관련 프레임워크 엔드포인트 |
| 인증 필요(Bearer) | 그 외 Core API 전체 (1~7장) |
| 내부 전용(`X-Internal-Token`, Core API만 호출 가능) | AI API 전체 (9장) — 프론트에서 직접 호출 불가 |

---

## 9. 내부 전용 API — AI API (Core API 전용 호출)

이 장의 엔드포인트는 모두 **AI API(FastAPI)가 제공**하며, 외부(프론트엔드)에서는 절대 호출할 수 없다. 사설 네트워크(Docker 내부망 등)에서만 접근 가능하고, 모든 요청에 `X-Internal-Token`(고정 서비스 시크릿) 헤더가 필요하다. AI API는 이 토큰이 일치하는지만 확인할 뿐 JWT를 검증하거나 DB를 조회하지 않는다 — 필요한 데이터는 항상 Core API가 요청 body에 담아 보낸다.

### 9.1 `POST /internal/crawl`

**설명**: 6.1이 위임하는 실제 크롤링/파싱 수행. Core API의 6.1과 요청/응답 형태가 동일하다.

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| url | String | 회차 URL | ✅ |

**📥 Response**

```json
{
  "status": 200,
  "data": {
    "title": "12화. 각성",
    "content": "……원문 본문……",
    "prevUrl": "https://ixdzs8.com/read/562760/p611.html",
    "nextUrl": "https://ixdzs8.com/read/562760/p613.html",
    "siteName": "ixdzs8.com"
  },
  "message": "크롤링 성공",
  "error": null
}
```

**🔥 ERROR**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `INTERNAL_UNAUTHORIZED` | 401 | `X-Internal-Token` 누락/불일치 |
| `UNSUPPORTED_SITE` | 400 | 등록된 파서가 없는 호스트 |
| `CRAWL_TARGET_ERROR` | 502 | 대상 사이트가 4xx/5xx 반환 |
| `PARSE_FAILED` | 502 | 파서가 본문/제목을 추출하지 못함 |

### 9.2 `POST /internal/translate/stream`

**설명**: 7.1이 위임하는 실제 Gemini 스트리밍 번역 수행. 사용자 컨텍스트를 Core API가 모두 body로 넘겨주므로, AI API는 이 값만으로 Gemini를 호출한다.

**Request Body**

| 필드명 | 타입 | 설명 | 필수 |
| --- | --- | --- | --- |
| apiKeys | String[] | 복호화된 API 키 목록, 배열 순서 = 로테이션 순서 | ✅ |
| model | String | | ✅ |
| thinkingBudget | Integer | | ❌ |
| systemPrompt | String | `{{memo}}` 치환 전 원본 템플릿 | ✅ |
| translationNote | String | `{{memo}}`에 들어갈 값 | ❌ |
| originalText | String | 번역할 원문 | ✅ |

**📥 Response**: `Content-Type: text/event-stream`

| event | data 예시 | 설명 |
| --- | --- | --- |
| `start` | `{ "totalLines": 84 }` | 원문을 줄 단위로 나눈 총 라인 수 (진행률 계산 기준) |
| `line` | `{ "index": 0, "text": "그날 하늘은 유난히 붉었다." }` | 번역된 한 줄 |
| `progress` | `{ "percent": 42 }` | 진행률(0~100). `receivedLines / totalLines` 기준, 완료 전까지 최대 95%로 캡핑 |
| `done` | `{ "translatedText": "……전체 번역문……" }` | 스트림 종료 |
| `error` | `{ "code": "INVALID_API_KEY", "message": "API 키가 올바르지 않습니다. 설정 화면에서 API 키를 다시 확인해주세요." }` | 스트림 도중 실패 |

**🔥 ERROR (스트림 시작 전 즉시 실패, 일반 JSON 응답)**

| 에러 코드 | HTTP 상태 | 발생 조건 |
| --- | --- | --- |
| `INTERNAL_UNAUTHORIZED` | 401 | `X-Internal-Token` 누락/불일치 |
| `INVALID_API_KEY` | 502 | 모든 로테이션 키가 401/403 반환 |
| `QUOTA_EXCEEDED` | 502 | 모든 로테이션 키가 429 반환 |
| `UPSTREAM_ERROR` | 502 | Gemini 서버 5xx |

**추가 참고사항**

- 키 로테이션 정책: 매 요청마다 시작 키를 무작위로 선택 후 순차 폴백(기존 `geminiClient.ts`의 `translateStream` 로직을 그대로 이전). 인증/한도 에러(401/403/429)일 때만 다음 키로 넘어가고, 그 외 에러는 즉시 `error` 이벤트로 종료.
- `{{memo}}` 플레이스홀더가 포함된 시스템 프롬프트는 AI API가 `translationNote`로 치환한 뒤 Gemini에 전달한다 (`resolveSystemPrompt` 로직 이전).

---

## 10. 다음 단계 (Open Items)

- [ ] Spring Boot 프로젝트 초기화 (Spring Security OAuth2 Client, Spring Data JPA, Flyway 마이그레이션)
- [ ] FastAPI 프로젝트 초기화 (기존 `src/crawl/parser/*.ts` 파서 로직을 Python(BeautifulSoup 등)으로 이식)
- [ ] Google/GitHub OAuth2 앱 등록 및 redirect URI 확정
- [ ] `api_keys` 암호화 키 관리 방식 결정 (환경변수 vs KMS)
- [ ] `X-Internal-Token` 서비스 시크릿 발급/배포 방식 결정 (환경변수 공유, Docker Compose 내부망 분리 등)
- [ ] 프론트엔드 IndexedDB(`db.ts`, `novelsRepo.ts`, `settingsRepo.ts`) 코드를 위 API 호출로 교체, 오프라인 캐시 레이어로 재설계
