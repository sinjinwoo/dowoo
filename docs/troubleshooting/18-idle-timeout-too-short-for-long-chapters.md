# 본문이 매우 길면 번역이 지연 에러(TRANSLATE_TIMEOUT)로 끊김

## 증상

원문이 짧은 챕터는 번역이 잘 되는데, 본문이 아주 긴 챕터는 번역이 진행되다가 중간에 에러로 끊겼다. 에러 메시지는 "번역 응답이 지연되어 중단되었습니다"(`TRANSLATE_TIMEOUT`)였다.

## 원인

[17번 문서](17-sse-close-exception-after-successful-done.md) 작업과 같은 시점에 추가한 유휴(idle) 타임아웃 워치독(`TranslateService.relaySse`)의 기본값이 90초였다. 이 워치독은 "AI API로부터 마지막으로 SSE 이벤트를 받은 시각"을 기준으로 동작하는데, Gemini는 입력이 길수록(그리고 `thinkingBudget`이 설정돼 있으면 더욱) **첫 번째 줄을 스트리밍하기 전까지 아무 데이터도 보내지 않고 "생각"만 하는 구간**이 길어진다. AI API(`gemini_client.py`)는 이 구간 동안 별도의 하트비트/keep-alive 이벤트를 보내지 않으므로, 본문이 충분히 길면 이 무응답 구간이 90초를 넘겨 워치독이 "스트림이 죽었다"고 오판하고 강제로 끊어버렸다.

즉 타임아웃 자체는 의도한 대로 동작한 것이지만, 기본값이 실제 서비스에서 만날 수 있는 긴 챕터의 정상적인 지연 폭보다 짧았다.

## 해결

`app.translate-idle-timeout-seconds`(env `TRANSLATE_IDLE_TIMEOUT_SECONDS`)의 기본값을 90초에서 **300초(5분)**로 올렸다(`application.properties`, `docker-compose.yml`, `docs/api-spec.md`).

**추가 변경(사용자 배포 설정 단순화 작업 중)**: 이후 내부 튜닝값을 환경변수로 노출하지 않기로 하면서, 이 값은 `app.translate-idle-timeout-seconds` 프로퍼티/env var가 아니라 `TranslateService.IDLE_TIMEOUT` 상수(300초)로 코드에 고정됐다. 더 늘려야 하면 이제 소스를 고쳐서 재빌드해야 한다.

## 참고

- 유휴 타임아웃 값을 정할 때는 "정상적인 최악의 케이스"가 어느 정도인지(여기서는 가장 긴 챕터 + `thinkingBudget` 설정 시 Gemini의 최대 응답 지연)를 먼저 가늠해보고, 그보다 확실히 여유 있게 잡아야 한다. 처음엔 "적당히 안전해 보이는" 값(90초)으로 시작했다가 실제 사용 패턴(매우 긴 챕터)에서 너무 짧다는 게 드러난 경우다.
- 근본적으로 더 견고한 해결책은 AI API가 Gemini 응답을 기다리는 동안 주기적으로 하트비트 이벤트를 보내 Core API의 워치독이 "느리지만 살아있음"을 구분할 수 있게 하는 것이다 - 지금은 타임아웃 값을 넉넉하게 잡아 우회했지만, 그래도 부족한 사례가 생기면 이 방향을 고려한다.
- 관련 파일: `dowoo-back/src/main/resources/application.properties`, `docker-compose.yml`, `docs/api-spec.md`, `dowoo-python/app/translate/gemini_client.py`
