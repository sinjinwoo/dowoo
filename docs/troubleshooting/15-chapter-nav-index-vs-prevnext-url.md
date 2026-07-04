# 이전편/다음편 버튼이 작동하지 않음 (URL이 아니라 로컬 인덱스만 옮기고 있었음)

## 증상

소설을 처음 읽을 때(챕터가 1개만 로드된 상태) "다음편" 버튼을 눌러도 아무 일도 일어나지 않았다. 이미 여러 챕터를 오갔던 소설이라도 실제 사이트의 다음 회차를 새로 불러오는 게 아니라, 이미 로드된 챕터 중 마지막 챕터에서 더 나아가지 못했다.

## 원인

`App.tsx`의 `ChapterNavBar` 핸들러가 `chapter.prevUrl`/`chapter.nextUrl`을 전혀 사용하지 않고, 이미 로컬에 로드된 `activeNovelDetail.chapters` 배열의 인덱스만 옮기고 있었다.

```ts
// 수정 전
<ChapterNavBar
  onPrevChapter={() => setCurrentChapterIndex((i) => Math.max(0, i - 1))}
  onNextChapter={() =>
    setCurrentChapterIndex((i) => Math.min(activeNovelDetail.chapters.length - 1, i + 1))
  }
/>
```

`Chapter` 타입에는 `prevUrl`/`nextUrl` 필드가 이미 있었고 크롤러도 이 값을 정상적으로 채워주고 있었지만, 정작 이동 버튼은 이 값을 한 번도 읽지 않았다. 소설을 처음 읽으면 `chapters` 배열에 챕터가 1개뿐이라 `Math.min(0, i+1)`이 항상 0으로 clamp되어 아무 이동도 일어나지 않았던 것.

`/api/v1/read`(§6.2)로 대신 처리하면 되지 않을까 싶었지만, api-spec.md 6.1의 "추가 참고사항"에 이미 명시돼 있듯 `/read`는 **캐시 미스 시 새 Novel을 자동 생성**한다 - novelId를 이미 알고 있는 prev/next 이동에 쓰면 같은 소설에 이어붙이는 대신 서재에 중복 소설이 생겨버린다.

## 해결

1. 이동하려는 URL이 이미 `activeNovelDetail.chapters`에 캐시돼 있으면(같은 소설 내에서 이미 방문한 챕터) 네트워크 호출 없이 바로 인덱스만 이동한다.
2. 캐시에 없으면 크롤링(`POST /api/v1/crawl`, §6.1) 결과로 챕터 생성(`POST /api/v1/novels/{novelId}/chapters`, §3.2)을 직접 두 단계로 호출해, 같은 소설 아래에 새 챕터를 추가한다.
3. `prevUrl`/`nextUrl`이 아예 없으면(원문 사이트에 더 이전/다음 회차가 없음) "이전 편이 없습니다"/"다음 편이 없습니다" 메시지를 보여준다.

```ts
const handleNavigateChapter = async (direction: 'prev' | 'next') => {
  if (!activeNovelDetail || !activeChapter) return
  abortControllerRef.current?.abort()
  const targetUrl = direction === 'prev' ? activeChapter.prevUrl : activeChapter.nextUrl
  if (!targetUrl) {
    setTranslationError({ type: 'crawling', message: direction === 'prev' ? '이전 편이 없습니다.' : '다음 편이 없습니다.' })
    return
  }

  const cachedIndex = activeNovelDetail.chapters.findIndex((c) => c.sourceUrl === targetUrl)
  if (cachedIndex !== -1) {
    setCurrentChapterIndex(cachedIndex)
    return
  }

  const crawled = await crawlUrl(targetUrl)
  const chapter = await createChapter(activeNovelDetail.id, {
    sourceUrl: targetUrl,
    title: crawled.title,
    originalText: crawled.content,
    prevUrl: crawled.prevUrl,
    nextUrl: crawled.nextUrl,
  })
  // ... detail 갱신 + setActiveChapter + handleTranslate
}
```

## 참고

- 프론트에 이미 있는 타입 필드(`Chapter.prevUrl`/`nextUrl`)라고 해서 실제로 UI 로직이 그 필드를 쓰고 있다고 가정하면 안 된다 - 스캐폴딩 단계에서 타입만 먼저 정의되고 실제 배선은 나중으로 미뤄진 필드일 수 있다.
- 같은 목적(챕터 조회/생성)을 가진 API가 여러 개 있을 때(`/read` vs 크롤링+챕터 생성 2단계), 각각이 "새 소설을 만들 수도 있는지" 같은 부작용 차이를 스펙 문서에서 다시 확인하고 상황에 맞는 쪽을 골라야 한다.
- 관련 파일: `dowoo/src/App.tsx`, `dowoo/src/api/novels.ts`, `dowoo/src/api/chapters.ts`, `docs/api-spec.md`(§6.1 추가 참고사항)
