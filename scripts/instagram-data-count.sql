-- instagram-data-count.sql — 저장된 인스타 데이터 건수 확인 (읽기만 함, 삭제 전 · 후 비교용)
--
-- 운영 서버에서 실행 (저장소 폴더, 비밀번호는 컨테이너 환경 변수를 그대로 씀):
--   docker compose -f docker-compose.yml -f docker-compose.prod.yml exec -T db \
--     sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -u root -t "$MYSQL_DATABASE"' < scripts/instagram-data-count.sql

SELECT 'event_instagram' AS item, COUNT(*) AS n FROM event WHERE source = 'INSTAGRAM'
UNION ALL SELECT 'event_foundation', COUNT(*) FROM event WHERE source = 'SWCF'
UNION ALL SELECT 'event_date_instagram', COUNT(*)
  FROM event_date d JOIN event e ON e.id = d.event_id WHERE e.source = 'INSTAGRAM'
UNION ALL SELECT 'instagram_media', COUNT(*) FROM instagram_media
UNION ALL SELECT 'instagram_block', COUNT(*) FROM instagram_block
-- 재단 대표로 옮겨 유지되는 찜 (삭제 스크립트가 회원 · 재단 행사 쌍마다 1건의 event_id 만 바꿈)
UNION ALL SELECT 'favorite_moved_to_foundation', COUNT(DISTINCT f.member_id, r.id)
  FROM favorite f JOIN event e ON e.id = f.event_id JOIN event r ON r.id = e.merged_into_id
  LEFT JOIN favorite same ON same.member_id = f.member_id AND same.event_id = r.id
  WHERE e.source = 'INSTAGRAM' AND r.source <> 'INSTAGRAM' AND same.id IS NULL
-- 함께 삭제되는 찜 행 (인스타 행사를 가리키는 찜 전체 - 옮기는 찜)
UNION ALL SELECT 'favorite_deleted',
  (SELECT COUNT(*) FROM favorite f JOIN event e ON e.id = f.event_id WHERE e.source = 'INSTAGRAM')
  - (SELECT COUNT(DISTINCT f.member_id, r.id)
     FROM favorite f JOIN event e ON e.id = f.event_id JOIN event r ON r.id = e.merged_into_id
     LEFT JOIN favorite same ON same.member_id = f.member_id AND same.event_id = r.id
     WHERE e.source = 'INSTAGRAM' AND r.source <> 'INSTAGRAM' AND same.id IS NULL)
-- 인스타 대표에 합쳐진 인스타 외 행 (삭제 스크립트가 합침만 풀어 다시 보이게 함)
UNION ALL SELECT 'other_merged_into_instagram', COUNT(*)
  FROM event e JOIN event r ON r.id = e.merged_into_id
  WHERE r.source = 'INSTAGRAM' AND e.source <> 'INSTAGRAM';
