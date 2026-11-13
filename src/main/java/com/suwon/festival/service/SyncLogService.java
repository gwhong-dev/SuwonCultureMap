package com.suwon.festival.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.suwon.festival.dto.SyncStatusResponse;
import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.SyncLogRepository;

// 동기화 이력(sync_log) 기록 · 마지막 성공 조회
//  - 기록 실패는 로그만 남기고 삼킴 → 이력 저장이 동기화 본 동작을 깨지 않음
//  - 시각은 서버 시간대와 상관없이 한국 시간 값 (운영 컨테이너가 UTC 여도 화면은 "06:00 갱신")
//  - errorMessage 는 예외 종류만 (재단 호출 주소에 API 키가 들어 있어 예외 메시지에 섞일 수 있음 → 메시지는 안 넣음)
@Service
public class SyncLogService {

  private static final Logger log = LoggerFactory.getLogger(SyncLogService.class);
  static final ZoneId KST = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter ISO_SECONDS = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

  private final SyncLogRepository repository;
  private final Clock clock;

  @Autowired
  public SyncLogService(SyncLogRepository repository) {
    this(repository, Clock.system(KST));
  }

  // 시험용 (고정 시계). 다른 패키지 시험(명소 하루 1회)에서도 씀
  public SyncLogService(SyncLogRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  public LocalDateTime now() {
    return LocalDateTime.now(clock);
  }

  public void success(SyncLog.Source source, LocalDateTime startedAt, Integer fetched, Integer created,
      Integer updated, String note) {
    save(source, startedAt, SyncLog.Status.SUCCESS, fetched, created, updated, note);
  }

  public void fail(SyncLog.Source source, LocalDateTime startedAt, Integer fetched, String reason) {
    save(source, startedAt, SyncLog.Status.FAIL, fetched, null, null, reason);
  }

  public void fail(SyncLog.Source source, LocalDateTime startedAt, Integer fetched, Throwable error) {
    fail(source, startedAt, fetched, errorText(error));
  }

  private void save(SyncLog.Source source, LocalDateTime startedAt, SyncLog.Status status, Integer fetched,
      Integer created, Integer updated, String error) {
    try {
      LocalDateTime finishedAt = now();
      repository.save(new SyncLog(source, startedAt == null ? finishedAt : startedAt, finishedAt, status,
          fetched, created, updated, truncate(error)));
    } catch (RuntimeException e) {
      // 메시지는 남기지 않음 (DB 오류에 값이 섞일 수 있음)
      log.warn("동기화 이력 저장 실패 source={} status={} {}", source, status, e.getClass().getSimpleName());
    }
  }

  // 출처별 마지막 성공 시각 (명소 하루 1회 판단용). 조회 실패면 비어 있음 → 부르는 쪽은 "할 때가 됨"으로 봄
  public Optional<LocalDateTime> lastSuccessAt(SyncLog.Source source) {
    try {
      return repository.findFirstBySourceAndStatusOrderByFinishedAtDescIdDesc(source, SyncLog.Status.SUCCESS)
          .map(SyncLog::getFinishedAt);
    } catch (RuntimeException e) {
      log.warn("동기화 이력 조회 실패 {}", e.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  // 재단 마지막 성공 → 화면 "M/D HH:mm 갱신" + 그 뒤 연속 실패 횟수(실패 알림 워크플로용)
  // 성공이 없으면 lastSuccessAt null, 조회 실패면 모두 null (화면은 줄을 숨기고 워크플로는 판단 보류)
  public SyncStatusResponse foundationStatus() {
    try {
      Optional<SyncLog> last = repository.findFirstBySourceAndStatusOrderByFinishedAtDescIdDesc(SyncLog.Source.SWCF,
          SyncLog.Status.SUCCESS);
      // 실패 행은 성공보다 나중에 저장될수록 id 가 큼 → "마지막 성공 뒤" = id 가 더 큰 실패
      long failures = last
          .map(s -> repository.countBySourceAndStatusAndIdGreaterThan(SyncLog.Source.SWCF, SyncLog.Status.FAIL, s.getId()))
          .orElseGet(() -> repository.countBySourceAndStatus(SyncLog.Source.SWCF, SyncLog.Status.FAIL));
      int consecutive = (int) Math.min(failures, Integer.MAX_VALUE);
      return last
          .map(s -> new SyncStatusResponse(s.getFinishedAt().truncatedTo(ChronoUnit.SECONDS).format(ISO_SECONDS),
              s.getCreatedCount(), s.getUpdatedCount(), consecutive))
          .orElseGet(() -> new SyncStatusResponse(null, null, null, consecutive));
    } catch (RuntimeException e) {
      log.warn("동기화 이력 조회 실패 {}", e.getClass().getSimpleName());
      return SyncStatusResponse.none();
    }
  }

  // 예외 종류 + 원인 종류만 (예: "IllegalStateException ← ConnectException"). 메시지 · 주소 · 키는 넣지 않음
  static String errorText(Throwable error) {
    if (error == null) return null;
    StringBuilder sb = new StringBuilder(error.getClass().getSimpleName());
    Throwable cause = error.getCause();
    for (int depth = 0; cause != null && cause != error && depth < 3; depth++) {
      sb.append(" ← ").append(cause.getClass().getSimpleName());
      cause = cause.getCause();
    }
    return sb.toString();
  }

  static String truncate(String text) {
    if (text == null || text.length() <= SyncLog.ERROR_MAX) return text;
    int end = SyncLog.ERROR_MAX;
    if (Character.isHighSurrogate(text.charAt(end - 1))) end--; // 이모지 등 반쪽 글자가 남지 않게
    return text.substring(0, end);
  }
}
