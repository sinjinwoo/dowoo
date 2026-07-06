-- 시스템 프롬프트+번역 메모(용어집)를 소설(novels)과 분리된 독립 리소스(prompts)로 뺀다.
-- 소설은 더 이상 프롬프트를 직접 소유하지 않고 prompt_id로 참조만 한다 - NULL이면
-- "사용자의 기본 프롬프트를 쓴다"는 뜻으로 취급한다(코드 쪽 조회 로직 참고).

CREATE TABLE prompts (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title             VARCHAR(255) NOT NULL,
    system_prompt     TEXT,
    translation_note  TEXT,
    is_default        BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_prompts_user_id ON prompts (user_id);

-- 사용자당 기본 프롬프트는 정확히 하나만 허용(부분 유니크 인덱스).
CREATE UNIQUE INDEX uq_prompts_user_default ON prompts (user_id) WHERE is_default;

ALTER TABLE novels ADD COLUMN prompt_id UUID REFERENCES prompts (id) ON DELETE SET NULL;

-- 기존 사용자마다 기본 프롬프트를 하나씩 시드한다. 내용은 DefaultPrompts.SYSTEM_PROMPT의
-- 현재 텍스트를 그대로 옮긴 것 - 이후 사용자가 자유롭게 내용만 수정할 수 있다(제목은 고정).
-- 제목 '기본 프롬프트'는 DefaultPrompts.DEFAULT_PROMPT_TITLE 상수와 반드시 일치해야 한다
-- (신규 가입자는 AuthService.signup()이 그 상수로 같은 제목을 시드한다).
INSERT INTO prompts (user_id, title, system_prompt, is_default, created_at, updated_at)
SELECT id, '기본 프롬프트', $$[가장 높은 최우선 명령 (시스템 절대 규칙)]
1. 출력의 기본 언어는 '오직 한국어'이다. 본문 전체가 중국어 문장으로 출력되거나, 번역되지 않은 중국어 단어가 단독으로 행에 남는 경우 즉시 규칙 위반으로 간주한다.
2. 단, 주요 고유명사, 인명, 무공명, 중요 도가/불교 용어에 한해서만 의미 전달을 위해 한국어 뒤에 괄호를 치고 한자를 병기하는 것(예: 여래(如來), 횡련(橫練))을 허용한다.
3. 이외의 일반 서술어, 조사, 문장 구조는 절대로 중국어 원문을 그대로 복사(Pass-through)하지 말고 완전히 한국어로 번역해야 한다.
4. 인사말, 주석, 설명 없이 오직 번역된 본문만 즉시 출력하라.

[공리: 줄 바꿈 및 매핑 규칙]
- 입력과 출력의 행(Line) 개수는 정확히 1:1로 일치해야 한다.
- 빈 줄은 빈 줄로 출력하되, 원문의 줄을 합치거나 임의로 쪼개지 않는다.
- 원문에 포함된 특수문자(예: 【 】, 「 」, →) 내부의 내용도 한자 병기 대상을 제외하고는 모두 한국어로 번역되어야 한다.
- 소설 사이트 도메인 등 영문/특수기호가 뒤섞인 문자열은 기호에 현혹되지 말고, 한국어 문맥에 맞게 자연스럽게 의역하거나 처리하라.

[지침: 번역 스타일 및 고유명사]
- 중국어 고유명사는 철저하게 '한국식 한자 훈독(한국 한자음)'을 우선 적용하고, 필요한 경우에만 한자를 병기한다. (예: 孟傳 -> 맹전, 湧泉 -> 용천(湧泉))
- 번역 투와 직역을 배제하고, 한국 웹소설 서사 스타일에 맞춰 매끄럽고 풍성한 구어체/독백체로 의역한다. (의역을 통해 문장을 풍성하게 작성하여 글자 수를 확보할 것)

[용어집]
{{memo}}$$, true, now(), now()
FROM users;

-- 기존 novel_prompts 행마다 그 소설 전용 프롬프트를 만든다(제목은 소설 제목을 그대로 씀).
INSERT INTO prompts (user_id, title, system_prompt, translation_note, is_default, created_at, updated_at)
SELECT n.user_id, n.title, np.system_prompt, np.translation_note, false, np.updated_at, np.updated_at
FROM novel_prompts np
JOIN novels n ON n.id = np.novel_id;

-- 방금 만든 프롬프트를 원래 소설에 연결한다. updated_at까지 맞춰서 조인해 같은 사용자가
-- 우연히 소설 제목이 같은 다른 프롬프트를 잘못 집지 않게 한다.
-- (Postgres는 UPDATE ... FROM의 JOIN ON절 안에서 업데이트 대상 테이블(n)을 참조할 수 없어
-- - "invalid reference to FROM-clause entry" - n 관련 조건은 전부 WHERE로 뺐다.)
UPDATE novels n
SET prompt_id = p.id
FROM novel_prompts np
JOIN prompts p ON p.updated_at = np.updated_at
WHERE np.novel_id = n.id
  AND p.user_id = n.user_id
  AND p.title = n.title
  AND p.is_default = false;

DROP TABLE novel_prompts;
