-- V6: 행사 위치의 구 (docs/design/mobile-home-search-plan.md D1)
-- - district: 장안구 · 권선구 · 팔달구 · 영통구 / 수원 밖 = 'OUTSIDE' / NULL = 아직 모름(다음 동기화 때 계산)
-- - 값은 동기화 뒤 EventDistrictFiller 가 카카오 "좌표 → 행정구역"으로 채움. 그전에는 전부 NULL → 화면 변화 없음
--
-- 되돌리기(필요할 때 수동):
--   ALTER TABLE event DROP COLUMN district;
--   DELETE FROM flyway_schema_history WHERE version = '6';
-- 중간 실패 시: 문장이 하나라 반영 안 됨 → flyway_schema_history 에서 success = 0 인 version '6' 행을 지운 뒤 다시 실행

ALTER TABLE event ADD COLUMN district VARCHAR(20) NULL;
