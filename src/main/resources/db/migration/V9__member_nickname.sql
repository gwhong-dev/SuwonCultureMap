-- V9: 회원 닉네임 — 가입할 때 랜덤 조합(형용사 + 수원 랜드마크)으로 붙이고, 마이페이지에서 고칠 수 있음
-- - 실명 · 이메일이 아니라 서비스가 만든 이름이라 개인정보 미보관 방침(V4)과 맞음
-- - 기존 회원은 NULL 로 두고, 다음 로그인 확인(/api/auth/me) 때 서버가 채움
-- - 겹치면 안 됨 → UNIQUE (MySQL · H2 모두 NULL 여러 개 허용)
--
-- 되돌리기(필요할 때 수동):
--   ALTER TABLE member DROP INDEX uk_member_nickname;
--   ALTER TABLE member DROP COLUMN nickname;
--   DELETE FROM flyway_schema_history WHERE version = '9';

ALTER TABLE member ADD COLUMN nickname VARCHAR(40) NULL;
CREATE UNIQUE INDEX uk_member_nickname ON member (nickname);
