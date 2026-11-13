package com.suwon.festival.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.Landmark;

public interface LandmarkRepository extends JpaRepository<Landmark, Long> {

  // 동기화 때 이미 저장된 명소를 찾아 갱신 (지우고 다시 넣지 않음 → id 유지)
  Optional<Landmark> findBySourceAndSourceKey(String source, String sourceKey);
}
