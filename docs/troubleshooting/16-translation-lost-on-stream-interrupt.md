# 번역 중지 후 이전편→다음편으로 돌아오면 진행 상황이 사라지고 재번역됨

## 증상

번역이 진행되는 도중 "중지"를 누른 뒤 이전편으로 갔다가 다시 원래 챕터(다음편)로 돌아오면, 중단하기 직전까지 화면에 표시되던 번역 내용이 그대로 보이지 않고 처음부터 재번역이 시작됐다.

## 원인

`TranslateService.relaySse()`가 챕터의 `translated_text`를 DB에 저장하는 시점이 **오직 `done` 이벤트를 받았을 때 뿐**이었다.

```java
// 수정 전
if ("done".equals(eventName)) {
    persistTranslation(novelId, chapterId, dataJson);
    emitter.complete();
    return;
}
if ("error".equals(eventName)) {
    emitter.complete();  // 여기선 아무것도 저장 안 하고 그냥 끝냄
    return;
}
```

사용자가 "중지"를 누르면 프론트가 `AbortController`로 연결을 끊는데, 이 경우 `done` 이벤트가 도착하기 전에 스트림이 끝나버리므로 그때까지 번역된 내용은 어디에도 저장되지 않고 유실됐다. 챕터의 `translated_text`가 여전히 빈 문자열이니, 그 챕터로 다시 돌아오면 "번역 결과가 없는 챕터"로 취급되어 자동 번역이 다시 시작된 것이다.

## 해결

SSE로 릴레이되는 `line` 이벤트를 인덱스별로 계속 누적해두고, 스트림이 어떤 이유로 끝나든(`done`, `error`, idle 타임아웃, 클라이언트 연결 끊김) 그 시점까지 누적된 내용을 저장하도록 고쳤다.

```java
List<String> translatedLines = new ArrayList<>();
// ... 루프 안에서:
if ("line".equals(eventName)) {
    collectLine(translatedLines, dataJson);   // index별로 누적
} else if ("done".equals(eventName)) {
    persistTranslation(novelId, chapterId, dataJson);   // AI API가 조립해준 최종본 사용
    ...
} else if ("error".equals(eventName)) {
    persistPartialTranslation(novelId, chapterId, translatedLines);   // 그때까지 누적분 저장
    ...
}
// catch (IOException e) { ... } 에서도, finally 이후 정상 종료 시에도 persistPartialTranslation 호출
```

## 참고

- SSE/스트리밍 응답을 다루는 서버 코드는 "정상 종료(done)" 경로만 저장 로직을 넣기 쉬운데, 클라이언트가 언제든 연결을 끊을 수 있다는 걸 전제로 **모든 종료 경로(정상/에러/타임아웃/연결 끊김)에서 그때까지의 부분 결과를 어떻게 할지**를 같이 설계해야 한다.
- 이건 "완전한 실시간 저장"은 아니고 "스트림이 끊길 때 그 시점까지 저장"이다 - 서버가 중간에 크래시하는 경우까지는 못 막는다. 진짜 실시간 저장이 필요하면 줄마다(또는 N초마다) DB에 쓰는 방식으로 바꿔야 하며, 그만큼 쓰기 부하가 늘어나는 트레이드오프가 있다.
- 관련 파일: `dowoo-back/src/main/java/io/dedyn/jwlabs/dowoo/book/service/TranslateService.java`
