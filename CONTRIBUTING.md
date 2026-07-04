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

1. `main`에서 새 브랜치를 만듭니다.
2. 변경 사항을 커밋합니다.
3. PR을 열 때 [PR 템플릿](.github/pull_request_template.md)의 항목을 채워주세요 - 특히 "왜 이 변경이 필요한지"와 "어떤 컴포넌트가 영향받는지"는 리뷰에 꼭 필요합니다.
4. 배포 이미지에 영향을 주는 변경(Dockerfile, docker-compose, 환경변수 등)이라면 PR 설명에 그 사실을 명시해주세요.
5. `v1.0.0` 같은 버전 태그를 `main`에 push하면 GitHub Actions가 자동으로 도커허브에 이미지를 빌드/배포합니다 (`.github/workflows/docker-publish.yml`) - 일반 PR 머지만으로는 배포되지 않습니다. `latest`와 그 버전 태그 두 개가 함께 올라가며, 동작하려면 저장소 Settings → Secrets에 `DOCKERHUB_USERNAME`/`DOCKERHUB_TOKEN`이 등록되어 있어야 합니다.

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
4. README의 [지원 사이트](README.md#지원-사이트) 목록을 같이 업데이트해주세요.

## 🧾 트러블슈팅 문서

디버깅에 시간이 걸린 문제를 고쳤다면(원인이 바로 안 보였던 버그, 라이브러리/프레임워크의 예상 밖 동작 등), `docs/troubleshooting/NN-짧은-슬러그.md` 형식으로 문서를 하나 추가해주세요. 기존 문서들(`docs/troubleshooting/01-*.md` ~)의 증상/원인/해결/참고 구조를 따르고, `docs/troubleshooting/README.md`의 인덱스 표에도 한 줄 추가해주세요. 서로 다른 원인의 문제는 한 문서에 묶지 말고 각각 별도 파일로 분리해주세요.

## 🎨 코드 스타일

- **Core API(`dowoo-back`)**: 레이어별이 아니라 도메인별로 패키지를 나눕니다 - `auth`, `library`, `book`, `settings` 각각이 자기 안에 `entity`/`service`/`controller`/`repository`를 가집니다. 새 클래스를 추가할 때 공용 `domain`/`controller`/`service` 패키지를 새로 만들지 마세요.
- **암호화/JWT/OAuth 같은 표준적인 보안 로직**은 직접 구현하지 말고 검증된 라이브러리(Spring Security, `jjwt` 등)를 사용해주세요.
- **프론트엔드(`dowoo`)**: 컴포넌트는 `src/components/<도메인>/` 아래에, API 호출은 `src/api/`에 도메인별 파일로 분리합니다.
