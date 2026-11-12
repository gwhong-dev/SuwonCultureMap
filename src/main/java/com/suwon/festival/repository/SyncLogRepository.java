package com.suwon.festival.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.suwon.festival.entity.SyncLog;

public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

  // 출처별 마지막 성공 (끝난 시각이 같으면 나중에 저장한 것)
  Optional<SyncLog> findFirstBySourceAndStatusOrderByFinishedAtDescIdDesc(SyncLog.Source source, SyncLog.Status status);

  // 연속 실패 세기: 마지막 성공(id) 뒤에 저장된 실패 / 성공이 한 번도 없을 때의 실패 전체
  long countBySourceAndStatusAndIdGreaterThan(SyncLog.Source source, SyncLog.Status status, Long id);

  long countBySourceAndStatus(SyncLog.Source source, SyncLog.Status status);
}
