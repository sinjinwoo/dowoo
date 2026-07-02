사용자가 직접 구글 제미나이 API 키와 타겟 사이트를 입력시 AI 번역을 제공하는 사이트

로컬 스토리지와 브라우저의 INDEXEDDB를 사용하여 자료를 저장함.

참고 사이트 https://syosetu.colomo.dev/를 디벨롭해서 개발할거임.


① API 설정 및 관리

사용자 API 키 등록: Google AI Studio (Gemini) 또는 Vertex AI 키를 입력받아 브라우저에 저장.

API 키 로테이션: 여러 개의 API 키를 줄 단위로 입력하면 번역 한도 분산을 위해 키를 자동으로 순환(Rotation)하며 사용.

모델 상세 설정: 번역 모델(Flash, Pro 등) 선택 기능 (구글 지침 참고)

② 크롤링 및 웹 소설 전용 뷰어

URL/텍스트 통합 입력창: 소설 사이트 주소나 직접 복사한 텍스트를 입력받음.(참고 url=https://www.69shuba.com/txt/88724/39943182 and  https://ixdzs8.com/read/562760/p624.html , https://twkan.com/txt/92130/56698741, https://m.xsw.tw/1758800/255482697.html 모두 대응 되어야함.)

CORS 우회 및 웹 파싱: 중간 서버 또는 프론트엔드 우회 방식으로 소설 본문을 긁어와 광고를 제거하고 가독성 높은 뷰어로 가공.

이전 편 / 다음 편 이동: 웹 소설 주소의 규칙성을 파악해 다음 회차 버튼 클릭 시 연속 번역 및 로딩.

③ 커스텀 프롬프트 및 번역 필터 (핵심)

시스템 프롬프트 커스텀: 번역 품질을 높이기 위한 AI 지시문 수정 기능.

번역 노트 (Glossary): 고유명사, 등장인물 이름, 말투 지정을 프롬프트에 동적으로 주입 (주인공A/철수 형식으로 입력 시 치환 번역).

④ 독자 편의성 테마 설정 (가독성 제어)

텍스트 커스텀: 폰트 지정(URL 연동), 글자 색상, 배경 색상, 글자 크기/두께, 줄 간격, 들여쓰기 조절.

원문/번역 대조 뷰: 한글 번역본 아래에 일본어/중국어 원문을 반투명하게 겹쳐보거나 대조하는 기능(원문 클릭시 노출).

페이지 뷰 모드: 스크롤 방식이 아닌 책처럼 좌우/상하 터치로 페이지를 넘기는 모드.

⑤ 데이터 저장 및 내보내기

자동 이어하기 및 캐싱: 번역된 데이터를 저장해 두어 새로고침 시 초기화 방지.

번역본 다운로드: 번역 완료된 소설을 txt 파일로 통합하여 추출(해당 소설만)

💡 [IndexedDB 활용] 로컬 서재 및 즐겨찾기 (라이브러리) 기능

기존 사이트는 주소 기반 히스토리만 지원하지만, 내 프로젝트에는 "내 서재" 기능을 추가합니다.

사용자가 읽던 소설의 제목, 최근 읽은 챕터 위치, 번역된 본문 전체를 IndexedDB에 통째로 보관하여 오프라인 상태에서도 언제든 다시 열어볼 수 있고 다운로드 받을수 있게 합니다.(사용자가 제목,표지 등 수정가능 미설정시 자동으로 제목감지하여 원문으로 저장)

💡 [IndexedDB 활용] 작품별 독자 번역 노트 관리

소설마다 등장인물과 고유명사가 다릅니다. 기존 사이트는 번역 노트를 매번 수동으로 바꿔줘야 하지만, 작품별로 번역 노트를 IndexedDB에 각각 따로 저장하여 해당 소설을 열 때마다 자동으로 고유 명사 사전이 로딩되어 번역프롬프트에 포함되게 만듭니다.

💡 테마 프리셋 제공 및 다크 모드 원클릭 전환

기존 사이트는 RGB 값을 사용자가 직접 입력해야 해서 불편합니다. 다크 모드, 미디엄 그린(눈 편한 테마), 오렌지 페이퍼 등 원클릭 테마 프리셋 버튼을 추가합니다.

번역 진행시 번역 진행도 하단 원형 로딩창


🛠️ 개발 아키텍처 매핑 팁
SessionStorage: 현재 로그인 세션, 사용자가 방금 입력한 일회성 설정, 임시 테마 상태 정보 유지.

IndexedDB (Dexie.js): 사용자 API 키 목록, 작품별 번역 사전(노트), 소설 본문 캐시 데이터, '내 서재' 북마크 데이터 저장.


참고 사이트 로컬 스토리지
google_adsense_settings	{"ca-pub-1724811766339171":["ca-pub-1724811766339171"]}
history	[{"key":"ㅇ","modelId":"gemini-3-flash-preview","time":495222},]
lastUrl	https://ixdzs8.com/read/562760/p623.html
settings	{"ai":{"apiKeyss":"ㅇ","model":null,"prompt":null,"note":"인물이름은 반드시 한글 훈독으로 해석할것.\n단위는 si단위계를 사용할것. \n1리 = 500m\n1근 = 500g","replacer":null,"temperature":null,"topP":null,"tryProModel":false,"thinkingBudget":null,"onlyBestModel":false,"usePaidModel":false,"skipSaveSettings":false,"allowJS":false,"memo":null},"styles":{"fontFamily":"","fontColor":"","bgColor":"","fontSize":"24","fontWeight":"","lineHeight":"","sentenceHeight":"","textIndent":"","originalOpacity":"","quoteStyle":"","spacingX":"","quoteBreak":false,"noEmptyLine":false,"pagination":false,"paginationY":false,"noScroll":false,"noHeadings":false,"keepAdditionalHan":false,"paragraphBreak":false,"sentenceBreak":false,"noHtml":false,"originalFirst":false,"quoteWhiteSpace":false,"googleTranslation":false,"googleTransliteration":false,"ignoreOriginalNewline":false,"breakAll":false,"navPb":false,"checkRetranslate":false}}
version	36

참고 사이트 로컬 스토리지
episode
key: https://ixdzs8.com/read/562760/p601.html"	
value: {prev: 'https://ixdzs8.com/read/562760/p603.html', next: 'https://ixdzs8.com/read/562760/p605.html'}

original
key: "https://ixdzs8.com/read/562760/p602.html"
value : 중국어 원문

translation
key: "https://ixdzs8.com/read/562760/p602.html"
value: 번역결과

모바일 환경 최적화 필수

상단 메뉴바를 통해 서재, 용어집, 소설보기 설정등 접근 가능

테스트는 내가 할게 너는 내가 뭘해야하는지만 말해줘.

비동기로 ai 로딩

로딩 완료된 txt는 줄단위로 화면에 표시