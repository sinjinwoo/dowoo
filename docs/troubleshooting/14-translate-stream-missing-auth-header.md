# 로그인 후 "불러오기"는 되는데 번역 스트림만 "인증이 필요합니다" 401

## 증상

로그인 기능을 붙인 뒤 실제로 로그인해서 테스트하니, 서재 목록/소설 상세 조회는 전부 정상 동작했는데 "불러오기"를 누르면 잠시 후 "인증이 필요합니다" 에러가 떴다. 브라우저 개발자 도구로 확인하니 실패한 요청은 다음과 같았다.

```
POST http://localhost:8080/api/v1/novels/{novelId}/chapters/{chapterId}/translate/stream -> 401
```

## 원인

로그인 기능을 붙이면서 `client.ts`의 `apiGet`/`apiPost`/`apiPatch`/`apiDelete`는 전부 `Authorization: Bearer {accessToken}` 헤더를 자동으로 붙이도록 고쳤지만, `translateStream.ts`는 로그인 기능이 생기기 전에 이미 작성돼 있던 코드로 **SSE 응답을 직접 파싱해야 해서 `client.ts`를 거치지 않고 자체적으로 `fetch()`를 호출**하고 있었다.

```ts
// 수정 전 - Authorization 헤더가 아예 없음
response = await fetch(
  `${API_BASE}/api/v1/novels/${novelId}/chapters/${chapterId}/translate/stream`,
  { method: 'POST', signal }
)
```

목록/상세 조회(`listNovels`, `getNovelDetail` 등)는 전부 `client.ts`의 `apiGet`/`apiPost`를 거치기 때문에 문제없이 동작했고, 유독 번역 스트림만 인증 헤더가 빠진 채 요청을 보내고 있었던 것이다.

## 해결

`client.ts`에서 액세스 토큰을 꺼낼 수 있는 `getAccessToken()`과, 만료 시 재발급하는 `refreshAccessToken()`을 export하도록 하고, `translateStream.ts`가 이를 사용해 직접 `Authorization` 헤더를 붙이도록 고쳤다. 401을 받으면 한 번 리프레시를 시도한 뒤 재요청하는 로직도 `client.ts`의 401 처리와 동일하게 맞췄다.

```ts
const doFetch = (token: string | null) =>
  fetch(streamUrl, {
    method: 'POST',
    signal,
    credentials: 'include',
    headers: token ? { Authorization: `Bearer ${token}` } : undefined,
  })

let response = await doFetch(getAccessToken())
if (response.status === 401) {
  const newToken = await refreshAccessToken()
  if (!newToken) {
    notifyUnauthorized()
    throw new Error('로그인이 필요합니다.')
  }
  response = await doFetch(newToken)
}
```

## 참고

- 인증을 나중에 추가하는 리팩터링을 할 때는, `fetch`를 감싸는 공용 클라이언트(`client.ts`)를 거치지 않고 **직접 `fetch`를 호출하는 코드가 없는지** 반드시 grep으로 확인해야 한다. SSE/스트리밍처럼 공용 클라이언트의 JSON 파싱 로직과 안 맞아 예외적으로 raw `fetch`를 쓰는 코드가 특히 이런 사각지대가 되기 쉽다.
- 관련 파일: `dowoo/src/api/translateStream.ts`, `dowoo/src/api/client.ts`
