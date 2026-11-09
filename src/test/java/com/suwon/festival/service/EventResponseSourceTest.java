package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.suwon.festival.dto.EventResponse;
import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;

// 인스타 행사는 사진 · info 가 없음 → 상세 화면이 쓸 장소 · 시간 · 출처(계정)가 응답에 실리는지
@SpringBootTest(properties = "instagram.sync.enabled=true")
@ActiveProfiles("test")
@Transactional
class EventResponseSourceTest {

  private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 1, 12, 0);

  @Autowired private EventService eventService;
  @Autowired private EventRepository eventRepository;
  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private EntityManager em;

  @Test
  void 인스타_행사는_장소_시간_출처_계정이_응답에_실림() {
    Event event = eventRepository.saveAndFlush(Event.builder()
        .title("가상 행사 인스타").category("공연").latitude(37.28).longitude(127.01).pubDate(AT)
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5))
        .link("https://www.instagram.com/p/TEST/").place("가상 공연장").timeText("오후 7시")
        .source(Event.SOURCE_INSTAGRAM).sourceKey("출처#1").build());
    InstagramMedia media = mediaRepository.saveAndFlush(InstagramMedia.builder()
        .mediaId("가상게시물1").account("sw_culture").permalink("https://www.instagram.com/p/TEST/")
        .postedAt(AT).collectedAt(AT).build());
    InstagramBlock block = blockRepository.saveAndFlush(InstagramBlock.builder()
        .media(media).blockKey("가상블록").status(InstagramBlock.Status.PARSED).updatedAt(AT).build());
    block.update(InstagramBlock.Status.READY, null, event, AT);
    em.flush();
    em.clear();

    EventResponse response = find(event.getId());

    assertThat(response.imageUrl()).isNull();
    assertThat(response.info()).isNull();
    assertThat(response.place()).isEqualTo("가상 공연장");
    assertThat(response.timeText()).isEqualTo("오후 7시");
    assertThat(response.source()).isEqualTo(Event.SOURCE_INSTAGRAM);
    assertThat(response.sourceAccount()).isEqualTo("sw_culture");
    assertThat(response.link()).isEqualTo("https://www.instagram.com/p/TEST/");
  }

  @Test
  void 재단_행사는_출처_계정이_없고_info_가_그대로() {
    Event event = eventRepository.saveAndFlush(Event.builder()
        .idx(990001L).title("가상 행사 재단").category("전시").latitude(37.28).longitude(127.01).pubDate(AT)
        .info("장소: 가상 전시관").place("가상 전시관")
        .fee("무료").contact("031-000-0000").host("가상 재단").build());
    em.clear();

    EventResponse response = find(event.getId());

    assertThat(response.source()).isEqualTo(Event.SOURCE_SWCF);
    assertThat(response.sourceAccount()).isNull();
    assertThat(response.info()).isEqualTo("장소: 가상 전시관");
    // 상세 정보 분해 (V7) 칸도 응답에 실림
    assertThat(response.fee()).isEqualTo("무료");
    assertThat(response.contact()).isEqualTo("031-000-0000");
    assertThat(response.host()).isEqualTo("가상 재단");
  }

  @Test
  void 블록이_없는_인스타_행사는_계정만_비어_있음() {
    Event event = eventRepository.saveAndFlush(Event.builder()
        .title("가상 행사 블록 없음").category("공연").latitude(37.28).longitude(127.01).pubDate(AT)
        .place("가상 광장").source(Event.SOURCE_INSTAGRAM).sourceKey("출처#2").build());
    em.clear();

    EventResponse response = find(event.getId());

    assertThat(response.place()).isEqualTo("가상 광장");
    assertThat(response.sourceAccount()).isNull();
  }

  private EventResponse find(Long id) {
    return eventService.getAllEvents().stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
  }
}
