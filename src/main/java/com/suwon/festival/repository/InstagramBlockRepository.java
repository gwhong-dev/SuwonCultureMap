package com.suwon.festival.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;

public interface InstagramBlockRepository extends JpaRepository<InstagramBlock, Long> {

  Optional<InstagramBlock> findByMediaAndBlockKey(InstagramMedia media, String blockKey);

  List<InstagramBlock> findByMedia(InstagramMedia media);
}
