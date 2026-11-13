package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.EventDate;
import com.suwon.festival.entity.Favorite;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventDateRepository;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.FavoriteRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;
import com.suwon.festival.repository.MemberRepository;
import com.suwon.festival.service.caption.CaptionParser;
import com.suwon.festival.service.place.PlaceGeocoder;
import com.suwon.festival.service.schedule.EventScheduleWriter;

import jakarta.persistence.EntityManager;

// 종료 7일 지난 인스타 행사 삭제 (ExpiredInstagramEventCleaner, 11/11 사용자 결정). 오늘 = 2026-11-20 (KST) 고정
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstagramExpiredCleanupTest {

  static final LocalDate TODAY = LocalDate.of(2026, 11, 20);
  // 11/20 00:30 KST = 11/19 15:30 UTC → KST 날짜로 판단하는지 (UTC 면 11/19)
  static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-19T15:30:00Z"), InstagramPostProcessor.KST);

  @Autowired private EventRepository eventRepository;
  @Autowired private FavoriteRepository favoriteRepository;
  @Autowired private EventDateRepository eventDateRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private StaleBlockRetirer retirer;
  @Autowired private EventScheduleWriter scheduleWriter;
  @Autowired private EntityManager em;

  private ExpiredInstagramEventCleaner cleaner;
  private int seq;

  @BeforeEach
  void setUp() {
    cleaner = new ExpiredInstagramEventCleaner(new InstagramExpiry(true, CLOCK), eventRepository, favoriteRepository,
        eventDateRepository, blockRepository);
  }

  private Event insta(String title, LocalDate start, LocalDate end) {
    return save(title, start, end, Event.SOURCE_INSTAGRAM, "1790000000000" + (100 + seq) + ":k" + seq++);
  }

  private Event swcf(String title, LocalDate start, LocalDate end) {
    return save(title, start, end, Event.SOURCE_SWCF, null);
  }

  private Event save(String title, LocalDate start, LocalDate end, String source, String sourceKey) {
    return eventRepository.save(Event.builder().idx(source.equals(Event.SOURCE_SWCF) ? 900000L + seq++ : null)
        .title(title).category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 10, 1, 9, 0)).startDate(start).endDate(end)
        .source(source).sourceKey(sourceKey).build());
  }

  private InstagramBlock block(Event event, String mediaId) {
    InstagramMedia media = mediaRepository.findByMediaId(mediaId).orElseGet(() -> mediaRepository.save(
        InstagramMedia.builder().mediaId(mediaId).account("suwon_city").collectedAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build()));
    InstagramBlock b = blockRepository.save(InstagramBlock.builder().media(media).blockKey("k" + seq++)
        .status(InstagramBlock.Status.READY).updatedAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build());
    b.update(InstagramBlock.Status.READY, null, event, LocalDateTime.of(2026, 10, 1, 9, 0));
    return b;
  }

  private Favorite favorite(Member member, Event event) {
    return favoriteRepository.save(Favorite.builder().member(member).event(event).createdAt(LocalDateTime.of(2026, 10, 1, 9, 0)).build());
  }

  private int clean() {
    em.flush();
    em.clear();
    int n = cleaner.clean();
    em.flush();
    em.clear();
    return n;
  }

  private boolean exists(Event e) {
    return eventRepository.findById(e.getId()).isPresent();
  }

  @Test
  void 종료_6일은_남기고_7일_8일은_지움_KST_기준() {
    Event day6 = insta("6일 지남", TODAY.minusDays(10), TODAY.minusDays(6));
    Event day7 = insta("7일 지남", TODAY.minusDays(10), TODAY.minusDays(7));
    Event day8 = insta("8일 지남", TODAY.minusDays(10), TODAY.minusDays(8));
    Event future = insta("진행 중", TODAY.minusDays(10), TODAY.plusDays(3));

    assertThat(clean()).isEqualTo(2);
    assertThat(exists(day6)).isTrue();
    assertThat(exists(day7)).isFalse();
    assertThat(exists(day8)).isFalse();
    assertThat(exists(future)).isTrue();
  }

  @Test
  void 종료일이_없으면_시작일_기준_둘_다_없으면_남김() {
    Event startOnly6 = insta("시작만 6일", TODAY.minusDays(6), null);
    Event startOnly7 = insta("시작만 7일", TODAY.minusDays(7), null);
    Event noDate = insta("날짜 없음", null, null);

    clean();
    assertThat(exists(startOnly6)).isTrue();
    assertThat(exists(startOnly7)).isFalse();
    assertThat(exists(noDate)).isTrue();
  }

  @Test
  void 재단_행사는_오래_지나도_안_지움() {
    Event old = swcf("재단 지난 행사", TODAY.minusDays(60), TODAY.minusDays(30));
    assertThat(clean()).isZero();
    assertThat(exists(old)).isTrue();
  }

  @Test
  void 찜_진행날짜_블록을_정리하고_행사를_지움() {
    Event e = insta("찜 있는 행사", TODAY.minusDays(9), TODAY.minusDays(8));
    Member member = memberRepository.save(Member.create());
    favorite(member, e);
    eventDateRepository.save(new EventDate(e.getId(), TODAY.minusDays(9)));
    eventDateRepository.save(new EventDate(e.getId(), TODAY.minusDays(8)));
    InstagramBlock b = block(e, "1790000000000901");

    assertThat(clean()).isEqualTo(1);
    assertThat(exists(e)).isFalse();
    assertThat(favoriteRepository.findByMemberId(member.getId())).isEmpty();
    assertThat(eventDateRepository.findByEventId(e.getId())).isEmpty();
    InstagramBlock after = blockRepository.findById(b.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(InstagramBlock.Status.EXPIRED);
    assertThat(after.getEvent()).isNull();
    assertThat(after.getReason()).isEqualTo(ExpiredInstagramEventCleaner.REASON);
    assertThat(memberRepository.findById(member.getId())).isPresent(); // 회원은 그대로
  }

  @Test
  void 합쳐진_행_숨긴_인스타는_찜을_대표로_옮기고_대표인_인스타는_숨긴_행을_풀어줌() {
    // ① 지난 인스타가 아직 진행 중인 재단 대표에 숨겨짐 → 찜은 대표로 (이미 대표를 찜했으면 지움)
    Event rep = swcf("재단 대표", TODAY.minusDays(30), TODAY.plusDays(30));
    Event hiddenInsta = insta("숨긴 인스타", TODAY.minusDays(10), TODAY.minusDays(8));
    hiddenInsta.mergeInto(rep.getId());
    Member a = memberRepository.save(Member.create());
    Member b = memberRepository.save(Member.create());
    favorite(a, hiddenInsta);
    favorite(b, hiddenInsta);
    favorite(b, rep);

    // ② 지난 인스타가 대표 · 재단 행이 숨겨짐 → 재단 행 숨김 해제, 재단 행은 남음
    Event repInsta = insta("대표 인스타", TODAY.minusDays(10), TODAY.minusDays(8));
    Event hiddenSwcf = swcf("숨긴 재단", TODAY.minusDays(10), TODAY.minusDays(8));
    hiddenSwcf.mergeInto(repInsta.getId());

    // ③ 지난 인스타끼리 (대표도 지워짐)
    Event repInsta2 = insta("대표 인스타 2", TODAY.minusDays(10), TODAY.minusDays(9));
    Event hiddenInsta2 = insta("숨긴 인스타 2", TODAY.minusDays(10), TODAY.minusDays(9));
    hiddenInsta2.mergeInto(repInsta2.getId());
    Member c = memberRepository.save(Member.create());
    favorite(c, hiddenInsta2);

    assertThat(clean()).isEqualTo(4);
    assertThat(exists(rep)).isTrue();
    assertThat(favoriteRepository.findByMemberId(a.getId())).extracting(f -> f.getEvent().getId()).containsExactly(rep.getId());
    assertThat(favoriteRepository.findByMemberId(b.getId())).extracting(f -> f.getEvent().getId()).containsExactly(rep.getId());
    Event swcfAfter = eventRepository.findById(hiddenSwcf.getId()).orElseThrow();
    assertThat(swcfAfter.getMergedIntoId()).isNull();
    assertThat(favoriteRepository.findByMemberId(c.getId())).isEmpty();
  }

  @Test
  void 꺼져_있으면_아무것도_안_지움() {
    Event e = insta("8일 지남", TODAY.minusDays(10), TODAY.minusDays(8));
    ExpiredInstagramEventCleaner off = new ExpiredInstagramEventCleaner(new InstagramExpiry(false, CLOCK), eventRepository,
        favoriteRepository, eventDateRepository, blockRepository);
    em.flush();
    assertThat(off.clean()).isZero();
    assertThat(exists(e)).isTrue();
  }

  // 수집이 꺼져 있어도 동기화 끝에 지난 행사를 지우고, 그 행사 사진 파일도 같은 실행에서 지움. 진행 중 행사 사진은 남김
  @Test
  void 동기화_끝에_지난_행사_사진도_지움() throws IOException {
    Path dir = Files.createTempDirectory("insta-expire-test");
    InstagramImageStore store = new InstagramImageStore(dir, List.of("127.0.0.1"), true);
    Event old = insta("지난 행사", TODAY.minusDays(10), TODAY.minusDays(8));
    Event live = insta("진행 중", TODAY.minusDays(1), TODAY.plusDays(5));
    old.useImage(InstagramImageStore.PUBLIC_PREFIX + "1790000000000501.jpg");
    live.useImage(InstagramImageStore.PUBLIC_PREFIX + "1790000000000502.jpg");
    Files.writeString(dir.resolve("1790000000000501.jpg"), "x");
    Files.writeString(dir.resolve("1790000000000502.jpg"), "x");
    em.flush();

    InstagramExpiry expiry = new InstagramExpiry(true, CLOCK);
    InstagramSyncService service = new InstagramSyncService(new InstagramSyncServiceTest.FakeClient(), null,
        new InstagramTokenExpiry("", CLOCK), new InstagramSwitch(false), "suwon_city", 10, store, eventRepository,
        new ExpiredInstagramEventCleaner(expiry, eventRepository, favoriteRepository, eventDateRepository, blockRepository));
    service.sync();
    em.flush();
    em.clear();

    assertThat(exists(old)).isFalse();
    assertThat(exists(live)).isTrue();
    assertThat(Files.exists(dir.resolve("1790000000000501.jpg"))).isFalse();
    assertThat(Files.exists(dir.resolve("1790000000000502.jpg"))).isTrue();
  }

  // 같은 게시물이 다시 수집돼도 지운 행사가 살아나지 않음
  @Test
  void 다시_수집돼도_안_살아남() {
    InstagramImageStore store = InstagramImageStore.disabled();
    String id = "1790000000000777";
    String caption = String.join("\n", "가을 음악회", "🗓️ 2026. 11. 5.(목) ~ 11. 7.(토) 19:00~21:00", "📍 장소: 가상 아트홀");
    InstagramPost post = new InstagramPost(id, caption, "https://www.instagram.com/p/" + id + "/",
        OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.UTC), "IMAGE");
    InstagramSyncServiceTest.FakeClient client = new InstagramSyncServiceTest.FakeClient();
    client.answers.put("suwon_city", List.of(post));

    // 10/31: 수집 → 행사 저장 (종료 11/7)
    Clock before = Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), InstagramPostProcessor.KST);
    syncWith(client, before, store);
    assertThat(instaEvents()).hasSize(1);

    // 11/20(종료 13일 뒤): 같은 글이 다시 수집됨 → 갱신 뒤 동기화 끝에 삭제
    syncWith(client, CLOCK, store);
    assertThat(instaEvents()).isEmpty();
    InstagramBlock b = blockRepository.findAll().stream().filter(x -> x.getMedia().getMediaId().equals(id)).findFirst().orElseThrow();
    assertThat(b.getStatus()).isEqualTo(InstagramBlock.Status.EXPIRED);

    // 한 번 더: 지난 날짜라 행사를 다시 만들지 않음, 블록은 EXPIRED 그대로
    InstagramSyncResult r = syncWith(client, CLOCK, store);
    assertThat(instaEvents()).isEmpty();
    assertThat(r.blocks()).containsEntry("EXPIRED", 1);
    assertThat(blockRepository.findById(b.getId()).orElseThrow().getStatus()).isEqualTo(InstagramBlock.Status.EXPIRED);
  }

  private List<Event> instaEvents() {
    return eventRepository.findAll().stream().filter(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource())).toList();
  }

  private InstagramSyncResult syncWith(InstagramSyncServiceTest.FakeClient client, Clock clock, InstagramImageStore store) {
    InstagramExpiry expiry = new InstagramExpiry(true, clock);
    InstagramPostProcessor processor = new InstagramPostProcessor(mediaRepository, blockRepository, eventRepository,
        new PlaceGeocoder(new InstagramSyncServiceTest.FakeSearch()), new CaptionParser(null), clock, retirer,
        scheduleWriter, store, expiry);
    ExpiredInstagramEventCleaner c = new ExpiredInstagramEventCleaner(expiry, eventRepository, favoriteRepository,
        eventDateRepository, blockRepository);
    InstagramSyncService service = new InstagramSyncService(client, processor, new InstagramTokenExpiry("", clock),
        new InstagramSwitch(true), "suwon_city", 10, store, eventRepository, c);
    InstagramSyncResult r = service.sync();
    em.flush();
    em.clear();
    return r;
  }
  // 관광 대상 아닌 인스타 행사 정리 — 최근 글 몇 개만 다시 처리되므로 오래된 글로 이미 올라간 행사도 동기화 끝에 내림
  private int cleanNonTourist() {
    em.flush();
    em.clear();
    int n = cleaner.cleanNonTourist();
    em.flush();
    em.clear();
    return n;
  }

  @Test
  void 관광_대상_아닌_인스타_행사는_진행날짜_숨긴_행과_함께_지우고_블록은_SKIPPED() {
    Event forum = insta("제12회 가상 교통정책발전포럼", TODAY.plusDays(1), TODAY.plusDays(1));
    eventDateRepository.save(new EventDate(forum.getId(), TODAY.plusDays(1)));
    Event hidden = insta("가상 교통정책 포럼 안내", TODAY.plusDays(1), TODAY.plusDays(1));
    hidden.mergeInto(forum.getId());
    Event hiddenSwcf = swcf("숨긴 재단 행사", TODAY.plusDays(1), TODAY.plusDays(1));
    hiddenSwcf.mergeInto(forum.getId());
    InstagramBlock b = block(forum, "1790000000000951");
    Event concert = insta("가상 시립교향악단 정기연주회", TODAY.plusDays(1), TODAY.plusDays(1));

    assertThat(cleanNonTourist()).isEqualTo(2); // 포럼 + 숨긴 포럼
    assertThat(exists(forum)).isFalse();
    assertThat(exists(hidden)).isFalse();
    assertThat(exists(concert)).isTrue();
    assertThat(eventDateRepository.findByEventId(forum.getId())).isEmpty();
    assertThat(eventRepository.findById(hiddenSwcf.getId()).orElseThrow().getMergedIntoId()).isNull();
    InstagramBlock after = blockRepository.findById(b.getId()).orElseThrow();
    assertThat(after.getStatus()).isEqualTo(InstagramBlock.Status.SKIPPED);
    assertThat(after.getEvent()).isNull();
    assertThat(after.getReason()).isEqualTo("관광 대상 아님: 포럼");
  }

  @Test
  void 관광_대상_아니어도_찜이_걸린_행사와_재단_행사는_남김() {
    Event favored = insta("가상 센터 20주년 기념식", TODAY.plusDays(1), TODAY.plusDays(1));
    Member member = memberRepository.save(Member.create());
    favorite(member, favored);
    Event swcfForum = swcf("가상 재단 포럼", TODAY.plusDays(1), TODAY.plusDays(1));

    assertThat(cleanNonTourist()).isZero();
    assertThat(exists(favored)).isTrue();
    assertThat(exists(swcfForum)).isTrue();
  }
}
