package com.suwon.festival.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.entity.Event;

public interface EventRepository extends JpaRepository<Event, Long> {
  boolean existsByIdx(Long idx);

  boolean existsBySource(String source); // 재단 행사를 한 번도 못 가져온 DB 인지 확인

  Optional<Event> findByIdx(Long idx); // 동기화 시 이미 저장된 행사를 꺼내서 갱신할 때 사용

  Optional<Event> findBySourceAndSourceKey(String source, String sourceKey); // 인스타 행사 중복 확인

  List<Event> findByMergedIntoIdIsNull(); // 화면에 보이는 행사 (합쳐져 숨겨진 행 제외)

  List<Event> findByMergedIntoId(Long mergedIntoId); // 이 행사를 대표로 삼아 숨겨진 행 (대표를 지우기 전에 비움)

  List<Event> findByMergedIntoIdIn(Collection<Long> mergedIntoIds); // 대표 여러 건의 숨긴 행을 한 번에 (진행 날짜 합치기)

  List<Event> findByDistrictIsNullOrderByIdDesc(Pageable pageable); // 구를 아직 모르는 행사, 새 행사부터 (EventDistrictFiller)

  // 구 저장. 계산하는 동안 좌표가 바뀌었거나 이미 채워졌으면 0건 (옛 좌표로 구한 값을 덮어쓰지 않음)
  @Transactional
  @Modifying(clearAutomatically = true)
  @Query("update Event e set e.district = :district where e.id = :id and e.district is null"
      + " and e.latitude = :latitude and e.longitude = :longitude")
  int saveDistrict(@Param("id") Long id, @Param("latitude") Double latitude, @Param("longitude") Double longitude,
      @Param("district") String district);
}
