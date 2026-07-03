# 서재의 책 제목이 챕터 제목과 똑같이 저장됨 - 사이트별 책 제목 추출 필요

## 증상

`/read`로 새 소설을 처음 만들면 `Novel.title`이 항상 크롤링한 **챕터** 제목과 동일하게 들어갔다. 예를 들어 twkan에서 "제225장 칼날로 상현 6을 베다" 같은 챕터를 처음 열면 서재에도 소설 제목이 "제225장 칼날로 상현 6을 베다"로 등록되어, 다른 챕터를 열 때마다 서재에 같은 책이 제목만 다르게 여러 개 생기는 것처럼 보였다.

## 원인

크롤러(`dowoo-python`)의 4개 파서(`idx.py`, `mxsw.py`, `shuba69.py`, `twkan.py`)는 애초에 **챕터 제목만** 추출하도록 만들어졌고, `ReadService`가 새 소설을 만들 때 그 챕터 제목을 그대로 `Novel.title`에 썼다. "책 제목"이라는 별도 개념 자체가 크롤링 단계부터 없었다.

## 해결

4개 사이트를 실제로 fetch해서 책 제목이 어디에 있는지 직접 확인했다.

- **69shuba.com / twkan.com**: 이미 `shuba_family.py`의 `extract_bookinfo()`가 페이지의 `bookinfo` JS 객체에서 `articlename`(책 제목)과 `chaptername`(챕터 제목)을 둘 다 추출하고 있었는데, 정작 `articlename`을 쓰는 곳이 없었다. 그냥 반환값에 추가하기만 하면 됐다.
- **ixdzs8.com**: 챕터 페이지 안에 책 소개 페이지로 돌아가는 링크(`<a href="/read/{bookId}/">{책 제목}</a>`)가 있어서 그 텍스트를 책 제목으로 썼다.
- **m.xsw.tw**: 마찬가지로 `<a href="/{bookId}/">{책 제목}信息頁</a>` 형태의 링크가 있어서, "信息頁"(정보 페이지) 접미사만 잘라내고 썼다.

**추가로 겪은 문제**: ixdzs8은 같은 `href="/read/562760/"`를 쓰는 링크가 **두 개** 있었다 - 하나는 페이지네이션 버튼 옆의 제네릭 라벨(`书籍页` = "책 페이지"), 다른 하나는 사이트 상단 브레드크럼의 진짜 책 제목. `soup.find()`로 첫 번째 매치만 가져왔더니 항상 "书籍页"라는 라벨 문자열이 책 제목으로 저장됐다. `soup.find_all()`로 모든 후보를 순회하면서 알려진 제네릭 라벨(`书籍页`/`書籍頁`)을 걸러내는 방식으로 고쳤다.

`Chapter`(§9.1/§6.1 응답)에 `bookTitle` 필드를 추가하고, `CrawlResult`(Java record)에도 같은 필드를 추가해 `ReadService`가 `bookTitle → title(챕터 제목) → sourceUrl` 순으로 폴백하며 `Novel.title`을 정하도록 했다.

## 참고

- 같은 `href`를 가진 여러 `<a>` 태그가 있을 수 있다는 걸 간과하고 `soup.find()`(첫 매치만)를 썼다가 틀렸다. 후보가 여러 개일 수 있는 선택자는 `find_all()`로 전부 확인하고 필터링하는 습관을 들일 것.
- 사이트 구조를 추측하지 않고 매번 실제로 fetch해서 raw HTML을 직접 눈으로 확인한 뒤 selector를 정했다 - 이 접근이 이번 세션 내내 유효했다([[06-playwright-blocked-by-cloudflare.md]], [[08-ixdzs8-js-redirect-challenge.md]] 참고).
- 관련 파일: `dowoo-python/app/crawl/parsers/idx.py`, `mxsw.py`, `shuba69.py`, `twkan.py`, `dowoo-python/app/crawl/registry.py`, `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/crawl/CrawlResult.java`, `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/ReadService.java`
