-- instagram-data-delete.sql — 저장된 인스타 데이터 삭제 (⚠️ 되돌릴 수 없음, 사용자 승인 후에만 실행)
--
-- 왜: 수집을 끄거나 다시 켜기 전 옛 데이터 정리 (docs/decisions/2026-11-02-instagram-resume-official.md "켜는 순서" 2,
--      중단 시: docs/decisions/2026-11-02-instagram-scope-off.md)
--
-- 실행 전 확인:
--   1) 서버에 스위치가 반영됨 (INSTAGRAM_SYNC_ENABLED 가 true 가 아니고, 새 코드로 재시작됨). 안 그러면 다음 동기화에 다시 쌓임
--   2) 방금 만든 DB 백업이 있음
--   3) instagram-data-count.sql 로 건수를 보고 사용자가 승인함 (favorite_deleted = 함께 지워지는 회원 찜)
--
-- 운영 서버에서 실행 (저장소 폴더, 비밀번호는 컨테이너 환경 변수를 그대로 씀):
--   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T db \
--     sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -t "$MYSQL_DATABASE"' < scripts/instagram-data-delete.sql
--
-- 건드리지 않는 것: 재단(SWCF) 행사 · 회원 · 로그인, 표 구조, Flyway 기록
-- 남는 것: 매일 백업에 최대 14일, 서버 로그의 블록 처리 기록은 용량 제한으로 지워질 때까지
--
-- 한 트랜잭션: 어느 문장이든 실패하면 mysql 이 멈추고 연결이 끊겨 전부 되돌아감 (--force 를 붙이지 말 것)

START TRANSACTION;

-- 1) 재단 대표에 합쳐진 인스타 행을 찜한 경우: 회원에겐 재단 행사로 보이므로 재단 행사로 옮김
--    회원 · 재단 행사 쌍마다 1건만 옮김 (유일 제약. 재단 행사도 찜했거나 인스타 행 여러 개를 찜한 경우) → 나머지는 3번에서 지워짐
UPDATE favorite f
  JOIN (
    SELECT MIN(f2.id) AS id, r.id AS representative_id
    FROM favorite f2
    JOIN event e ON e.id = f2.event_id
    JOIN event r ON r.id = e.merged_into_id
    LEFT JOIN favorite same ON same.member_id = f2.member_id AND same.event_id = r.id
    WHERE e.source = 'INSTAGRAM' AND r.source <> 'INSTAGRAM' AND same.id IS NULL
    GROUP BY f2.member_id, r.id
  ) pick ON pick.id = f.id
  SET f.event_id = pick.representative_id;

-- 2) 인스타 대표에 합쳐진 인스타 외 행은 합침만 풂 (FK. 지금 규칙으론 재단이 항상 대표라 보통 0건)
UPDATE event e
  JOIN event r ON r.id = e.merged_into_id
  SET e.merged_into_id = NULL
  WHERE r.source = 'INSTAGRAM' AND e.source <> 'INSTAGRAM';

-- 3) 남은 인스타 행사 찜 삭제 (FK)
DELETE f FROM favorite f JOIN event e ON e.id = f.event_id WHERE e.source = 'INSTAGRAM';

-- 4) 수집 기록 삭제 (블록 → 게시물 순서, FK)
DELETE FROM instagram_block;
DELETE FROM instagram_media;

-- 5) 인스타끼리의 합침을 풀고 행사 삭제 (event_date 는 FK ON DELETE CASCADE 로 함께 삭제)
UPDATE event SET merged_into_id = NULL WHERE source = 'INSTAGRAM';
DELETE FROM event WHERE source = 'INSTAGRAM';

COMMIT;

SELECT 'event_instagram' AS item, COUNT(*) AS n FROM event WHERE source = 'INSTAGRAM'
UNION ALL SELECT 'event_foundation', COUNT(*) FROM event WHERE source = 'SWCF'
UNION ALL SELECT 'instagram_media', COUNT(*) FROM instagram_media
UNION ALL SELECT 'instagram_block', COUNT(*) FROM instagram_block;
