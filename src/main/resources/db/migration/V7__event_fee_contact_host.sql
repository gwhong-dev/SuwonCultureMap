-- V7: 상세 정보 분해 — 요금 · 문의처 · 주최 칸 (원문 info 의 "항목: 내용" 줄에서 꺼내 따로 저장)
-- - 재단 XML 의 (항목, 내용) 쌍에서 바로 꺼냄 (InfoParser.findFee · findContact · findHost). 없으면 NULL
-- - 기존 행은 다음 동기화(06 · 18시) 때 채워짐 (EventSyncService 의 detailFixed). 그전엔 화면이 원문에서 찾아 씀
--
-- 되돌리기(필요할 때 수동):
--   ALTER TABLE event DROP COLUMN fee, DROP COLUMN contact, DROP COLUMN host;
--   DELETE FROM flyway_schema_history WHERE version = '7';
-- 중간 실패 시: MySQL 은 ALTER 가 문장 단위로 반영됨 → 이미 생긴 칸을 확인해 지운 뒤 flyway_schema_history 의 version '7' 실패 행을 지우고 다시 실행

ALTER TABLE event ADD COLUMN fee VARCHAR(500) NULL;
ALTER TABLE event ADD COLUMN contact VARCHAR(200) NULL;
ALTER TABLE event ADD COLUMN host VARCHAR(500) NULL;
