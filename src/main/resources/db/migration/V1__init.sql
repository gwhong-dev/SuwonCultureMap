-- V1: Flyway 도입 시점(2026-10-31)의 테이블 구조 = 엔티티 4개(Member · LoginMethod · Event · Favorite)
-- - 이미 테이블이 있는 DB(지금까지 ddl-auto=update 로 만든 DB)는 이 파일을 건너뜀 (baseline-on-migrate, 버전 1로 표시)
-- - 빈 DB(새 PC · Docker 새 볼륨 · 테스트 H2)에서만 실행됨
-- - MySQL 8 문법. 테스트용 H2(MODE=MySQL)에서도 돌도록 엔진 · 문자셋 옵션은 쓰지 않음 (MySQL 8 기본값 InnoDB · utf8mb4)
-- - 앞으로 구조 변경은 이 파일을 고치지 말고 V2__설명.sql 처럼 새 파일로 추가

CREATE TABLE member (
  id         BIGINT       NOT NULL AUTO_INCREMENT,
  nickname   VARCHAR(255) NOT NULL,
  created_at DATETIME(6)  NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_member_nickname UNIQUE (nickname)
);

CREATE TABLE login_method (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  provider    VARCHAR(255) NOT NULL,
  provider_id VARCHAR(255) NOT NULL,
  email       VARCHAR(255),
  password    VARCHAR(255),
  member_id   BIGINT,
  PRIMARY KEY (id),
  CONSTRAINT uk_login_method_provider UNIQUE (provider, provider_id),
  CONSTRAINT fk_login_method_member FOREIGN KEY (member_id) REFERENCES member (id)
);

CREATE TABLE event (
  id         BIGINT        NOT NULL AUTO_INCREMENT,
  idx        BIGINT        NOT NULL,
  title      VARCHAR(255)  NOT NULL,
  category   VARCHAR(255)  NOT NULL,
  latitude   DOUBLE        NOT NULL,
  longitude  DOUBLE        NOT NULL,
  image_url  VARCHAR(1000),
  info       TEXT,
  pub_date   DATETIME(6)   NOT NULL,
  start_date DATE,
  end_date   DATE,
  link       VARCHAR(1000),
  place      VARCHAR(500),
  time_text  VARCHAR(1000),
  PRIMARY KEY (id),
  CONSTRAINT uk_event_idx UNIQUE (idx)
);

CREATE TABLE favorite (
  id         BIGINT      NOT NULL AUTO_INCREMENT,
  member_id  BIGINT      NOT NULL,
  event_id   BIGINT      NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_favorite_member_event UNIQUE (member_id, event_id),
  CONSTRAINT fk_favorite_member FOREIGN KEY (member_id) REFERENCES member (id),
  CONSTRAINT fk_favorite_event FOREIGN KEY (event_id) REFERENCES event (id)
);
