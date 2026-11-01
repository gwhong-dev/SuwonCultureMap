package com.suwon.festival.service.merge;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.InstagramBlock;
import com.suwon.festival.entity.InstagramMedia;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;

// 합치기 계산 → merged_into_id 반영 (docs/event-merge.md 5 · 6번)
//  - 다른 테스트가 남긴 행과 섞이지 않게 제목 · 좌표 · 날짜를 이 테스트만의 값으로 씀
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventMergeServiceTest {

  private static final LocalDate DAY = LocalDate.of(2031, 3, 10);

  @Autowired private EventMergeService mergeService;
  @Autowired private EventRepository eventRepository;
  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private EntityManager em;

  private int nextKey = 0;

  @Test
  void 재단_1개와_인스타_2개면_인스타_2개가_재단을_가리킴() {
    Event foundation = foundation(70001L, "합치기시험 국악한마당");
    Event instaA = instagram("<합치기시험 국악한마당>");
    Event instaB = instagram("합치기시험 국악 한마당");

    mergeService.recompute();
    reload();

    assertThat(mergedIntoOf(foundation)).isNull();
    assertThat(mergedIntoOf(instaA)).isEqualTo(foundation.getId());
    assertThat(mergedIntoOf(instaB)).isEqualTo(foundation.getId());
  }

  @Test
  void 두_번_실행해도_결과가_같음() {
    Event foundation = foundation(70002L, "합치기시험 두번실행 공연");
    Event insta = instagram("합치기시험 두번실행 공연");

    mergeService.recompute();
    em.flush();
    EventMergeResult second = mergeService.recompute();
    reload();

    assertThat(mergedIntoOf(insta)).isEqualTo(foundation.getId());
    assertThat(mergedIntoOf(foundation)).isNull();
    assertThat(second.groups()).anySatisfy(g -> assertThat(g.representative().id()).isEqualTo(foundation.getId()));
  }

  @Test
  void 재단_행_정보가_바뀌어_기준에서_벗어나면_숨김_해제() {
    Event foundation = foundation(70003L, "합치기시험 해제 공연");
    Event insta = instagram("합치기시험 해제 공연");
    mergeService.recompute();
    em.flush();

    foundation.updateFrom(Event.builder()
        .idx(70003L).title("합치기시험 해제 공연").category("공연").latitude(37.5).longitude(127.5)
        .pubDate(LocalDateTime.of(2031, 1, 2, 0, 0))
        .startDate(DAY.plusMonths(2)).endDate(DAY.plusMonths(2)).place("다른 곳").build());
    em.flush();
    mergeService.recompute();
    reload();

    assertThat(mergedIntoOf(insta)).isNull();
  }

  @Test
  void 숨겨져_있던_대표에_재단이_새로_붙으면_모두_재단을_가리킴_사슬_없음() {
    Event first = instagram("합치기시험 사슬 공연");
    Event second = instagram("합치기시험 사슬 공연");
    mergeService.recompute();
    em.flush();
    assertThat(second.getMergedIntoId()).isEqualTo(first.getId());

    Event foundation = foundation(70004L, "합치기시험 사슬 공연");
    mergeService.recompute();
    reload();

    assertThat(mergedIntoOf(foundation)).isNull();
    assertThat(mergedIntoOf(first)).isEqualTo(foundation.getId());
    assertThat(mergedIntoOf(second)).isEqualTo(foundation.getId());
  }

  @Test
  void 대표는_항상_숨김_칸이_비어_있음() {
    foundation(70005L, "합치기시험 대표규칙 공연");
    instagram("합치기시험 대표규칙 공연");
    instagram("합치기시험 대표규칙 공연");
    instagram("합치기시험 별개 가상 전시");
    instagram("합치기시험 별개 가상 전시");

    mergeService.recompute();
    reload();

    List<Event> all = eventRepository.findAll();
    assertThat(all).allSatisfy(e -> {
      assertThat(e.getMergedIntoId()).isNotEqualTo(e.getId()); // 자기 자신 금지
      if (e.getMergedIntoId() != null) {
        Event representative = eventRepository.findById(e.getMergedIntoId()).orElseThrow();
        assertThat(representative.getMergedIntoId()).isNull(); // 사슬 금지
      }
    });
  }

  @Test
  void 재단이_2개_낀_묶음은_합치지_않고_충돌로_알려줌_숨겨둔_행도_해제() {
    Event foundationA = foundation(70006L, "합치기시험 충돌 공연");
    Event insta = instagram("합치기시험 충돌 공연");
    mergeService.recompute();
    em.flush();
    assertThat(insta.getMergedIntoId()).isEqualTo(foundationA.getId());

    Event foundationB = foundation(70007L, "합치기시험 충돌 공연");
    EventMergeResult result = mergeService.recompute();
    reload();

    assertThat(mergedIntoOf(insta)).isNull();
    assertThat(mergedIntoOf(foundationA)).isNull();
    assertThat(mergedIntoOf(foundationB)).isNull();
    assertThat(result.conflicts()).anySatisfy(c -> assertThat(c).extracting(EventMergeResult.Item::id)
        .contains(foundationA.getId(), foundationB.getId(), insta.getId()));
  }

  @Test
  void 개별_글이_모음_글보다_나중에_저장됐어도_대표() {
    Event roundup = instagram("합치기시험 모음개별 공연");
    Event single = instagram("합치기시험 모음개별 공연");
    Event roundupOther = instagram("합치기시험 전혀 다른 전시");
    InstagramMedia roundupPost = media("m-roundup");
    block(roundupPost, "k1", InstagramBlock.Status.READY, roundup);
    block(roundupPost, "k2", InstagramBlock.Status.READY, roundupOther);
    block(media("m-single"), "k1", InstagramBlock.Status.READY, single);

    mergeService.recompute();
    reload();

    assertThat(mergedIntoOf(single)).isNull();
    assertThat(mergedIntoOf(roundup)).isEqualTo(single.getId());
  }

  @Test
  void 행사가_아닌_블록은_모음_글_판단에_세지_않음() {
    Event onePost = instagram("합치기시험 공지섞인 공연");
    Event laterRoundup = instagram("합치기시험 공지섞인 공연");
    Event laterOther = instagram("합치기시험 또 다른 전시");
    InstagramMedia noticePost = media("m-notice");
    block(noticePost, "k1", InstagramBlock.Status.READY, onePost);
    block(noticePost, "k2", InstagramBlock.Status.SKIPPED, null); // 공지 → 행사 1개짜리 글
    InstagramMedia roundupPost = media("m-roundup2");
    block(roundupPost, "k1", InstagramBlock.Status.READY, laterRoundup);
    block(roundupPost, "k2", InstagramBlock.Status.NO_COORD, null); // 좌표만 못 찾은 행사 → 모음 글
    block(roundupPost, "k3", InstagramBlock.Status.READY, laterOther);

    mergeService.recompute();
    reload();

    assertThat(mergedIntoOf(onePost)).isNull();
    assertThat(mergedIntoOf(laterRoundup)).isEqualTo(onePost.getId());
  }

  @Test
  void 미리보기는_결과만_돌려주고_저장하지_않음() {
    Event foundation = foundation(70008L, "합치기시험 미리보기 공연");
    Event insta = instagram("합치기시험 미리보기 공연");

    EventMergeResult preview = mergeService.preview();
    reload();

    assertThat(mergedIntoOf(insta)).isNull();
    assertThat(preview.groups()).anySatisfy(g -> {
      assertThat(g.representative().id()).isEqualTo(foundation.getId());
      assertThat(g.representative().source()).isEqualTo(Event.SOURCE_SWCF);
      assertThat(g.hidden()).extracting(EventMergeResult.Item::id).containsExactly(insta.getId());
      assertThat(g.hidden().get(0).title()).isEqualTo("합치기시험 미리보기 공연");
    });
    assertThat(preview.hiddenCount()).isGreaterThanOrEqualTo(1);
    assertThat(preview.groupCount()).isEqualTo(preview.groups().size());
  }

  private Long mergedIntoOf(Event e) {
    return eventRepository.findById(e.getId()).orElseThrow().getMergedIntoId();
  }

  private void reload() {
    em.flush();
    em.clear();
  }

  private Event foundation(Long idx, String title) {
    return eventRepository.saveAndFlush(base(title).idx(idx).build());
  }

  private Event instagram(String title) {
    return eventRepository.saveAndFlush(base(title)
        .source(Event.SOURCE_INSTAGRAM).sourceKey("합치기시험#" + (++nextKey)).build());
  }

  private static Event.EventBuilder base(String title) {
    return Event.builder()
        .title(title).category("공연").latitude(37.1).longitude(126.9).place("합치기시험 아트홀")
        .pubDate(LocalDateTime.of(2031, 1, 1, 0, 0)).startDate(DAY).endDate(DAY);
  }

  private InstagramMedia media(String mediaId) {
    return mediaRepository.saveAndFlush(InstagramMedia.builder()
        .mediaId(mediaId).account("gasang_city").permalink("https://www.instagram.com/p/AAA/")
        .postedAt(LocalDateTime.of(2031, 1, 1, 0, 0)).collectedAt(LocalDateTime.of(2031, 1, 1, 0, 0)).build());
  }

  private void block(InstagramMedia media, String key, InstagramBlock.Status status, Event event) {
    InstagramBlock block = blockRepository.saveAndFlush(InstagramBlock.builder()
        .media(media).blockKey(key).status(status).updatedAt(LocalDateTime.of(2031, 1, 1, 0, 0)).build());
    block.update(status, null, event, LocalDateTime.of(2031, 1, 1, 0, 0));
    em.flush();
  }
}
