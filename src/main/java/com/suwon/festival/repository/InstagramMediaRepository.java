package com.suwon.festival.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.InstagramMedia;

public interface InstagramMediaRepository extends JpaRepository<InstagramMedia, Long> {

  Optional<InstagramMedia> findByMediaId(String mediaId); // 이미 수집한 게시물인지 확인
}
