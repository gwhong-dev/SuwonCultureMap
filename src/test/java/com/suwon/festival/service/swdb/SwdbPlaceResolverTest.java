package com.suwon.festival.service.swdb;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.place.AddressSearchClient;
import com.suwon.festival.service.place.PlaceCandidate;
import com.suwon.festival.service.place.PlaceGeocoder;
import com.suwon.festival.service.place.PlaceSearchClient;
import com.suwon.festival.service.swdb.SwdbPlaceResolver.Status;

// 수원문화DB 장소 → 좌표 · 카드 장소 줄 · 상세 안내 (가짜 검색, 실제 카카오 호출 없음)
class SwdbPlaceResolverTest {

  // 정해 둔 검색어에만 결과를 주는 가짜 장소 검색 (이름 = 검색어 → PlaceGeocoder 이름 확인 통과)
  static class FakePlaces implements PlaceSearchClient {
    final Map<String, PlaceCandidate> known = new HashMap<>();
    final List<String> queries = new ArrayList<>();
    boolean fail;

    FakePlaces add(String name, double lat, double lng) {
      known.put(name, new PlaceCandidate(name, "경기 수원시 팔달구 가상동 1", null, lat, lng));
      return this;
    }

    @Override
    public Page search(String query) {
      queries.add(query);
      if (fail) throw new IllegalStateException("가상 실패");
      PlaceCandidate c = known.get(query);
      return new Page(c == null ? List.of() : List.of(c), true);
    }
  }

  static class FakeAddresses implements AddressSearchClient {
    final Map<String, PlaceCandidate> known = new HashMap<>();
    final List<String> queries = new ArrayList<>();

    FakeAddresses add(String address, double lat, double lng) {
      known.put(address, new PlaceCandidate("경기 " + address, "경기 " + address, null, lat, lng));
      return this;
    }

    @Override
    public List<PlaceCandidate> search(String address) {
      queries.add(address);
      PlaceCandidate c = known.get(address);
      return c == null ? List.of() : List.of(c);
    }
  }

  final FakePlaces places = new FakePlaces();
  final FakeAddresses addresses = new FakeAddresses();
  final SwdbPlaceResolver resolver = new SwdbPlaceResolver(addresses, new PlaceGeocoder(places));

  @Test
  void 주소_1개는_주소_검색으로_찾고_카드엔_경기를_뗀_원문() {
    addresses.add("수원시 영통구 반달로7번길 40", 37.25, 127.07);

    var r = resolver.resolve("경기 수원시 영통구 반달로7번길 40 평익칼라스위트 407-1호 에보니홀");

    assertThat(r.status()).isEqualTo(Status.FOUND);
    assertThat(r.lat()).isEqualTo(37.25);
    assertThat(r.place()).isEqualTo("수원시 영통구 반달로7번길 40 평익칼라스위트 407-1호 에보니홀");
    assertThat(r.note()).isNull();
    assertThat(addresses.queries).containsExactly("수원시 영통구 반달로7번길 40");
  }

  @Test
  void 주소_뒤_층_조각은_여러_장소가_아님() {
    addresses.add("수원시 팔달구 세지로421번길 37-1", 37.28, 127.01);

    var r = resolver.resolve("영공 (경기 수원시 팔달구 세지로421번길 37-1, 2층)");

    assertThat(r.status()).isEqualTo(Status.FOUND);
    assertThat(r.note()).isNull();
  }

  @Test
  void 지번_주소도_읽음() {
    addresses.add("수원시 영통구 하동 1023", 37.28, 127.06);

    var r = resolver.resolve("경기 수원시 영통구 하동 1023   광교호수공원");

    assertThat(r.status()).isEqualTo(Status.FOUND);
  }

  @Test
  void 수원_밖_주소는_OUTSIDE_검색_안_함() {
    var r = resolver.resolve("경기 화성시 동탄대로 1 가상홀");

    assertThat(r.status()).isEqualTo(Status.OUTSIDE);
    assertThat(addresses.queries).isEmpty();
    assertThat(places.queries).isEmpty();
  }

  @Test
  void 수원시립_같은_이름은_수원_밖이_아님() {
    places.add("수원시립미술관", 37.28, 127.01);

    assertThat(resolver.resolve("수원시립미술관").status()).isEqualTo(Status.FOUND);
  }

  @Test
  void 여러_장소는_찾은_첫_장소에_핀_카드엔_외_N곳_상세엔_안내() {
    places.add("푸른지대창작샘터", 37.27, 126.98);
    String raw = "111CM, 푸른지대창작샘터, 정조테마공연장, 지혜샘어린이도서관";

    var r = resolver.resolve(raw);

    assertThat(r.status()).isEqualTo(Status.FOUND);
    assertThat(r.lat()).isEqualTo(37.27);
    assertThat(r.place()).isEqualTo("여러 장소 · 111CM 외 3곳");
    assertThat(r.note()).startsWith(SwdbPlaceResolver.MULTI_NOTE).contains(raw);
  }

  @Test
  void 날짜가_붙은_여러_장소도_나눔() {
    places.add("수원 신성초", 37.29, 127.0);

    var r = resolver.resolve("7/9 수원 신곡초(1층 미래관), 7/10 수원 신성초(4층 다목적실), 7/22 수원 영화초(3층)");

    assertThat(r.status()).isEqualTo(Status.FOUND);
    assertThat(r.place()).isEqualTo("여러 장소 · 수원 신곡초 외 2곳");
  }

  @Test
  void 주소_뒤에_장소가_여러_개면_여러_장소() {
    addresses.add("수원시 권선구 칠보로 169", 37.27, 126.95);

    var r = resolver.resolve("경기 수원시 권선구 칠보로 169 , 호매실도서관, 수원시립미술관, 광교");

    assertThat(r.status()).isEqualTo(Status.FOUND);
    assertThat(r.place()).isEqualTo("여러 장소 · 호매실도서관 외 2곳");
    assertThat(r.note()).startsWith(SwdbPlaceResolver.MULTI_NOTE);
  }

  @Test
  void 일대는_기준_장소에_핀_카드엔_원문() {
    places.add("수원역", 37.266, 127.0);

    var r = resolver.resolve("수원역 일대(이동형 공연 예정)");

    assertThat(r.status()).isEqualTo(Status.FOUND);
    assertThat(r.lat()).isEqualTo(37.266);
    assertThat(r.place()).isEqualTo("수원역 일대(이동형 공연 예정)");
    assertThat(r.note()).startsWith(SwdbPlaceResolver.WIDE_NOTE);
    assertThat(places.queries).containsExactly("수원역");
  }

  @Test
  void 못_찾으면_NO_COORD() {
    assertThat(resolver.resolve("어딘가 모르는 곳").status()).isEqualTo(Status.NO_COORD);
    assertThat(resolver.resolve(" ").status()).isEqualTo(Status.NO_COORD);
    assertThat(resolver.resolve(null).status()).isEqualTo(Status.NO_COORD);
  }

  @Test
  void 검색_호출이_실패하면_ERROR() {
    places.fail = true;

    assertThat(resolver.resolve("가상 아트홀").status()).isEqualTo(Status.ERROR);
  }
}
