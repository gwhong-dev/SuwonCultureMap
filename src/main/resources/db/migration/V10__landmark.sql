-- V10: 명소(landmark) — 행사(event)와 섞지 않는 별도 테이블 (결정 docs/decisions/2026-11-13-foundation-api-scope.md)
-- - 지금 출처: 수원문화재단 API 59_C 수원화성 시설물(SWCF_FORTRESS) · 63_C 화성행궁 시설물(SWCF_PALACE)
--   나중에 수원문화DB 문화예술공간(SWDB_SPACE) · 직접 추가(MANUAL)도 같은 테이블에 → (source, source_key) UNIQUE
-- - 재동기화 때 지우고 다시 넣지 않고 갱신 → id 가 바뀌지 않음 (② 명소 찜이 id 를 가리킴)
-- - latitude · longitude: NULL 허용 (좌표 없는 곳은 목록에만). coord_source = API(재단 좌표) / KAKAO(장소 검색 보충) / NULL
-- - non_public: 제목의 "비공개"로 판정. 빼지 않고 저장 (화면에서 "관람 불가" 강조 — 11/13 사용자 결정)
-- - history: 본문 첫머리 창건 · 복원 줄을 " · " 로 이은 한 줄 / description: 국문 본문 (HTML 제거, 영문 단락 제외)
-- - image_url: 재단 대표 사진 원본 주소만 (파일을 받아 두지 않음)
-- - last_seen_at: 마지막으로 재단 응답에 있던 시각 (응답에서 빠진 곳을 가려낼 때). updated_at: 내용이 바뀐 시각
--
-- 되돌리기(필요할 때 수동):
--   DROP TABLE landmark;
--   DELETE FROM flyway_schema_history WHERE version = '10';

CREATE TABLE landmark (
  id            BIGINT         NOT NULL AUTO_INCREMENT,
  source        VARCHAR(20)    NOT NULL,
  source_key    VARCHAR(100)   NOT NULL,
  name          VARCHAR(255)   NOT NULL,
  kind          VARCHAR(50)    NOT NULL,
  latitude      DOUBLE,
  longitude     DOUBLE,
  coord_source  VARCHAR(20),
  history       VARCHAR(500),
  non_public    BOOLEAN        NOT NULL DEFAULT FALSE,
  image_url     VARCHAR(1000),
  link          VARCHAR(1000),
  description   TEXT,
  pub_date      DATETIME(6),
  created_at    DATETIME(6)    NOT NULL,
  updated_at    DATETIME(6)    NOT NULL,
  last_seen_at  DATETIME(6)    NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_landmark_source_key UNIQUE (source, source_key)
);
