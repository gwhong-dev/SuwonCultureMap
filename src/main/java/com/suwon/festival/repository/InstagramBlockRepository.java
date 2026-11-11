package com.suwon.festival.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;

public interface InstagramBlockRepository extends JpaRepository<InstagramBlock, Long> {

  Optional<InstagramBlock> findByMediaAndBlockKey(InstagramMedia media, String blockKey);

  List<InstagramBlock> findByMedia(InstagramMedia media);

  List<InstagramBlock> findByEventIdIn(java.util.Collection<Long> eventIds); // 지울 행사에 연결된 블록

  // 행사 id → 게시한 계정 이름 (상세 화면 출처 표시용). [행사 id, 계정] 쌍
  @Query("select b.event.id, b.media.account from InstagramBlock b where b.event.id in :eventIds")
  List<Object[]> findAccountsByEventIds(@Param("eventIds") List<Long> eventIds);
}
