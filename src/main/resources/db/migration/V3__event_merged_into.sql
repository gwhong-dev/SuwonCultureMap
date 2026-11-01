-- V3: 같은 행사 합치기 — 숨김 칸 (docs/event-merge.md 4번)
-- - 대표 행은 NULL, 합쳐져 숨겨진 행은 대표의 id. 원본 행을 지우지 않아 다시 계산할 수 있고 찜 연결도 유지됨
-- - 값을 채우는 코드는 다음 PR(EventMergeService). 이번 버전에서는 전부 NULL → 화면 변화 없음
--
-- 되돌리기(필요할 때 수동). 순서 중요:
--   ALTER TABLE event DROP FOREIGN KEY fk_event_merged_into;  -- 먼저 지움 (MySQL 이 FK 용으로 만든 인덱스는 칸과 함께 사라짐)
--   ALTER TABLE event DROP COLUMN merged_into_id;
--   DELETE FROM flyway_schema_history WHERE version = '3';
-- 중간 실패 시: MySQL 의 DDL 은 하나씩 바로 반영됨 → 이미 적용된 문장만 위 순서로 되돌리고,
--   flyway_schema_history 에서 success = 0 인 version '3' 행을 지운 뒤 다시 실행

ALTER TABLE event ADD COLUMN merged_into_id BIGINT NULL;
ALTER TABLE event ADD CONSTRAINT fk_event_merged_into FOREIGN KEY (merged_into_id) REFERENCES event (id);
