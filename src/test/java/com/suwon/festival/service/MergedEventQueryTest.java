package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.MemberRepository;

// 합쳐져 숨겨진 행(merged_into_id)이 조회 · 찜에 어떻게 보이는지 (docs/event-merge.md 4번 "영향 받는 조회")
//  - 화면엔 대표만 보임 → 찜도 대표 기준으로 보이고, 대표 id 로 추가 · 취소가 맞물려야 함
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MergedEventQueryTest {

  @Autowired private EventService eventService;
  @Autowired private FavoriteService favoriteService;
  @Autowired private EventRepository eventRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private EntityManager em;

  private Long memberId;
  private Event representative;
  private Event hiddenA;
  private Event hiddenB;
  private Event plain;

  @BeforeEach
  void setUp() {
    memberId = memberRepository.saveAndFlush(Member.create()).getId();
    representative = eventRepository.saveAndFlush(event("대표"));
    hiddenA = eventRepository.saveAndFlush(event("숨김A"));
    hiddenB = eventRepository.saveAndFlush(event("숨김B"));
    plain = eventRepository.saveAndFlush(event("일반"));
    hiddenA.mergeInto(representative.getId());
    hiddenB.mergeInto(representative.getId());
    em.flush();
    em.clear();
  }

  @Test
  void 행사_목록에_숨긴_행이_없음() {
    assertThat(eventService.getAllEvents()).extracting(EventResponse::id)
        .contains(representative.getId(), plain.getId())
        .doesNotContain(hiddenA.getId(), hiddenB.getId());
  }

  @Test
  void 숨긴_행을_찜했으면_찜_목록에_대표로_보임() {
    saveFavorite(hiddenA);

    assertThat(favoriteService.getFavoriteEvents(memberId)).extracting(EventResponse::id)
        .containsExactly(representative.getId());
  }

  @Test
  void 같은_대표로_모이는_찜은_1개만_보임() {
    saveFavorite(representative);
    saveFavorite(hiddenA);
    saveFavorite(hiddenB);
    saveFavorite(plain);

    assertThat(favoriteService.getFavoriteEvents(memberId)).extracting(EventResponse::id)
        .containsExactlyInAnyOrder(representative.getId(), plain.getId());
  }

  @Test
  void 숨긴_행을_대표보다_먼저_찜했어도_1개만_보임() {
    saveFavorite(hiddenA);
    saveFavorite(representative);

    assertThat(favoriteService.getFavoriteEvents(memberId)).extracting(EventResponse::id)
        .containsExactly(representative.getId());
  }

  @Test
  void 숨긴_행_id_로_찜하면_대표로_저장() {
    favoriteService.addFavorite(memberId, hiddenA.getId());

    assertThat(favoriteRepository.existsByMemberIdAndEventId(memberId, representative.getId())).isTrue();
    assertThat(favoriteRepository.existsByMemberIdAndEventId(memberId, hiddenA.getId())).isFalse();
  }

  @Test
  void 숨긴_행으로_찜한_상태에서_대표를_또_찜하면_이미_찜한_행사() {
    saveFavorite(hiddenA); // 합쳐지기 전에 찜한 경우

    assertThatThrownBy(() -> favoriteService.addFavorite(memberId, representative.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미");
  }

  @Test
  void 숨긴_행으로_찜한_것을_화면이_보낸_대표_id_로_취소함() {
    saveFavorite(hiddenA);

    favoriteService.removeFavorite(memberId, representative.getId());

    assertThat(favoriteService.getFavoriteEvents(memberId)).isEmpty();
  }

  @Test
  void 같은_대표로_모이는_찜은_취소할_때_모두_지움() {
    saveFavorite(representative);
    saveFavorite(hiddenA);
    saveFavorite(plain);

    favoriteService.removeFavorite(memberId, representative.getId());

    assertThat(favoriteService.getFavoriteEvents(memberId)).extracting(EventResponse::id)
        .containsExactly(plain.getId());
  }

  @Test
  void 찜하지_않은_행사를_취소하면_오류() {
    saveFavorite(plain);

    assertThatThrownBy(() -> favoriteService.removeFavorite(memberId, representative.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("즐겨찾기하지 않은");
  }

  @Test
  void 숨기지_않은_행사는_찜_추가_취소가_그대로() {
    favoriteService.addFavorite(memberId, plain.getId());
    assertThat(favoriteService.getFavoriteEvents(memberId)).extracting(EventResponse::id)
        .containsExactly(plain.getId());
    assertThatThrownBy(() -> favoriteService.addFavorite(memberId, plain.getId()))
        .isInstanceOf(IllegalArgumentException.class);

    favoriteService.removeFavorite(memberId, plain.getId());
    assertThat(favoriteService.getFavoriteEvents(memberId)).isEmpty();
  }

  @Test
  void 대표를_찜한_것을_숨긴_행_id_로_취소함() {
    saveFavorite(representative);

    favoriteService.removeFavorite(memberId, hiddenA.getId());

    assertThat(favoriteService.getFavoriteEvents(memberId)).isEmpty();
  }

  @Test
  void 대표를_찜한_상태에서_숨긴_행을_또_찜하면_이미_찜한_행사() {
    saveFavorite(representative);

    assertThatThrownBy(() -> favoriteService.addFavorite(memberId, hiddenA.getId()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("이미");
  }

  @Test
  void 없는_행사를_취소하면_오류() {
    assertThatThrownBy(() -> favoriteService.removeFavorite(memberId, -1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("즐겨찾기하지 않은");
  }

  @Test
  void 없는_행사를_찜하면_오류() {
    assertThatThrownBy(() -> favoriteService.addFavorite(memberId, -1L))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("존재하지 않는 행사");
  }

  private void saveFavorite(Event event) {
    Member member = memberRepository.getReferenceById(memberId);
    favoriteRepository.saveAndFlush(Favorite.builder()
        .member(member).event(eventRepository.getReferenceById(event.getId()))
        .createdAt(LocalDateTime.of(2026, 11, 1, 12, 0)).build());
    em.clear();
  }

  private static Event event(String key) {
    return Event.builder()
        .title("가상 행사 " + key).category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 11, 1, 12, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5))
        .source(Event.SOURCE_INSTAGRAM).sourceKey("합치기#" + key)
        .build();
  }
}
