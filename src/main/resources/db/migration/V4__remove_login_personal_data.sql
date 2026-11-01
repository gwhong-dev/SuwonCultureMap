-- V4: 로그인은 소셜에게 맡기고 서비스는 로그인에 필요한 최소값만 보관 (2026-11-01 결정)
-- - 지우는 칸: 이메일 · 비밀번호(항상 비어 있었음) · 닉네임(화면에서 안 씀)
-- - 지우는 행: 로그인 수단도 찜도 없는 회원 (예전 공개 가입 API 로 생긴 행. 로그인 · 탈퇴를 할 수 없음)
-- - 남는 칸: provider + provider_id. 같은 소셜 계정을 알아보는 데 꼭 필요함 (찜 · 탈퇴 · 카카오 연결 해제)
--
-- 되돌릴 수 없음: 지운 값은 복구되지 않음. 칸만 되살리려면(수동, 값은 비어 있음):
--   ALTER TABLE login_method ADD COLUMN email VARCHAR(255), ADD COLUMN password VARCHAR(255);
--   ALTER TABLE member ADD COLUMN nickname VARCHAR(255);  -- 코드도 되돌려야 하고 NOT NULL · UNIQUE 는 값을 채운 뒤에
--   DELETE FROM flyway_schema_history WHERE version = '4';
-- 닉네임의 UNIQUE 는 칸과 함께 사라짐 (제약 이름은 DB 마다 달라 지정하지 않음)
-- 중간 실패 시: MySQL 의 DDL 은 하나씩 바로 반영됨 → 실패한 문장부터 수동으로 실행하고,
--   flyway_schema_history 에서 success = 0 인 version '4' 행을 지운 뒤 다시 실행. 값이 지워지므로 운영 DB 는 실행 전에 백업

DELETE FROM member
WHERE id NOT IN (SELECT member_id FROM login_method WHERE member_id IS NOT NULL)
  AND id NOT IN (SELECT member_id FROM favorite);
ALTER TABLE login_method DROP COLUMN email;
ALTER TABLE login_method DROP COLUMN password;
ALTER TABLE member DROP COLUMN nickname;
