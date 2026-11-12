-- V8: 동기화 이력 — 재단 · 인스타 동기화를 한 번 돌 때마다 한 줄 (화면의 "데이터 갱신 시각"용)
-- - source: SWCF(수원문화재단) / INSTAGRAM, status: SUCCESS / FAIL (둘 다 enum 이름 문자열)
-- - started_at · finished_at: 한국 시간 값 (SyncLogService 가 Asia/Seoul 시계로 기록)
-- - fetched · created · updated_count: 받은 개수 · 새로 저장 · 갱신. 못 세면 NULL
-- - error_message: 예외 종류 · 실패 개수만 (예외 메시지 · 호출 주소 · 키는 넣지 않음), 최대 500자
-- - GET /api/sync-status 가 재단 마지막 SUCCESS 를 찾으므로 (source, status, finished_at) 인덱스
--
-- 되돌리기(필요할 때 수동):
--   DROP TABLE sync_log;
--   DELETE FROM flyway_schema_history WHERE version = '8';

CREATE TABLE sync_log (
  id             BIGINT        NOT NULL AUTO_INCREMENT,
  source         VARCHAR(20)   NOT NULL,
  started_at     DATETIME(6)   NOT NULL,
  finished_at    DATETIME(6)   NOT NULL,
  status         VARCHAR(20)   NOT NULL,
  fetched_count  INT,
  created_count  INT,
  updated_count  INT,
  error_message  VARCHAR(500),
  PRIMARY KEY (id)
);
CREATE INDEX idx_sync_log_source_status_finished ON sync_log (source, status, finished_at);
