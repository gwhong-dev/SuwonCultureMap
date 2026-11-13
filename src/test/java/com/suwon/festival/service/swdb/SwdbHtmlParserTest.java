package com.suwon.festival.service.swdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

// 수원문화DB 화면 읽기 — 실제 화면(11/13)과 같은 모양의 가짜 HTML (실제 등록자 이름 · 번호는 넣지 않음)
class SwdbHtmlParserTest {

  static String listItem(String reqIdx, String categoryClass, String category, String title, String address,
      String date) {
    return """
        <li>
          <div class="img img4x3">
              <a class="jibomtv-img-area" href="/swdb/?p=15_view&amp;reqIdx=%1$s&page=1">
                <img src="/swdb/_File/bbs//0/%1$s.png" alt="">
              </a>
          </div>
          <div class="txt-box">
            <p class="listcategory"><span  class="%2$s">%3$s</span></p>
            <a href="/swdb/?p=15_view&amp;reqIdx=%1$s&page=1">
              <strong>%4$s</strong>
              <p class="listinfobox"><span class="listaddressbox"><i class="icon_location"></i>%5$s</span></p>
              <p class="listinfobox"><span class="listdatebox"><i class="icon_calendar"></i>%6$s</span></p>
              <p class="longcontentbox">소개글 &lt;원문&gt; — 저장하지 않음</p>
            </a>
          </div>
        </li>
        """.formatted(reqIdx, categoryClass, category, title, address, date);
  }

  static String listPage(String... items) {
    return "<html><body><ul class=\"gnb\"><li><a href=\"/swdb/?p=41&reqIdx=202603231511108508\">메뉴</a></li></ul>"
        + "<ul class=\"video-notice-list-area\">" + String.join("", items) + "</ul></body></html>";
  }

  static String detailPage(String category, String when, String place) {
    return """
        <table summary="행사 상세보기"><tbody>
        <tr>
          <th scope='row'>활동분야</th>
          <td class='r_none'>%s</td>
        </tr>
        <tr>
          <th scope='row'>행사일시</th>
          <td class='r_none'>%s</td>
        </tr>
        <tr>
          <th scope='row'>주관단체 및 개인</th>
          <td class='r_none'>가상 개인</td>
        </tr>
        <tr>
          <th scope="row">문의처</th>
          <td class='r_none'>010-0000-0000</td>
        </tr>
        <tr>
          <th scope='row'>행사장소</th>
          <td class='r_none'>%s</td>
        </tr>
        </tbody></table>
        <div class="schedule_box">소개글 — 저장하지 않음</div>
        """.formatted(category, when, place);
  }

  @Test
  void 목록에서_키_분류_제목_주소_날짜_사진을_읽음() {
    String html = listPage(
        listItem("202611061144388032", "listcategory1", "전시", "가상 전시 &lt;빛&gt;", "경기 수원시 팔달구 인계동 1127",
            "2026-10-08"),
        listItem("202611041917216321", "listcategory2", "공연", "가상 연주회", "경기 수원시 영통구 반달로7번길 40",
            "2026-11-28"));

    List<SwdbHtmlParser.ListItem> items = SwdbHtmlParser.parseList(html);

    assertThat(items).hasSize(2); // 메뉴의 reqIdx 링크는 목록 칸이 아니라 빠짐
    SwdbHtmlParser.ListItem first = items.get(0);
    assertThat(first.reqIdx()).isEqualTo("202611061144388032");
    assertThat(first.category()).isEqualTo("전시");
    assertThat(first.title()).isEqualTo("가상 전시 <빛>");
    assertThat(first.address()).isEqualTo("경기 수원시 팔달구 인계동 1127");
    assertThat(first.listDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    assertThat(first.imagePath()).isEqualTo("/swdb/_File/bbs//0/202611061144388032.png");
    assertThat(items.get(1).category()).isEqualTo("공연");
  }

  @Test
  void 목록_구조가_바뀌면_빈_목록() {
    assertThat(SwdbHtmlParser.parseList("<html><body>점검 중</body></html>")).isEmpty();
    assertThat(SwdbHtmlParser.parseList(null)).isEmpty();
  }

  @Test
  void 상세에서_분류_기간_시간_장소만_읽음() {
    SwdbHtmlParser.Detail d = SwdbHtmlParser.parseDetail(detailPage("전시",
        "2026-10-08일 10:30부터 ~ 2026-10-20일 20:30까지", "경기 수원시 팔달구 인계동 1127"));

    assertThat(d.category()).isEqualTo("전시");
    assertThat(d.startDate()).isEqualTo(LocalDate.of(2026, 10, 8));
    assertThat(d.endDate()).isEqualTo(LocalDate.of(2026, 10, 20));
    assertThat(d.timeText()).isEqualTo("10:30~20:30");
    assertThat(d.place()).isEqualTo("경기 수원시 팔달구 인계동 1127");
  }

  @Test
  void 시간_칸에_이미_물결이_있으면_그대로() {
    SwdbHtmlParser.Detail d = SwdbHtmlParser.parseDetail(detailPage("공연",
        "2026-10-05일 15:00~17:0부터 ~ 2026-10-16일 15:00~17:0까지", "광교호수공원"));

    assertThat(d.timeText()).isEqualTo("15:00~17:0");
  }

  @Test
  void 종료일이_비면_시작일_하루() {
    SwdbHtmlParser.Detail d = SwdbHtmlParser.parseDetail(detailPage("공연", "2026-06-27일 15:00부터 ~ 일 까지", "가상홀"));

    assertThat(d.startDate()).isEqualTo(LocalDate.of(2026, 6, 27));
    assertThat(d.endDate()).isEqualTo(LocalDate.of(2026, 6, 27));
    assertThat(d.timeText()).isEqualTo("15:00");
  }

  @Test
  void 행사일시를_못_읽으면_null() {
    assertThat(SwdbHtmlParser.parseDetail(detailPage("공연", "추후 공지", "어딘가"))).isNull();
    assertThat(SwdbHtmlParser.parseDetail("<html>없음</html>")).isNull();
  }

  @Test
  void 키_앞_14자리는_등록_시각() {
    assertThat(SwdbHtmlParser.registeredAt("202611061144388032")).isEqualTo(LocalDateTime.of(2026, 11, 6, 11, 44, 38));
    assertThat(SwdbHtmlParser.registeredAt("2026")).isNull();
    assertThat(SwdbHtmlParser.registeredAt("20261399999999")).isNull();
  }
}
