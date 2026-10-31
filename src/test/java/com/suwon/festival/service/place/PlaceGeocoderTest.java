package com.suwon.festival.service.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    // "가상홀 2층" → 줄여도 "가상홀" ≠ "가상아트홀" 이라 못 고름
    var notExact = new PlaceGeocoder(new FakeSearch(suwon("가상아트홀")).more()).geocode("가상홀 2층");
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

  // 검색어마다 다른 결과를 돌려주는 가짜 검색기 (정해 두지 않은 검색어는 결과 0건)
  static class MapSearch implements PlaceSearchClient {
    final List<String> queries = new ArrayList<>();
    final Map<String, List<PlaceCandidate>> answers = new HashMap<>();

    MapSearch on(String query, PlaceCandidate... result) {
      answers.put(query, List.of(result));
      return this;
    }

    @Override
    public Page search(String query) {
      queries.add(query);
      return new Page(answers.getOrDefault(query, List.of()), true);
    }
  }

  @Test
  void 방_층_이름이_붙어_못_찾으면_뒤_단어를_떼고_건물_이름으로_찾음() {
    MapSearch s = new MapSearch().on("가상시청", suwon("가상시청"));
    var r = new PlaceGeocoder(s).geocode("가상시청 본관 로비");
    assertThat(r.status()).isEqualTo(Status.FOUND);
    assertThat(r.place().name()).isEqualTo("가상시청");
    assertThat(s.queries).containsExactly("가상시청 본관 로비", "가상시청 본관", "가상시청");
  }

  @Test
  void 줄인_검색어는_이름이_정확히_같을_때만_인정() {
    // "가상공원" 으로 줄였는데 결과가 "가상공원 주차장" 하나뿐 → 다른 곳일 수 있어 안 찍음
    MapSearch s = new MapSearch().on("가상공원", suwon("가상공원 주차장"));
    var r = new PlaceGeocoder(s).geocode("가상공원 다목적체육관");
    assertThat(r.status()).isEqualTo(Status.NO_COORD);
  }

  @Test
  void 줄이는_횟수는_2번까지_3글자_미만_지역_이름이면_멈춤() {
    MapSearch s = new MapSearch().on("가상센터", suwon("가상센터"));
    // 단어 4개: 2번 줄여도 "가상센터 1층" 까지 → 3번째("가상센터")는 안 함
    assertThat(new PlaceGeocoder(s).geocode("가상센터 1층 대강당 앞").status()).isEqualTo(Status.NO_COORD);
    assertThat(s.queries).containsExactly("가상센터 1층 대강당 앞", "가상센터 1층 대강당", "가상센터 1층");

    MapSearch region = new MapSearch().on("가상동", suwon("가상동"));
    assertThat(new PlaceGeocoder(region).geocode("가상동 마당").status()).isEqualTo(Status.NO_COORD);
    assertThat(region.queries).containsExactly("가상동 마당"); // "가상동" 은 지역 이름 → 검색 안 함

    MapSearch tiny = new MapSearch().on("가상", suwon("가상"));
    new PlaceGeocoder(tiny).geocode("가상 홀");
    assertThat(tiny.queries).containsExactly("가상 홀"); // 2글자로는 줄이지 않음
  }

  @Test
  void 후보가_여러_개였으면_줄여서_다시_찾지_않음() {
    MapSearch s = new MapSearch().on("가상 홀 2관", suwon("가상 홀 2관"), suwon("가상홀2관"));
    var r = new PlaceGeocoder(s).geocode("가상 홀 2관");
    assertThat(r.status()).isEqualTo(Status.NO_COORD);
    assertThat(s.queries).containsExactly("가상 홀 2관");
  }

  @Test
  void 괄호_부연과_앞_라벨은_지우고_검색() {
    assertThat(PlaceGeocoder.tidy("가상센터 전시홀(1F)")).isEqualTo("가상센터 전시홀");
    assertThat(PlaceGeocoder.tidy("가상센터(1층 전시홀, 3층 컨벤션홀)")).isEqualTo("가상센터");
    assertThat(PlaceGeocoder.tidy("집결지: 가상공원 마당극장")).isEqualTo("가상공원 마당극장");
    assertThat(PlaceGeocoder.tidy("가상홀 (A) (B)")).isEqualTo("가상홀");
    assertThat(PlaceGeocoder.tidy(null)).isEmpty();

    // 괄호 속 쉼표 때문에 "여러 곳"으로 보지 않음
    MapSearch s = new MapSearch().on("가상센터", suwon("가상센터"));
    assertThat(new PlaceGeocoder(s).geocode("가상센터(1층 전시홀, 3층 컨벤션홀)").status()).isEqualTo(Status.FOUND);
    // 괄호 밖 쉼표는 여전히 여러 곳
    assertThat(new PlaceGeocoder(s).geocode("가상센터, 가상공원").status()).isEqualTo(Status.NO_COORD);
  }

  @Test
  void 줄여서_다시_찾다가_호출이_실패하면_ERROR() {
    PlaceSearchClient flaky = new PlaceSearchClient() {
      int n = 0;

      @Override
      public Page search(String query) {
        if (n++ == 0) return new Page(List.of(), true);
        throw new IllegalStateException("KakaoAK secret-key");
      }
    };
    var r = new PlaceGeocoder(flaky).geocode("가상센터 대강당");
    assertThat(r.status()).isEqualTo(Status.ERROR);
    assertThat(r.reason()).doesNotContain("secret");
  }
}
