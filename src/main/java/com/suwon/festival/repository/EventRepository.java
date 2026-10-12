package com.suwon.festival.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {
  boolean existsByIdx(Long idx);

  Optional<Event> findByIdx(Long idx); // 동기화 시 이미 저장된 행사를 꺼내서 갱신할 때 사용
}
