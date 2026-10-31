package com.suwon.festival.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;
import com.suwon.festival.entity.LoginMethod;
import com.suwon.festival.entity.Member;

// 테이블이 Flyway 스크립트(V*.sql)로 만들어지고, 엔티티 4개의 모든 칸이 그 테이블과 맞는지 확인
//  - 칸 이름 · NOT NULL · 길이가 틀리면 저장이나 조회에서 바로 깨짐
@SpringBootTest
@ActiveProfiles("test")
@Transactional // 테스트가 끝나면 저장한 행을 되돌림 → 다른 테스트에 영향 없음
class RepositorySchemaTest {

  @Autowired private EventRepository eventRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private LoginMethodRepository loginMethodRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private InstagramMediaRepository instagramMediaRepository;
  @Autowired private InstagramBlockRepository instagramBlockRepository;
  @Autowired private Flyway flyway;
  @Autowired private EntityManager em;

  @Test
  void Flyway가_마지막_버전까지_적용함() {
    assertThat(flyway.info().current()).isNotNull();
    assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("2");
    assertThat(flyway.info().pending()).isEmpty();
  }

  @Test
  void 엔티티_4개를_모든_칸_채워_저장하고_다시_읽음() {
    long members = memberRepository.count();
    long logins = loginMethodRepository.count();
    long favorites = favoriteRepository.count();

    Member member = memberRepository.saveAndFlush(Member.builder().nickname("가상회원").build());

    loginMethodRepository.saveAndFlush(LoginMethod.builder()
        .provider("kakao").providerId("p-1").email("a@example.com").password(null).member(member).build());

    Event event = eventRepository.saveAndFlush(Event.builder()
        .idx(990001L).title("가상 행사").category("공연")
        .latitude(37.28).longitude(127.01)
        .imageUrl("i".repeat(1000)) // 칸 길이 끝까지
        .info("긴 설명 ".repeat(200)) // TEXT
        .pubDate(LocalDateTime.of(2026, 10, 31, 12, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 7))
        .link("l".repeat(1000)).place("p".repeat(500)).timeText("t".repeat(1000))
        .build());

    favoriteRepository.saveAndFlush(Favorite.builder()
        .member(member).event(event).createdAt(LocalDateTime.of(2026, 10, 31, 12, 0)).build());

    // 메모리(1차 캐시)를 비워야 findById 가 DB 에서 다시 읽음 → 진짜 저장 · 조회 왕복
    em.flush();
    em.clear();
    assertThat(eventRepository.findById(event.getId())).get()
        .satisfies(e -> {
          assertThat(e.getPlace()).hasSize(500);
          assertThat(e.getInfo()).hasSize("긴 설명 ".length() * 200);
          assertThat(e.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 7));
        });
    assertThat(memberRepository.count()).isEqualTo(members + 1);
    assertThat(loginMethodRepository.count()).isEqualTo(logins + 1);
    assertThat(favoriteRepository.count()).isEqualTo(favorites + 1);
  }

  @Test
  void 재단_행사는_출처_기본값_SWCF() {
    Event e = eventRepository.saveAndFlush(Event.builder()
        .idx(990002L).title("가상 재단 행사").category("행사").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 10, 31, 12, 0)).build());
    em.clear();
    assertThat(eventRepository.findById(e.getId())).get()
        .satisfies(r -> {
          assertThat(r.getSource()).isEqualTo(Event.SOURCE_SWCF);
          assertThat(r.getSourceKey()).isNull();
        });
  }

  @Test
  void 인스타_행사는_idx_없이_여러_개_저장되고_게시물_블록_상태와_연결됨() {
    Event a = eventRepository.saveAndFlush(instagramEvent("m1#가상키1"));
    Event b = eventRepository.saveAndFlush(instagramEvent("m1#가상키2")); // idx 가 둘 다 null 이어도 됨

    InstagramMedia media = instagramMediaRepository.saveAndFlush(InstagramMedia.builder()
        .mediaId("1790000000000001").account("gasang_city").permalink("https://www.instagram.com/p/AAA/")
        .postedAt(LocalDateTime.of(2026, 10, 30, 18, 0)).collectedAt(LocalDateTime.of(2026, 10, 31, 12, 0)).build());
    InstagramBlock block = instagramBlockRepository.saveAndFlush(InstagramBlock.builder()
        .media(media).blockKey("가상키1").status(InstagramBlock.Status.PARSED).reason(null)
        .updatedAt(LocalDateTime.of(2026, 10, 31, 12, 0)).build());
    block.update(InstagramBlock.Status.READY, null, a, LocalDateTime.of(2026, 10, 31, 12, 1));
    media.markProcessed(LocalDateTime.of(2026, 10, 31, 12, 1));
    em.flush();
    em.clear();

    assertThat(eventRepository.findBySourceAndSourceKey(Event.SOURCE_INSTAGRAM, "m1#가상키2")).get()
        .satisfies(r -> assertThat(r.getId()).isEqualTo(b.getId()));
    InstagramMedia readMedia = instagramMediaRepository.findByMediaId("1790000000000001").orElseThrow();
    assertThat(readMedia.getStatus()).isEqualTo(InstagramMedia.Status.PROCESSED);
    InstagramBlock readBlock = instagramBlockRepository.findByMediaAndBlockKey(readMedia, "가상키1").orElseThrow();
    assertThat(readBlock.getStatus()).isEqualTo(InstagramBlock.Status.READY);
    assertThat(readBlock.getEvent().getId()).isEqualTo(a.getId());
  }

  @Test
  void 같은_출처_키는_두_번_저장_안_됨() {
    eventRepository.saveAndFlush(instagramEvent("m2#중복"));
    assertThatThrownBy(() -> eventRepository.saveAndFlush(instagramEvent("m2#중복")))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private static Event instagramEvent(String sourceKey) {
    return Event.builder()
        .idx(null).title("가상 인스타 행사").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 10, 30, 18, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5))
        .link("https://www.instagram.com/p/AAA/").place("가상 아트홀")
        .source(Event.SOURCE_INSTAGRAM).sourceKey(sourceKey)
        .build();
  }
}
