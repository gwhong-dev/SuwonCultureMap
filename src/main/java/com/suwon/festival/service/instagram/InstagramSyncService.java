package com.suwon.festival.service.instagram;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.SyncLog;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.service.SyncLogService;
import com.suwon.festival.service.instagram.InstagramFetchException.Kind;

// 인스타 5단계: 대상 계정마다 최근 게시물 수집 → 게시물별 정리 · 좌표 · 저장 (설계 문서 2번 파이프라인)
//  - 계정 하나가 실패하면 그 계정만 건너뜀. 단 토큰 · 호출 제한 문제는 다른 계정도 똑같이 실패하므로 바로 멈춤
//  - 게시물 하나가 실패하면 그 게시물만 되돌리고 다음 게시물 진행
//  - 로그에는 계정 이름 · 게시물 id · 오류 종류만 (토큰 · 캡션 원문 X)
//  - 공식 계정 목록(OfficialInstagramAccounts)에 없는 계정은 호출하지 않고 건너뜀 (개인 계정 제외)
@Service
public class InstagramSyncService {

  private static final Logger log = LoggerFactory.getLogger(InstagramSyncService.class);

  private final InstagramClient client;
  private final InstagramPostProcessor processor;
  private final InstagramTokenExpiry tokenExpiry;
  private final InstagramSwitch instagramSwitch;
  private final List<String> accounts;
  private final List<String> rejectedAccounts;
  private final int fetchLimit;
  private final InstagramImageStore imageStore;
  private final EventRepository eventRepository;
  private final ExpiredInstagramEventCleaner expiredCleaner; // null = 지난 행사 정리 안 함 (시험용 생성자)
  private final SyncLogService syncLogService; // null = 동기화 이력 안 남김 (시험용 생성자)

  // 예약 동기화와 POST /api/instagram/sync 가 겹치면 뒤의 것을 건너뜀 (ScheduledSyncJob 과 같은 방식, 서버 1대 기준)
  //  겹치면 한쪽 사진 정리가 다른 쪽이 아직 저장 안 한 사진 파일을 지울 수 있음
  private final AtomicBoolean running = new AtomicBoolean();

  @Autowired
  public InstagramSyncService(InstagramClient client, InstagramPostProcessor processor,
      InstagramTokenExpiry tokenExpiry, InstagramSwitch instagramSwitch,
      @Value("${instagram.accounts:}") String accounts,
      @Value("${instagram.fetch-limit:10}") int fetchLimit,
      InstagramImageStore imageStore, EventRepository eventRepository, ExpiredInstagramEventCleaner expiredCleaner,
      SyncLogService syncLogService) {
    this.syncLogService = syncLogService;
    this.imageStore = imageStore;
    this.expiredCleaner = expiredCleaner;
    this.eventRepository = eventRepository;
    this.client = client;
    this.processor = processor;
    this.tokenExpiry = tokenExpiry;
    this.instagramSwitch = instagramSwitch;
    List<String> configured = Arrays.stream(accounts == null ? new String[0] : accounts.split(","))
        .map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    this.accounts = configured.stream().filter(OfficialInstagramAccounts::isOfficial)
        .map(OfficialInstagramAccounts::normalize).distinct().toList();
    this.rejectedAccounts = configured.stream().filter(a -> !OfficialInstagramAccounts.isOfficial(a)).toList();
    if (!rejectedAccounts.isEmpty()) {
      log.warn("공식 계정 목록에 없어 수집하지 않는 계정: {}", rejectedAccounts);
    }
    this.fetchLimit = fetchLimit;
  }

  // 시험용: 동기화 이력 없이
  public InstagramSyncService(InstagramClient client, InstagramPostProcessor processor,
      InstagramTokenExpiry tokenExpiry, InstagramSwitch instagramSwitch, String accounts, int fetchLimit,
      InstagramImageStore imageStore, EventRepository eventRepository, ExpiredInstagramEventCleaner expiredCleaner) {
    this(client, processor, tokenExpiry, instagramSwitch, accounts, fetchLimit, imageStore, eventRepository,
        expiredCleaner, null);
  }

  // 시험용: 사진 저장 없이
  InstagramSyncService(InstagramClient client, InstagramPostProcessor processor, InstagramTokenExpiry tokenExpiry,
      InstagramSwitch instagramSwitch, String accounts, int fetchLimit) {
    this(client, processor, tokenExpiry, instagramSwitch, accounts, fetchLimit, InstagramImageStore.disabled(), null, null, null);
  }

  // 수집이 끝나면(꺼져 있어도) 종료 7일 지난 인스타 행사를 지우고(ExpiredInstagramEventCleaner),
  // DB 행사가 더 쓰지 않는 사진 파일을 지움 → 행사가 지워지면 사진도 같은 실행에서 (docs/instagram-images.md)
  public InstagramSyncResult sync() {
    if (!running.compareAndSet(false, true)) {
      log.info("인스타 동기화가 이미 실행 중 → 이번 실행은 건너뜀");
      return InstagramSyncResult.alreadyRunning();
    }
    try {
      return collectAndRecord();
    } finally {
      try {
        cleanupExpired();
        cleanupImages();
      } finally {
        running.set(false);
      }
    }
  }

  // 실행마다 이력 1줄 (sync_log). 꺼져 있음 · 설정 없음(수집 안 함)은 안 남김
  //  계정이 하나도 안 되면 FAIL, 일부만 실패면 SUCCESS + 실패 계정 수. 오류 글은 종류 · 개수만 (토큰 · 응답 메시지 X)
  private InstagramSyncResult collectAndRecord() {
    LocalDateTime startedAt = syncLogService == null ? null : syncLogService.now();
    InstagramSyncResult result;
    try {
      result = collect();
    } catch (RuntimeException e) {
      if (syncLogService != null && instagramSwitch.isEnabled()) {
        syncLogService.fail(SyncLog.Source.INSTAGRAM, startedAt, null, e);
      }
      throw e;
    }
    if (syncLogService != null && result.configured()) {
      int failedAccounts = result.accounts() - result.accountsOk();
      String accountNote = failedAccounts > 0 ? "계정 수집 실패 " + failedAccounts + "/" + result.accounts() : null;
      if (result.accountsOk() == 0 && result.accounts() > 0) {
        syncLogService.fail(SyncLog.Source.INSTAGRAM, startedAt, result.posts(), accountNote);
      } else {
        String postNote = result.postErrors() > 0 ? "게시물 처리 실패 " + result.postErrors() + "건" : null;
        String note = accountNote == null ? postNote : postNote == null ? accountNote : accountNote + " · " + postNote;
        syncLogService.success(SyncLog.Source.INSTAGRAM, startedAt, result.posts(), result.eventsSaved(),
            result.eventsUpdated(), note);
      }
    }
    return result;
  }

  void cleanupExpired() {
    if (expiredCleaner == null) return;
    try {
      expiredCleaner.clean();
    } catch (RuntimeException e) {
      // 한 트랜잭션이라 모두 되돌려짐 → 다음 동기화에서 다시. 메시지는 남기지 않음 (DB 오류에 값이 섞일 수 있음)
      log.warn("지난 인스타 행사 삭제 실패 {}", e.getClass().getSimpleName());
    }
  }

  void cleanupImages() {
    if (!imageStore.isEnabled() || eventRepository == null) return;
    Set<String> keep;
    try {
      keep = eventRepository.findImageUrlsBySource(Event.SOURCE_INSTAGRAM).stream()
          .map(InstagramImageStore::mediaIdOf).filter(Objects::nonNull).collect(Collectors.toSet());
    } catch (RuntimeException e) {
      // 쓰는 목록을 모르면 지우지 않음 (틀리게 지우면 사진을 다시 못 받을 수 있음)
      log.warn("인스타 사진 정리 건너뜀: 사용 중 목록 조회 실패 {}", e.getClass().getSimpleName());
      return;
    }
    imageStore.cleanup(keep);
  }

  private InstagramSyncResult collect() {
    if (!instagramSwitch.isEnabled()) {
      return InstagramSyncResult.notConfigured("인스타 수집 중단 상태 (instagram.sync.enabled=false)", null);
    }
    String tokenWarning = tokenExpiry.warning();
    if (tokenWarning != null) log.warn("{}", tokenWarning);
    if (!client.isConfigured()) {
      return InstagramSyncResult.notConfigured("인스타 토큰 · 내 계정 ID 설정이 비어 있거나 형식이 다름", tokenWarning);
    }
    if (accounts.isEmpty()) {
      String reason = rejectedAccounts.isEmpty() ? "대상 계정(instagram.accounts)이 비어 있음"
          : "대상 계정이 모두 공식 계정 목록에 없음: " + String.join(", ", rejectedAccounts);
      return InstagramSyncResult.notConfigured(reason, tokenWarning);
    }

    List<String> accountErrors = new ArrayList<>();
    for (String rejected : rejectedAccounts) accountErrors.add(rejected + ": 공식 계정 목록에 없어 건너뜀");
    Map<String, Integer> blocks = new LinkedHashMap<>();
    for (InstagramBlock.Status s : InstagramBlock.Status.values()) blocks.put(s.name(), 0);
    Map<String, Map<String, Integer>> blocksByAccount = new LinkedHashMap<>();
    int okAccounts = 0, posts = 0, newPosts = 0, postErrors = 0, saved = 0, updated = 0, duplicates = 0;

    for (String account : accounts) {
      List<InstagramPost> fetched;
      try {
        fetched = client.fetchRecent(account, fetchLimit);
      } catch (InstagramFetchException e) {
        accountErrors.add(account + ": " + e.getMessage());
        log.warn("인스타 수집 실패 account={} {}", account, e.getMessage());
        if (e.kind() == Kind.TOKEN_INVALID || e.kind() == Kind.RATE_LIMIT || e.kind() == Kind.PERMISSION) {
          accountErrors.add("남은 계정은 건너뜀 (" + e.kind() + " 는 모든 계정에 같은 원인)");
          break;
        }
        continue;
      }
      okAccounts++;
      Map<InstagramBlock.Status, Integer> accountBlocks = new EnumMap<>(InstagramBlock.Status.class);
      Map<InstagramImageStore.Reason, Integer> accountImages = new EnumMap<>(InstagramImageStore.Reason.class);
      for (InstagramPost post : fetched) {
        posts++;
        try {
          InstagramPostProcessor.Outcome o = processor.process(account, post);
          if (o.newMedia()) newPosts++;
          o.blocks().forEach((s, n) -> {
            blocks.merge(s.name(), n, Integer::sum);
            accountBlocks.merge(s, n, Integer::sum);
          });
          saved += o.eventsSaved();
          updated += o.eventsUpdated();
          duplicates += o.duplicateBlocks();
          if (o.imageReason() != null) accountImages.merge(o.imageReason(), 1, Integer::sum);
        } catch (RuntimeException e) {
          postErrors++;
          // 메시지는 남기지 않음 (DB 오류 메시지에 값이 섞일 수 있음) → 예외 종류와 원인 종류만
          Throwable cause = e.getCause();
          log.warn("인스타 게시물 처리 실패 account={} mediaId={} {}{}", account, post.id(), e.getClass().getSimpleName(),
              cause == null ? "" : " ← " + cause.getClass().getSimpleName());
        }
      }
      Map<String, Integer> named = new LinkedHashMap<>(); // 상태 순서(EnumMap) 그대로, 0 은 빠짐
      accountBlocks.forEach((s, n) -> { if (n > 0) named.put(s.name(), n); });
      blocksByAccount.put(account, named);
      log.info("인스타 계정 결과 account={} 게시물 {} · 블록 {}", account, fetched.size(), named);
      // 사진 진단: 행사가 나온 글의 사진 처리 이유 · 이번에 받아 온 게시물 id (최근 N개 밖이면 여기 없음)
      log.info("인스타 사진 결과 account={} 사진 {} · 받아 온 게시물 {}개 {}", account, accountImages, fetched.size(),
          fetched.stream().map(InstagramPost::id).toList());
    }

    InstagramSyncResult result = new InstagramSyncResult(true, null, accounts.size(), okAccounts, accountErrors,
        posts, newPosts, postErrors, blocks, blocksByAccount, saved, updated, duplicates, tokenWarning, null);
    log.info("인스타 동기화 끝: 계정 {}/{} · 게시물 {} (새 {} · 실패 {}) · 행사 저장 {} · 갱신 {} · 블록 {}",
        okAccounts, accounts.size(), posts, newPosts, postErrors, saved, updated, blocks);
    return result;
  }
}
