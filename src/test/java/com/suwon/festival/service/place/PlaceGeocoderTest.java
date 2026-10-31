package com.suwon.festival.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.place.PlaceGeocoder.Status;

// 장소 → 좌표 규칙 — 가짜 검색 결과 (실제 카카오 호출 없음)
class PlaceGeocoderTest {

  // 받은 검색어를 기록하고, 정해 둔 결과를 돌려주는 가짜 검색기
  static class FakeSearch implements PlaceSearchClient {
    final List<String> queries = new ArrayList<>();
    final List<PlaceCandidate> answer;
    boolean complete = true; // 다음 페이지 없음

    FakeSearch(PlaceCandidate... answer) {
      this.answer = List.of(answer);
    }

    FakeSearch more() {
      complete = false;
      return this;
    }

    @Override
    public Page search(String query) {
      queries.add(query);
      return new Page(answer, complete);
    }
  }

  static PlaceCandidate suwon(String name) {
    return new PlaceCandidate(name, "경기 수원시 팔달구 가상동 1", "경기 수원시 팔달구 가상로 1", 37.28, 127.01);
  }

  static PlaceCandidate seoul(String name) {
    return new PlaceCandidate(name, "서울 중구 가상동 1", null, 37.56, 126.98);
  }

  @Test
  void 수원_안_1건이면_FOUND() {
    FakeSearch s = new FakeSearch(suwon("가상 아트홀"), seoul("가상 아트홀"));
    var r = new PlaceGeocoder(s).geocode("  가상   아트홀 ");
    assertThat(r.status()).isEqualTo(Status.FOUND);
    assertThat(r.place().lat()).isEqualTo(37.28);
    assertThat(s.queries).containsExactly("가상 아트홀"); // 공백 정리 후 검색
  }

  @Test
  void 수원_밖_결과만_있으면_NO_COORD() {
    var r = new PlaceGeocoder(new FakeSearch(seoul("가상 아트홀"))).geocode("가상 아트홀");
    assertThat(r.status()).isEqualTo(Status.NO_COORD);
    assertThat(r.reason()).contains("수원");
  }

  @Test
  void 주소는_수원인데_좌표가_범위_밖이면_버림() {
    var odd = new PlaceCandidate("가상 아트홀", "경기 수원시 팔달구 가상동 1", null, 0.0, 0.0);
    assertThat(PlaceGeocoder.inSuwon(odd)).isFalse();
  }

  @Test
  void 수원_비슷한_주소는_수원으로_보지_않음() {
    // "수원시" 로 시작하지 않는 다른 시의 "…수원…" 주소
    var other = new PlaceCandidate("가상", "경기 화성시 가상수원로 1", null, 37.25, 127.00);
    assertThat(PlaceGeocoder.inSuwon(other)).isFalse();
  }

  @Test
  void 후보가_여러_개면_이름이_정확히_같은_1건만_FOUND() {
    var exactOne = new PlaceGeocoder(new FakeSearch(suwon("가상 공원 주차장"), suwon("가상공원"))).geocode("가상 공원");
    assertThat(exactOne.status()).isEqualTo(Status.FOUND);
    assertThat(exactOne.place().name()).isEqualTo("가상공원");

    var ambiguous = new PlaceGeocoder(new FakeSearch(suwon("가상 공원"), suwon("가상공원"))).geocode("가상 공원");
    assertThat(ambiguous.status()).isEqualTo(Status.NO_COORD);
    assertThat(ambiguous.reason()).contains("여러");
  }

  @Test
  void 범위가_넓은_말이나_여러_장소는_검색하지_않음() {
    FakeSearch s = new FakeSearch(suwon("가상동"));
    PlaceGeocoder g = new PlaceGeocoder(s);
    assertThat(g.geocode("가상동 일대").status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode("가상 광장, 가상 공원").status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode("가상 광장 및 가상 공원").status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode("장소 미정").status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode(null).status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode(" ").status()).isEqualTo(Status.NO_COORD);
    assertThat(s.queries).isEmpty(); // 호출 수(한도) 아끼기
  }

  @Test
  void 후보가_1건이어도_이름이_다르면_NO_COORD() {
    // 검색어보다 긴 다른 곳("○○ 공방거리")이 1건 나와도 찍지 않음
    var r = new PlaceGeocoder(new FakeSearch(suwon("가상 광장 공방거리"))).geocode("가상 광장");
    assertThat(r.status()).isEqualTo(Status.NO_COORD);
    assertThat(r.reason()).contains("이름");
  }

  @Test
  void 검색어가_결과_이름을_품으면_FOUND_짧은_이름은_불인정() {
    var floor = new PlaceGeocoder(new FakeSearch(suwon("가상아트홀"))).geocode("가상 아트홀 2층 소극장");
    assertThat(floor.status()).isEqualTo(Status.FOUND);
    var shortName = new PlaceGeocoder(new FakeSearch(suwon("공원"))).geocode("가상 시민 공원");
    assertThat(shortName.status()).isEqualTo(Status.NO_COORD);
  }

  @Test
  void 결과가_다음_페이지에_더_있으면_정확히_같은_이름만_FOUND() {
    var notExact = new PlaceGeocoder(new FakeSearch(suwon("가상아트홀")).more()).geocode("가상 아트홀 2층");
    assertThat(notExact.status()).isEqualTo(Status.NO_COORD);
    assertThat(notExact.reason()).contains("더 있어");
    var exact = new PlaceGeocoder(new FakeSearch(suwon("가상 아트홀")).more()).geocode("가상 아트홀");
    assertThat(exact.status()).isEqualTo(Status.FOUND);
  }

  @Test
  void 동_구_시_이름뿐이면_검색하지_않음() {
    FakeSearch s = new FakeSearch(suwon("가상동"));
    PlaceGeocoder g = new PlaceGeocoder(s);
    assertThat(g.geocode("가상동").status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode("가상구").status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode("가상1동").status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode("가상 시내").status()).isEqualTo(Status.NO_COORD);
    assertThat(g.geocode("관내 도서관").status()).isEqualTo(Status.NO_COORD);
    assertThat(s.queries).isEmpty();
  }

  @Test
  void 검색_실패는_ERROR_예외_내용은_남기지_않음() {
    PlaceSearchClient broken = q -> {
      throw new IllegalStateException("KakaoAK secret-key");
    };
    var r = new PlaceGeocoder(broken).geocode("가상 아트홀");
    assertThat(r.status()).isEqualTo(Status.ERROR);
    assertThat(r.reason()).doesNotContain("secret");
  }
}
