package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceGeocoder;

// 게시물 하나가 중간에 실패하면 그 게시물이 쓴 것(게시물 · 블록)만 되돌려지는지 — 실제 스프링 빈(트랜잭션 프록시)으로 확인
// 테스트 전체 트랜잭션을 쓰지 않음 (쓰면 게시물별 롤백이 가려짐) → 끝나고 직접 정리
@SpringBootTest(properties = "instagram.accounts=gasang_city")
@ActiveProfiles("test")
class InstagramSyncRollbackTest {

  @MockitoBean private InstagramClient client;
  @MockitoBean private PlaceGeocoder geocoder;

  @Autowired private InstagramSyncService syncService;
  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private EventRepository eventRepository;

  static final OffsetDateTime POSTED = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.UTC);

  // 날짜 없는 블록(먼저 저장됨) → 정리된 블록(좌표 찾기에서 실패)
  static final String FIRST = String.join("\n",
      "주말 장터",
      "🗓️ 매주 금~일요일",
      "📍 가상 공원",
      "",
      "가을 음악회",
      "🗓️ 2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00",
      "📍 장소: 가상 아트홀");

  static final String SECOND = String.join("\n",
      "가을 음악회",
      "🗓️ 2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00",
      "📍 장소: 가상 아트홀");

  @AfterEach
  void cleanUp() {
    blockRepository.deleteAll();
    mediaRepository.deleteAll();
    eventRepository.deleteAll(eventRepository.findAll().stream()
        .filter(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource())).toList());
  }

  @Test
  void 중간에_실패한_게시물은_통째로_되돌리고_다음_게시물은_저장() {
    when(client.isConfigured()).thenReturn(true);
    when(client.fetchRecent(eq("gasang_city"), anyInt())).thenReturn(List.of(
        new InstagramPost("1790000000000011", FIRST, "https://www.instagram.com/p/A/", POSTED, "IMAGE"),
        new InstagramPost("1790000000000012", SECOND, "https://www.instagram.com/p/B/", POSTED, "IMAGE")));
    PlaceCandidate hall = new PlaceCandidate("가상 아트홀", "경기 수원시 팔달구 가상동 1", null, 37.28, 127.01);
    when(geocoder.geocode(any()))
        .thenThrow(new IllegalStateException("가상 실패")) // 첫 게시물: 블록 1개 · 게시물 행을 쓴 뒤 실패
        .thenReturn(new PlaceGeocoder.Result(PlaceGeocoder.Status.FOUND, hall, null));

    InstagramSyncResult r = syncService.sync();

    assertThat(r.posts()).isEqualTo(2);
    assertThat(r.postErrors()).isEqualTo(1);
    assertThat(r.eventsSaved()).isEqualTo(1);
    assertThat(mediaRepository.findByMediaId("1790000000000011")).isEmpty(); // 게시물 행도 되돌려짐
    assertThat(mediaRepository.findByMediaId("1790000000000012")).isPresent();
    assertThat(blockRepository.count()).isEqualTo(1); // 첫 게시물의 NO_DATE 블록은 남지 않음
    assertThat(eventRepository.findAll()).filteredOn(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource())).hasSize(1);
  }
}
