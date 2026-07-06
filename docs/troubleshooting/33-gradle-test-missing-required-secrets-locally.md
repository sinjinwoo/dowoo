# 로컬에서 `./gradlew test`만 돌리면 DB_PASSWORD를 못 읽어와 컨텍스트 로딩 실패

## 증상

`DowooApplicationTests.contextLoads()`가 `docker compose`로 Postgres를 띄운 상태에서도
로컬 셸에서 `./gradlew test`를 직접 실행하면 계속 실패했다. 처음엔 "Connection refused"였다가
(Postgres 컨테이너가 안 떠 있어서), 컨테이너를 띄운 뒤에도 여전히 안 됐다.

## 원인

`application.properties`의 `spring.datasource.password=${DB_PASSWORD}`,
`app.jwt-secret=${JWT_SECRET}`, `app.internal-token=${INTERNAL_TOKEN}`,
`app.api-key-encryption-secret=${API_KEY_ENCRYPTION_SECRET}`는 의도적으로 기본값이 없다
(배포 시 실수로 빠뜨려도 공개된 기본값으로 조용히 뜨는 걸 막기 위함). `docker compose up`은
저장소 루트 `.env`를 자동으로 읽어 이 값들을 컨테이너 환경변수로 주입하지만, `./gradlew test`를
호스트 셸에서 직접 실행하면 `.env`가 전혀 로드되지 않는다. 그 결과 이 플레이스홀더들을 해석할
값이 하나도 없어 `ApplicationContext` 자체가 못 뜬다 - 도커로 띄운 core-api가 정상 기동되는
것과 대비돼서 "왜 이럴까" 헷갈리기 쉬웠다.

## 해결

`dowoo-back/build.gradle`의 `test` 태스크에 저장소 루트 `.env`를 직접 읽어 테스트 JVM
환경변수로 주입하는 코드를 추가했다.

```groovy
tasks.named('test') {
	useJUnitPlatform()

	def envFile = rootProject.file('../.env')
	if (envFile.exists()) {
		envFile.readLines().each { line ->
			line = line.trim()
			if (line && !line.startsWith('#') && line.contains('=')) {
				def (key, value) = line.split('=', 2)
				environment key.trim(), value.trim()
			}
		}
	}
}
```

`docker compose`가 쓰는 것과 완전히 같은 값을 그대로 재사용하므로 값을 따로 맞춰줄 필요가
없다. GitHub Actions(`ci.yml`)는 `.env` 파일 자체가 없고(`.gitignore`에 포함) 워크플로가
이미 job 레벨 환경변수로 이 값들을 직접 주입하므로, `envFile.exists()`가 `false`가 되어 이
블록은 조용히 스킵되고 CI 동작에는 전혀 영향을 주지 않는다.

## 참고

- "프로덕션에서 안전하려고 기본값을 없앤 필수 설정값"은 로컬 개발 편의성과 상충하기 쉽다 -
  기본값을 추가해서 편의성을 얻지 말고(그러면 프로덕션에서도 실수로 빠뜨려도 조용히 뜨는
  문제가 되살아난다), 로컬/CI 전용 경로로 값을 주입하는 쪽을 택할 것.
- CI에 영향을 주지 않는 로컬 전용 편의 기능을 추가할 때는 "그 파일/조건이 CI 환경에는
  존재하지 않는다"는 사실 자체를 안전장치로 쓸 수 있다 - 별도의 프로필 분기 없이도 파일
  존재 여부만으로 로컬/CI를 자연스럽게 구분했다.
- 관련 파일: `dowoo-back/build.gradle`, `.github/workflows/ci.yml`
