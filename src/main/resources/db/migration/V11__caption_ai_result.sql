-- V11: 캡션 AI 결과 보관 (결정 docs/decisions/2026-11-24-caption-ai-gemini.md)
-- - 동기화는 하루 2번 최근 게시물을 다시 읽음 → 같은 블록에 AI 를 매번 부르지 않도록 결과를 블록 원문 해시로 보관
-- - 원문은 저장하지 않음 (설계 원칙: docs/instagram-integration.md 2번). text_hash = SHA-256 앞 32글자
-- - outcome: EVENT(행사로 읽음) / NOT_EVENT(행사 아님) / UNREADABLE(못 읽음). 호출 실패는 저장하지 않음 (다음 실행에서 다시)
-- - 읽은 값은 꺼내 쓸 때 ParsedEventValidator 가 다시 검사 (AI 결과를 그대로 믿지 않음)
--
-- 되돌리기(필요할 때 수동):
--   DROP TABLE caption_ai_result;
--   DELETE FROM flyway_schema_history WHERE version = '11';

CREATE TABLE caption_ai_result (
  text_hash   VARCHAR(32)    NOT NULL,
  outcome     VARCHAR(12)    NOT NULL,
  title       VARCHAR(255),
  category    VARCHAR(50),
  start_date  DATE,
  end_date    DATE,
  time_text   VARCHAR(1000),
  place_name  VARCHAR(500),
  model       VARCHAR(60)    NOT NULL,
  created_at  DATETIME(6)    NOT NULL,
  PRIMARY KEY (text_hash)
);
