package com.suwon.festival.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.EventDate;

public interface EventDateRepository extends JpaRepository<EventDate, EventDate.Key> {

  List<EventDate> findByEventId(Long eventId);

  List<EventDate> findByEventIdInOrderByRunDate(Collection<Long> eventIds); // 목록 조회가 행사마다 쿼리하지 않게 한 번에 읽음
}
