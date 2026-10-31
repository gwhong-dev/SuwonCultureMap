-- V2: 인스타 연동 3단계 — 데이터 모델 B안 (설계 문서 4번)
-- - 화면에 보이는 행사는 event 한 표. 출처를 source 칸으로 구분 (SWCF = 수원문화재단, INSTAGRAM)
-- - 재단 행사는 지금처럼 idx(재단 번호)로, 인스타 행사는 (source, source_key)로 중복을 막음 → idx 는 NULL 허용
-- - 인스타 게시물 처리 상태는 instagram_media(게시물) · instagram_block(게시물 안 행사 후보) 에 따로
-- - 캡션 원문은 저장하지 않음 (설계 문서 8번 결정)
-- - 작성 원칙(설계 문서 4번): 기존 제약 이름을 직접 지정해 지우거나 바꾸지 않음 → 칸 단위 변경만, 새 제약만 이름 붙임
--
-- 되돌리기(필요할 때 수동, 인스타 행사가 아직 없을 때만). 순서 중요:
--   DROP TABLE instagram_block; DROP TABLE instagram_media;
--   ALTER TABLE event DROP INDEX uk_event_source_key;   -- 먼저 지움 (칸부터 지우면 MySQL 이 source 하나짜리 unique 로 남겨 실패)
--   ALTER TABLE event DROP COLUMN source_key, DROP COLUMN source;
--   ALTER TABLE event MODIFY COLUMN idx BIGINT NOT NULL;
--   DELETE FROM flyway_schema_history WHERE version = '2';
-- 중간 실패 시: MySQL 의 DDL 은 하나씩 바로 반영됨 → 이미 적용된 문장만 위 순서로 되돌리고,
--   flyway_schema_history 에서 success = 0 인 version '2' 행을 지운 뒤 다시 실행

ALTER TABLE event MODIFY COLUMN idx BIGINT NULL;
ALTER TABLE event ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'SWCF';
ALTER TABLE event ADD COLUMN source_key VARCHAR(100);
ALTER TABLE event ADD CONSTRAINT uk_event_source_key UNIQUE (source, source_key);

CREATE TABLE instagram_media (
  id           BIGINT        NOT NULL AUTO_INCREMENT,
  media_id     VARCHAR(64)   NOT NULL,
  account      VARCHAR(30)   NOT NULL,
  permalink    VARCHAR(1000),
  posted_at    DATETIME(6),
  status       VARCHAR(20)   NOT NULL,
  reason       VARCHAR(255),
  collected_at DATETIME(6)   NOT NULL,
  processed_at DATETIME(6),
  PRIMARY KEY (id),
  CONSTRAINT uk_instagram_media_media_id UNIQUE (media_id)
);

CREATE TABLE instagram_block (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  media_id   BIGINT       NOT NULL,
  block_key  VARCHAR(100) NOT NULL,
  status     VARCHAR(20)  NOT NULL,
  reason     VARCHAR(255),
  event_id   BIGINT,
  updated_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_instagram_block_media_key UNIQUE (media_id, block_key),
  CONSTRAINT fk_instagram_block_media FOREIGN KEY (media_id) REFERENCES instagram_media (id),
  CONSTRAINT fk_instagram_block_event FOREIGN KEY (event_id) REFERENCES event (id)
);
