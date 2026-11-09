package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.suwon.festival.dto.SyncResult;
import com.suwon.festival.dto.xml.Coordinate;
import com.suwon.festival.dto.xml.EventDate;
import com.suwon.festival.dto.xml.EventItem;
import com.suwon.festival.dto.xml.PhotoInfo;
import com.suwon.festival.dto.xml.Photos;
import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;

// 재단 동기화가 인스타 행사(idx 없음)와 섞이지 않는지 — 가짜 재단 행사 목록을 직접 넣음 (실제 API 호출 없음)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class EventSyncServiceTest {

  @Autowired private EventSyncService eventSyncService;
  @Autowired private EventRepository eventRepository;

  private static EventItem item(Long idx, String title) {
    EventItem it = new EventItem();
    it.idx = idx;
    it.title = title;
    it.category = "공연";
    it.coordinate = new Coordinate();
    it.coordinate.lat = 127.01; // 재단 XML 은 lat/lng 이 뒤바뀌어 옴 (EventSyncService 참고)
    it.coordinate.lng = 37.28;
    it.pubDate = "2026-10-31 12:00:00";
    return it;
  }

  @Test
  void 재단_번호가_없는_행사는_건너뛰고_인스타_행사를_덮어쓰지_않음() {
    Event insta1 = eventRepository.saveAndFlush(instagramEvent("m9#가상1"));
    eventRepository.saveAndFlush(instagramEvent("m9#가상2")); // idx 가 null 인 행이 2개 → findByIdx(null) 이면 터졌을 상황

    SyncResult result = eventSyncService.syncItems(List.of(item(null, "번호 없는 재단 행사"), item(880001L, "가상 재단 행사")));

    assertThat(result.saved()).isEqualTo(1); // 번호 있는 것만 저장
    assertThat(eventRepository.findById(insta1.getId())).get()
        .satisfies(e -> {
          assertThat(e.getTitle()).isEqualTo("가상 인스타 행사"); // 덮어쓰이지 않음
          assertThat(e.getSource()).isEqualTo(Event.SOURCE_INSTAGRAM);
        });
    assertThat(eventRepository.findByIdx(880001L)).get()
        .satisfies(e -> assertThat(e.getSource()).isEqualTo(Event.SOURCE_SWCF));
  }

  @Test
  void 형식이_틀린_행은_건너뛰고_뒤_행사는_저장() {
    EventItem badPubDate = item(880101L, "게시일 형식 오류");
    badPubDate.pubDate = "2026/10/31";
    EventItem badPeriod = item(880102L, "기간 형식 오류");
    badPeriod.eventDate = new EventDate();
    badPeriod.eventDate.sdate = "2026-13-01";
    badPeriod.eventDate.edate = "2026-13-02";

    SyncResult result = eventSyncService.syncItems(List.of(badPubDate, badPeriod, item(880103L, "정상 행사")));

    assertThat(result.saved()).isEqualTo(1);
    assertThat(result.skipped()).isEqualTo(2);
    assertThat(eventRepository.findByIdx(880103L)).isPresent();
    assertThat(eventRepository.findByIdx(880101L)).isEmpty();
  }

  @Test
  void 모든_행이_실패하면_성공으로_넘기지_않음() {
    EventItem a = item(880201L, "오류 1");
    a.pubDate = "잘못된 값";
    EventItem b = item(880202L, "오류 2");
    b.pubDate = "잘못된 값";

    assertThatThrownBy(() -> eventSyncService.syncItems(List.of(a, b)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("2건 모두");
  }

  @Test
  void 행사_0건_응답은_빈_목록() throws Exception {
    String xml = "<rss><channel><title>수원문화재단</title></channel></rss>";
    assertThat(EventSyncService.parseItems(xml)).isEmpty();
    assertThat(eventSyncService.syncItems(List.of())).isEqualTo(new SyncResult(0, 0, 0));
  }

  @Test
  void 행사_목록_모양이_아닌_응답은_이유를_알_수_있게_실패() {
    String maintenance = "<html><body><p>서비스 점검 중입니다</p></body></html>";
    assertThatThrownBy(() -> EventSyncService.parseItems(maintenance))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("channel");
  }

  @Test
  void 이미지_주소는_https_로_바꿔_저장() {
    assertThat(EventSyncService.toHttps("http://www.swcf.or.kr/a.jpg")).isEqualTo("https://www.swcf.or.kr/a.jpg");
    assertThat(EventSyncService.toHttps("HTTP://www.swcf.or.kr/a.jpg")).isEqualTo("https://www.swcf.or.kr/a.jpg");
    assertThat(EventSyncService.toHttps("https://www.swcf.or.kr/a.jpg")).isEqualTo("https://www.swcf.or.kr/a.jpg");
    assertThat(EventSyncService.toHttps(null)).isNull();

    eventSyncService.syncItems(List.of(withPhoto(item(880301L, "이미지 행사"), "http://www.swcf.or.kr/a.jpg")));

    assertThat(eventRepository.findByIdx(880301L)).get()
        .satisfies(e -> assertThat(e.getImageUrl()).isEqualTo("https://www.swcf.or.kr/a.jpg"));
  }

  @Test
  void 예전에_http_로_저장된_이미지는_다음_동기화에_https_로_갱신() {
    EventItem first = withPhoto(item(880302L, "옛 이미지 행사"), "http://www.swcf.or.kr/b.jpg");
    eventSyncService.syncItems(List.of(first));
    Event saved = eventRepository.findByIdx(880302L).orElseThrow();
    saved.updateFrom(Event.builder().idx(880302L).title(saved.getTitle()).category(saved.getCategory())
        .latitude(saved.getLatitude()).longitude(saved.getLongitude())
        .imageUrl("http://www.swcf.or.kr/b.jpg") // 변환 전에 저장된 상태를 흉내
        .info(saved.getInfo()).pubDate(saved.getPubDate()).startDate(saved.getStartDate())
        .endDate(saved.getEndDate()).link(saved.getLink()).place(saved.getPlace()).timeText(saved.getTimeText())
        .build());
    eventRepository.saveAndFlush(saved);

    SyncResult result = eventSyncService.syncItems(List.of(first)); // pubDate 그대로 — 이미지 주소만 다름

    assertThat(result.updated()).isEqualTo(1);
    assertThat(eventRepository.findByIdx(880302L)).get()
        .satisfies(e -> assertThat(e.getImageUrl()).isEqualTo("https://www.swcf.or.kr/b.jpg"));
  }

  @Test
  void 요금_문의처_주최를_저장하고_칸이_비어_있던_행사는_다음_동기화에_채움() {
    EventItem it = withDetails(item(880303L, "분해 행사"),
        "이용료", "무료", "문의처", "031-290-3600", "주최/주관", "수원문화재단", "장소", "수원화성");
    eventSyncService.syncItems(List.of(it));
    Event saved = eventRepository.findByIdx(880303L).orElseThrow();
    assertThat(saved.getFee()).isEqualTo("무료");
    assertThat(saved.getContact()).isEqualTo("031-290-3600");
    assertThat(saved.getHost()).isEqualTo("수원문화재단");

    // V7 전에 저장된 행(요금 · 문의 · 주최 칸이 빈 상태)을 흉내 → pubDate 가 같아도 다음 동기화에 채워야 함
    saved.updateFrom(Event.builder().idx(880303L).title(saved.getTitle()).category(saved.getCategory())
        .latitude(saved.getLatitude()).longitude(saved.getLongitude()).imageUrl(saved.getImageUrl())
        .info(saved.getInfo()).pubDate(saved.getPubDate()).startDate(saved.getStartDate())
        .endDate(saved.getEndDate()).link(saved.getLink()).place(saved.getPlace()).timeText(saved.getTimeText())
        .build());
    eventRepository.saveAndFlush(saved);
    assertThat(eventRepository.findByIdx(880303L).orElseThrow().getFee()).isNull();

    SyncResult result = eventSyncService.syncItems(List.of(it));

    assertThat(result.updated()).isEqualTo(1);
    assertThat(eventRepository.findByIdx(880303L)).get().satisfies(e -> {
      assertThat(e.getFee()).isEqualTo("무료");
      assertThat(e.getContact()).isEqualTo("031-290-3600");
      assertThat(e.getHost()).isEqualTo("수원문화재단");
    });
  }

  private static EventItem withDetails(EventItem it, String... labelAndContent) {
    it.contentInfo = new com.suwon.festival.dto.xml.ContentInfo();
    it.contentInfo.contentInfoDetail = new java.util.ArrayList<>();
    for (int i = 0; i < labelAndContent.length; i += 2) {
      com.suwon.festival.dto.xml.ContentInfoDetail d = new com.suwon.festival.dto.xml.ContentInfoDetail();
      d.infoItem = labelAndContent[i];
      d.infoContent = labelAndContent[i + 1];
      it.contentInfo.contentInfoDetail.add(d);
    }
    return it;
  }

  private static EventItem withPhoto(EventItem it, String url) {
    PhotoInfo photo = new PhotoInfo();
    photo.photoUrl = url;
    it.photos = new Photos();
    it.photos.photoInfo = List.of(photo);
    return it;
  }

  private static Event instagramEvent(String sourceKey) {
    return Event.builder()
        .title("가상 인스타 행사").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 10, 30, 18, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5))
        .source(Event.SOURCE_INSTAGRAM).sourceKey(sourceKey)
        .build();
  }
}
