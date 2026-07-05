# Contributing to dowoo

관심 가져주셔서 감사합니다! 이 문서는 dowoo에 기여하는 방법을 안내합니다.

## 🛠️ 개발 환경 설정

전체 스택을 로컬에서 띄우는 방법, 프론트엔드만 따로 개발하는 방법은 README의 [로컬 개발 / 소스 빌드 (테스트 환경)](README.md#로컬-개발--소스-빌드-테스트-환경) 섹션을 참고하세요.

아키텍처와 API 계약(엔드포인트, 요청/응답 형식, 인증 방식 등)은 [`docs/api-spec.md`](docs/api-spec.md)가 기준 문서입니다. 코드를 고치기 전에 먼저 읽어보시길 권합니다.

## 🌱 브랜치 & 커밋 규칙

- 브랜치명은 `<타입>/<짧은-설명>` 형태를 씁니다. 예: `feat/auth-login`, `fix/chapter-nav`, `docs/troubleshooting-update`.
- 커밋 메시지는 무엇을(what) 보다 왜(why) 바꿨는지를 중심으로, 한 줄 요약 + 필요하면 본문에 이유를 씁니다.
- 하나의 PR/커밋에는 하나의 논리적인 변경만 담는 것을 지향합니다. 로그인 기능과 무관한 버그 수정을 같은 커밋에 섞지 마세요.

## 🔀 Pull Request 절차

1. 이 저장소를 **Fork**합니다.
2. Fork한 저장소의 `develop` 브랜치에서 새 브랜치를 생성합니다.
3. 변경 사항을 커밋하고 자신의 Fork에 Push합니다.
4. 본 저장소의 `develop` 브랜치를 대상으로 Pull Request를 생성합니다.
5. PR 작성 시 [PR 템플릿](.github/pull_request_template.md)을 작성해 주세요. 특히 **변경 이유**와 **영향받는 컴포넌트**를 함께 작성해 주시면 리뷰에 큰 도움이 됩니다.
6. Dockerfile, `docker-compose`, 환경변수 등 **배포 이미지에 영향을 주는 변경**이 포함되어 있다면 PR 설명에 함께 적어 주세요.
7. PR을 열면 `.github/workflows/ci.yml`이 자동으로 Core API(JUnit)/AI API(pytest) 테스트와 프론트 lint를 돌립니다 - 실패하면 병합 전에 고쳐주세요. 로직을 고쳤다면(특히 버그 수정) 가능한 한 재현 테스트를 같이 추가해 주세요.

> **참고**
>
> Docker Hub 이미지는 일반 PR 머지나 `develop` 브랜치 Push만으로는 배포되지 않습니다. Maintainer가 `main` 브랜치에 `v1.0.0`과 같은 버전 태그를 Push하면 GitHub Actions가 Docker 이미지를 빌드하여 `latest`와 해당 버전 태그로 배포합니다.

## 🐞 이슈 리포트

버그/기능 제안은 [Issues](https://github.com/sinjinwoo/dowoo/issues)에 등록해주세요. 버그 리포트에는 가능하면 다음을 포함해주세요:

- 재현 방법(가능하면 어떤 소설 사이트/URL에서 발생했는지)
- 기대한 동작과 실제 동작
- Core API 로그(`docker compose logs core-api`)나 브라우저 콘솔 에러가 있다면 함께

## 📚 새 크롤링 사이트 추가하기

새 웹소설 사이트를 지원하려면:

1. `dowoo-python/app/crawl/parsers/`에 사이트 전용 파서를 추가합니다 (기존 파서들, 예: `idx.py`, `mxsw.py`를 참고).
2. `dowoo-python/app/crawl/registry.py`의 `SITE_REGISTRY`에 호스트명과 파서를 등록합니다.
3. 대상 사이트가 Cloudflare 등으로 막혀 있다면 먼저 `curl_cffi`(`impersonate="chrome"`)로 뚫리는지 확인하세요 - 헤드리스 브라우저(Playwright)는 오히려 자동화 탐지에 걸려 막히는 경우가 있었습니다 ([`docs/troubleshooting/06-playwright-blocked-by-cloudflare.md`](docs/troubleshooting/06-playwright-blocked-by-cloudflare.md) 참고).
4. 첫 화/마지막 화 대응도 반드시 구현해야 합니다. 대상 사이트는 이전/다음 링크가 없을 때 보통 실제 회차 대신 목차 페이지나 종료 페이지 등으로 대체된 링크를 내려줍니다. 이 패턴을 정규식 등으로 감지해 `prev`/`next` 값을 `None`으로 처리하세요 (기존 파서들의 `NO_PREV_CHAPTER_RE`/`NO_NEXT_CHAPTER_RE`/`NO_CHAPTER_RE` 구현을 참고).
5. **본문 텍스트의 줄 경계를 반드시 정규화하세요.** 원본 HTML에는 `\r\n` 같은 실제 개행이나 들여쓰기 공백이 섞여 있는 경우가 많은데, 이걸 그대로 두고 페이지 전체를 한 번에 `get_text()`로 뽑으면 `\r`만 남은 "유령 줄"이 생길 수 있습니다. 뷰어는 원문/번역문을 줄 단위(`\n` 기준) 인덱스로 대조하는데, JS의 `Boolean("\r")`은 `true`라 일반적인 blank-line 필터로도 안 걸러져서 원문/번역 문단이 챕터 중간부터 어긋나게 됩니다 ([`docs/troubleshooting/27-crlf-breaks-original-translation-line-alignment.md`](docs/troubleshooting/27-crlf-breaks-original-translation-line-alignment.md) 참고). `get_text()` 직후 `text.splitlines()`로 줄 경계를 통일하고 각 줄을 `strip()`한 뒤 다시 `\n`으로 join하세요(`shuba69.py`/`twkan.py`/`mxsw.py` 참고). `<p>` 요소별로 텍스트를 뽑는 방식(`idx.py` 참고)이라도 요소 내부에 개행이 섞일 수 있으니 `" ".join(text.split())`로 문단 내부 공백을 접어 "문단 하나 = 줄 하나"를 보장하세요.
6. README의 [지원 사이트](README.md#지원-사이트) 목록을 같이 업데이트해주세요.

## 🧾 트러블슈팅 문서

디버깅에 시간이 걸린 문제를 고쳤다면(원인이 바로 안 보였던 버그, 라이브러리/프레임워크의 예상 밖 동작 등), `docs/troubleshooting/NN-짧은-슬러그.md` 형식으로 문서를 하나 추가해주세요. 기존 문서들(`docs/troubleshooting/01-*.md` ~)의 증상/원인/해결/참고 구조를 따르고, `docs/troubleshooting/README.md`의 인덱스 표에도 한 줄 추가해주세요. 서로 다른 원인의 문제는 한 문서에 묶지 말고 각각 별도 파일로 분리해주세요.

## 🎨 코드 스타일

- **Core API(`dowoo-back`)**: 레이어별이 아니라 도메인별로 패키지를 나눕니다 - `auth`, `library`, `book`, `settings` 각각이 자기 안에 `entity`/`service`/`controller`/`repository`를 가집니다. 새 클래스를 추가할 때 공용 `domain`/`controller`/`service` 패키지를 새로 만들지 마세요.
- **암호화/JWT/OAuth 같은 표준적인 보안 로직**은 직접 구현하지 말고 검증된 라이브러리(Spring Security, `jjwt` 등)를 사용해주세요.
- **프론트엔드(`dowoo`)**: 컴포넌트는 `src/components/<도메인>/` 아래에, API 호출은 `src/api/`에 도메인별 파일로 분리합니다.
