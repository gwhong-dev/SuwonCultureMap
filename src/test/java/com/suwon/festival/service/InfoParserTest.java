package com.suwon.festival.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.suwon.festival.dto.xml.ContentInfoDetail;
import com.suwon.festival.service.InfoParser.TimeRange;

// 장소·시간 꺼내기 + 글자 정리 규칙 (예시는 실제 재단 데이터 141건에서 나온 값)
class InfoParserTest {

  private static List<ContentInfoDetail> details(String... labelAndContent) {
    List<ContentInfoDetail> list = new ArrayList<>();
    for (int i = 0; i < labelAndContent.length; i += 2) {
      ContentInfoDetail d = new ContentInfoDetail();
      d.infoItem = labelAndContent[i];
      d.infoContent = labelAndContent[i + 1];
      list.add(d);
    }
    return list;
  }

  // ===== 글자 정리 =====

  @Test
  void br_태그는_줄바꿈으로_바뀌고_끝에_붙은_br은_사라짐() {
    assertThat(InfoParser.clean("10:00~12:00(최대 2시간)<br>")).isEqualTo("10:00~12:00(최대 2시간)");
    assertThat(InfoParser.clean("14:00~21:00<br />(일요일은 20:00까지)")).isEqualTo("14:00~21:00\n(일요일은 20:00까지)");
  }

  @Test
  void 작품명_꺾쇠는_남기고_HTML_태그만_지움() {
    assertThat(InfoParser.clean("연극 &lt;파랑새&gt;")).isEqualTo("연극 <파랑새>");
    assertThat(InfoParser.clean("<span style=\"color:red\">무료</span>")).isEqualTo("무료");
    assertThat(InfoParser.clean("&#39;어울야락(夜樂)&#39;")).isEqualTo("'어울야락(夜樂)'");
    assertThat(InfoParser.clean(null)).isNull();
  }

  @Test
  void 영어_작품명_꺾쇠도_남김() {
    assertThat(InfoParser.clean("뮤지컬 <A Christmas Carol>")).isEqualTo("뮤지컬 <A Christmas Carol>");
    assertThat(InfoParser.clean("<I Love Suwon> 축제")).isEqualTo("<I Love Suwon> 축제");
    assertThat(InfoParser.clean("<a href=\"https://x.y\">링크</a>")).isEqualTo("링크");
  }

  @Test
  void 속성_있는_br과_주석도_정리() {
    assertThat(InfoParser.clean("10:00<br clear=\"all\">입장")).isEqualTo("10:00\n입장");
    assertThat(InfoParser.clean("무료<!-- 메모 -->")).isEqualTo("무료");
  }

  @Test
  void 너무_긴_장소와_시간은_칸_길이에_맞춰_자름() {
    String longPlace = "가".repeat(600);
    assertThat(InfoParser.findPlace(details("장소", longPlace))).hasSize(InfoParser.PLACE_MAX);
    assertThat(InfoParser.findTimeText(details("시간", "1".repeat(1200)))).hasSize(InfoParser.TIME_TEXT_MAX);
  }

  // ===== 장소·시간 꺼내기 =====

  @Test
  void 장소와_시간을_XML_쌍에서_꺼냄() {
    List<ContentInfoDetail> d = details(
        "문의처", "031-247-5615",
        " 장소 ", "정조테마공연장 대공연장<br>",
        "시간", "19:00~19:40");
    assertThat(InfoParser.findPlace(d)).isEqualTo("정조테마공연장 대공연장");
    assertThat(InfoParser.findTimeText(d)).isEqualTo("19:00~19:40");
  }

  @Test
  void 장소가_없거나_비어_있으면_주소_계열을_씀() {
    assertThat(InfoParser.findPlace(details("장소", "<br>", "주소/소재지", "수원시 팔달구 정조로 817")))
        .isEqualTo("수원시 팔달구 정조로 817");
    assertThat(InfoParser.findPlace(details("문의처", "031-000-0000"))).isNull();
    assertThat(InfoParser.findTimeText(null)).isNull();
  }

  // ===== 시간 해석: 확실한 1개만 =====

  @Test
  void 시작과_끝이_있는_형식() {
    assertThat(InfoParser.parseTime("19:00~19:40")).contains(new TimeRange(LocalTime.of(19, 0), LocalTime.of(19, 40)));
    assertThat(InfoParser.parseTime("10:00 ~ 17:00")).contains(new TimeRange(LocalTime.of(10, 0), LocalTime.of(17, 0)));
    assertThat(InfoParser.parseTime("16:30-18:00 ")).contains(new TimeRange(LocalTime.of(16, 30), LocalTime.of(18, 0)));
    assertThat(InfoParser.parseTime("14:00~15:00 (60분)")).contains(new TimeRange(LocalTime.of(14, 0), LocalTime.of(15, 0)));
    assertThat(InfoParser.parseTime("14:00~15:00(1시간)")).contains(new TimeRange(LocalTime.of(14, 0), LocalTime.of(15, 0)));
  }

  @Test
  void 시작만_있는_형식() {
    assertThat(InfoParser.parseTime("14:00")).contains(new TimeRange(LocalTime.of(14, 0), null));
    assertThat(InfoParser.parseTime("10:00~")).contains(new TimeRange(LocalTime.of(10, 0), null));
  }

  @Test
  void 시작과_길이만_있으면_길이로_끝_시간_계산() {
    assertThat(InfoParser.parseTime("14:00 (60분)")).contains(new TimeRange(LocalTime.of(14, 0), LocalTime.of(15, 0)));
    assertThat(InfoParser.parseTime("19:00(1시간)")).contains(new TimeRange(LocalTime.of(19, 0), LocalTime.of(20, 0)));
    // 자정을 넘기면 끝 시간은 모르는 것으로 (→ 캘린더는 기본 2시간)
    assertThat(InfoParser.parseTime("23:30 (60분)")).contains(new TimeRange(LocalTime.of(23, 30), null));
  }

  @Test
  void 전각_물결도_구분_기호로_인정() {
    assertThat(InfoParser.parseTime("19:00～20:00")).contains(new TimeRange(LocalTime.of(19, 0), LocalTime.of(20, 0)));
    assertThat(InfoParser.parseTime("19:00〜20:00")).contains(new TimeRange(LocalTime.of(19, 0), LocalTime.of(20, 0)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "14:00, 17:00", // 여러 회차
      "12:00 / 13:30",
      "1:00 / 14:00 / 16:30",
      "(토) 17:00~18:00 (일) 14:00~15:00 (60분)", // 요일별
      "12.24.(수) 19:30 ㅣ 12.25.(목) 14:00, 17:00", // 날짜별
      "17:00~18:40(인터미션 15분 포함)", // 부가설명
      "10:00~18:00  *17:00 입장 마감",
      "10:00~12:00(최대 2시간)",
      "14:00~21:00\n(일요일은 20:00까지)", // 여러 줄
      "프로그램 별 상이",
      "25:00", // 잘못된 시각
      "18:00~17:00", // 끝이 시작보다 빠름
      ""
  })
  void 확실하지_않으면_비워_둠(String text) {
    assertThat(InfoParser.parseTime(text)).isEmpty();
  }

  @Test
  void 시간이_없으면_비워_둠() {
    assertThat(InfoParser.parseTime(null)).isEmpty();
  }
}
