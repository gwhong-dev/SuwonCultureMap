package com.suwon.festival.service.instagram;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.service.instagram.InstagramFetchException.Kind;

// 인스타 5단계: 대상 계정마다 최근 게시물 수집 → 게시물별 정리 · 좌표 · 저장 (설계 문서 2번 파이프라인)
//  - 계정 하나가 실패하면 그 계정만 건너뜀. 단 토큰 · 호출 제한 문제는 다른 계정도 똑같이 실패하므로 바로 멈춤
//  - 게시물 하나가 실패하면 그 게시물만 되돌리고 다음 게시물 진행
//  - 로그에는 계정 이름 · 게시물 id · 오류 종류만 (토큰 · 캡션 원문 X)
@Service
public class InstagramSyncService {

  private static final Logger log = LoggerFactory.getLogger(InstagramSyncService.class);

  private final InstagramClient client;
  private final InstagramPostProcessor processor;
  private final List<String> accounts;
  private final int fetchLimit;

  public InstagramSyncService(InstagramClient client, InstagramPostProcessor processor,
      @Value("${instagram.accounts:}") String accounts,
      @Value("${instagram.fetch-limit:10}") int fetchLimit) {
    this.client = client;
    this.processor = processor;
    this.accounts = Arrays.stream(accounts == null ? new String[0] : accounts.split(","))
        .map(String::strip).filter(s -> !s.isEmpty()).distinct().toList();
    this.fetchLimit = fetchLimit;
  }

  public InstagramSyncResult sync() {
    if (!client.isConfigured()) {
      return InstagramSyncResult.notConfigured("인스타 토큰 · 내 계정 ID 설정이 비어 있거나 형식이 다름");
    }
    if (accounts.isEmpty()) {
      return InstagramSyncResult.notConfigured("대상 계정(instagram.accounts)이 비어 있음");
    }

    List<String> accountErrors = new ArrayList<>();
    Map<String, Integer> blocks = new LinkedHashMap<>();
    for (InstagramBlock.Status s : InstagramBlock.Status.values()) blocks.put(s.name(), 0);
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
      for (InstagramPost post : fetched) {
        posts++;
        try {
          InstagramPostProcessor.Outcome o = processor.process(account, post);
          if (o.newMedia()) newPosts++;
          o.blocks().forEach((s, n) -> blocks.merge(s.name(), n, Integer::sum));
          saved += o.eventsSaved();
          updated += o.eventsUpdated();
          duplicates += o.duplicateBlocks();
        } catch (RuntimeException e) {
          postErrors++;
          log.warn("인스타 게시물 처리 실패 account={} mediaId={} {}", account, post.id(), e.getClass().getSimpleName());
        }
      }
    }

    InstagramSyncResult result = new InstagramSyncResult(true, null, accounts.size(), okAccounts, accountErrors,
        posts, newPosts, postErrors, blocks, saved, updated, duplicates);
    log.info("인스타 동기화 끝: 계정 {}/{} · 게시물 {} (새 {} · 실패 {}) · 행사 저장 {} · 갱신 {} · 블록 {}",
        okAccounts, accounts.size(), posts, newPosts, postErrors, saved, updated, blocks);
    return result;
  }
}
