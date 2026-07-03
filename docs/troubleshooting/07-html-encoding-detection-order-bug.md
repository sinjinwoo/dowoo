# gb18030을 먼저 시도하면 UTF-8 사이트까지 깨진 텍스트(mojibake)로 디코딩됨

## 증상

`curl_cffi`로 전환한 뒤([[06-playwright-blocked-by-cloudflare.md]] 참고) 4개 사이트를 다시 테스트하니 `69shuba.com`은 정상인데 `m.xsw.tw`, `twkan.com`의 결과가 다음처럼 깨져서 나왔다.

```json
{"title": "绗666绔 鎴戦欎竴娆′笉鏈冨啀閷亷濂癸紒瑾埌鍋氬埌", ...}
```

## 원인

인코딩 자동 감지 로직이 다음과 같았다.

```python
ENCODING_CANDIDATES = ("gb18030", "gbk", "utf-8")

def _decode_html(response) -> str:
    for encoding in ENCODING_CANDIDATES:
        try:
            return response.content.decode(encoding, errors="ignore")
        except (UnicodeDecodeError, LookupError):
            continue
    return response.text
```

여기에 두 가지 문제가 겹쳐 있었다.

1. `errors="ignore"`를 쓰면 디코딩이 **절대 예외를 던지지 않는다** — 잘못된 바이트를 그냥 버리고 넘어가 버리므로, `try` 블록이 항상 첫 번째 후보(`gb18030`)에서 "성공"해버려 루프가 사실상 의미가 없었다.
2. `gb18030`은 GBK의 상위 호환 인코딩이라 **거의 모든 바이트 시퀀스를 에러 없이 디코딩**할 수 있다. 즉 실제로는 UTF-8인 콘텐츠를 gb18030으로 읽어도 "성공"하지만 결과는 완전히 다른(엉뚱한) 문자들로 나온다 — 이게 바로 mojibake다.

## 해결

우선순위와 에러 모드를 모두 바꿨다: **UTF-8을 strict(엄격) 모드로 가장 먼저 시도**하고, 그게 실패했을 때만(=진짜 GBK 계열 사이트일 때만) gb18030/gbk로 넘어가도록 순서를 뒤집었다. 최후의 수단으로만 `errors="ignore"`를 사용한다.

```python
ENCODING_CANDIDATES = ("utf-8", "gb18030", "gbk")

def _decode_html(response) -> str:
    for encoding in ENCODING_CANDIDATES:
        try:
            return response.content.decode(encoding, errors="strict")
        except (UnicodeDecodeError, LookupError):
            continue
    return response.content.decode("utf-8", errors="ignore")
```

UTF-8이 아닌 바이트 시퀀스는 strict UTF-8 디코딩에서 거의 항상 `UnicodeDecodeError`를 던지므로, 진짜 UTF-8 사이트는 1번 후보에서 정확히 성공하고, 진짜 GBK 계열 사이트만 2~3번 후보로 폴백된다.

## 참고

- 여러 인코딩을 순서대로 "시도해보고 성공하면 채택" 하는 패턴을 쓸 때는 반드시 `errors="strict"`(기본값)로 시도해야 한다. `errors="ignore"`/`"replace"`는 사실상 항상 "성공"하기 때문에 후보 목록의 첫 번째 것으로 고정되어 버려 인코딩 감지 로직 자체가 무력화된다.
- GBK/GB18030처럼 "거의 모든 바이트를 받아들이는" 인코딩은 후보 목록에서 **최대한 뒤에** 두는 것이 안전하다 — 그래야 진짜로 그 인코딩이 필요한 경우에만 도달한다.
- 관련 파일: `dowoo-python/app/crawl/fetcher.py`
