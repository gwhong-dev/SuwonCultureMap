-- V5: 실제 진행 날짜 (docs/event-schedule.md 5번)
-- - event_date: 진행하는 날 하나당 한 줄. 행사가 지워지면 같이 지워짐
-- - schedule_status: DATES(날짜 표가 정답) / UNKNOWN(기간만 믿을 수 있음). 행이 없을 때 "못 읽음"을 "안 함"으로 오해하지 않기 위한 칸
-- - schedule_text: 화면에 보일 원문 한 줄
-- - 값은 다음 재단 동기화 때 채워짐. 그전에는 전부 UNKNOWN → 화면 변화 없음
--
-- 되돌리기(필요할 때 수동):
--   DROP TABLE event_date;
--   ALTER TABLE event DROP COLUMN schedule_status, DROP COLUMN schedule_text;
--   DELETE FROM flyway_schema_history WHERE version = '5';
-- 중간 실패 시: MySQL 의 DDL 은 하나씩 바로 반영됨 → 이미 적용된 문장만 위 순서로 되돌리고,
--   flyway_schema_history 에서 success = 0 인 version '5' 행을 지운 뒤 다시 실행

CREATE TABLE event_date (
  event_id BIGINT NOT NULL,
  run_date DATE NOT NULL,
  PRIMARY KEY (event_id, run_date),
  CONSTRAINT fk_event_date_event FOREIGN KEY (event_id) REFERENCES event (id) ON DELETE CASCADE
);
CREATE INDEX idx_event_date_run_date ON event_date (run_date);
ALTER TABLE event ADD COLUMN schedule_status VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN';
ALTER TABLE event ADD COLUMN schedule_text VARCHAR(1000) NULL;
