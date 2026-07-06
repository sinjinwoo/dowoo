# Flyway 마이그레이션의 UPDATE ... FROM 문에서 "invalid reference to FROM-clause entry" 발생

## 증상

프롬프트를 소설과 분리된 독립 엔티티로 옮기는 `V3__prompts.sql` 마이그레이션에서, 기존
`novel_prompts` 데이터를 새 `prompts` 테이블로 옮긴 뒤 원래 소설에 다시 연결하는 `UPDATE`
문에서 core-api가 기동 즉시 죽었다.

```
ERROR: invalid reference to FROM-clause entry for table "n"
Detail: There is an entry for table "n", but it cannot be referenced from this part of the query.
```

## 원인

문제가 된 문장은 다음과 같은 형태였다.

```sql
UPDATE novels n
SET prompt_id = p.id
FROM novel_prompts np
JOIN prompts p
    ON p.user_id = n.user_id AND p.title = n.title AND p.updated_at = np.updated_at AND p.is_default = false
WHERE np.novel_id = n.id;
```

Postgres는 `UPDATE ... FROM A JOIN B ON <조건>` 형태에서, `A JOIN B`의 `ON` 절은 그 조인
결과가 업데이트 대상 테이블(`n`)과 합쳐지기 **이전에** 먼저 평가된다. 즉 `np JOIN p`라는
조인 자체가 독립적으로 먼저 계산되는데, 그 계산 도중에는 `n`이 아직 스코프에 없다. 그래서
`ON` 절 안에서 `n.user_id`/`n.title`을 참조하면 "그 테이블이 존재하긴 하지만 지금 이
부분에서는 참조할 수 없다"는 에러가 난다. `WHERE` 절은 `UPDATE` 대상 테이블과 `FROM`
목록의 모든 테이블을 동시에 볼 수 있는 시점에 평가되므로 거기서는 문제없이 `n`을 쓸 수 있다.

## 해결

`n`을 참조하는 조건을 전부 `ON`에서 `WHERE`로 옮기고, `ON`에는 `np`와 `p`끼리만 비교하는
조건만 남겼다.

```sql
UPDATE novels n
SET prompt_id = p.id
FROM novel_prompts np
JOIN prompts p ON p.updated_at = np.updated_at
WHERE np.novel_id = n.id
  AND p.user_id = n.user_id
  AND p.title = n.title
  AND p.is_default = false;
```

다행히 Postgres는 트랜잭셔널 DDL을 지원해서, 이 문장이 실패하며 마이그레이션 전체가
롤백됐고 `flyway_schema_history`에도 실패 기록이 남지 않았다 - SQL만 고치고 다시
기동하니 별도 `flyway repair`나 수동 정리 없이 V3부터 깨끗하게 재적용됐다.

## 참고

- `UPDATE ... FROM` 문에서 여러 테이블을 조인해야 한다면, 그 조인의 `ON` 절에는 조인되는
  테이블끼리의 조건만 넣고, 업데이트 대상 테이블과 관련된 조건은 전부 `WHERE`로 뺄 것.
- 트랜잭셔널 DDL을 지원하는 DB(Postgres 등)에서 마이그레이션이 중간에 실패하면 보통
  전체가 자동 롤백된다 - "실패한 마이그레이션을 고치고 다시 배포하면 되는지, 아니면 DB를
  수동으로 복구해야 하는지" 헷갈릴 때는 먼저 `flyway_schema_history`에 실패 기록이 남았는지
  확인해서 판단할 것.
- 관련 파일: `dowoo-back/src/main/resources/db/migration/V3__prompts.sql`
