-- OAuth2 로그인(Phase 6)이 붙기 전까지 모든 API가 사용하는 고정 로컬 사용자.
INSERT INTO users (id, email, oauth_provider, oauth_id)
VALUES ('00000000-0000-0000-0000-000000000001', 'local@dowoo.local', 'local', 'local');
