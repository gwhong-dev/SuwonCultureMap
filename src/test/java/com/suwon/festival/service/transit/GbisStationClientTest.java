package com.suwon.festival.service.transit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

// 경기도 정류소 API 호출 모양 — 가짜 서버(MockRestServiceServer)로 확인, 실제 호출 없음
// 응답 견본은 11/11 실제 호출 결과(화성행궁 광장 좌표)를 줄인 것
class GbisStationClientTest {

  private static final String KEY = "test-gbis-key";

  private RestTemplate restTemplate;
  private MockRestServiceServer server;
  private GbisStationClient client;

  @BeforeEach
  void setUp() {
    restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    client = new GbisStationClient(restTemplate, KEY);
  }

  @Test
  void 주변정류소_x는_경도_y는_위도_가까운_순_번호_앞공백_제거() {
    server.expect(requestTo(Matchers.startsWith(GbisStationClient.AROUND_URL)))
        .andExpect(method(HttpMethod.GET))
        .andExpect(request -> {
          var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
          assertThat(params.getFirst("x")).isEqualTo("127.01439");
          assertThat(params.getFirst("y")).isEqualTo("37.28188");
          assertThat(params.getFirst("format")).isEqualTo("json");
          assertThat(params.getFirst("serviceKey")).isEqualTo(KEY);
        })
        .andRespond(withSuccess("""
            {"response":{"comMsgHeader":"","msgHeader":{"resultCode":0,"resultMessage":"정상적으로 처리되었습니다."},
             "msgBody":{"busStationAroundList":[
              {"centerYn":"N","mobileNo":" 03050","regionName":"수원","stationId":202000231,"stationName":"팔달구청.화성행궁.남문시장","x":127.0163333,"y":37.28375,"distance":266},
              {"centerYn":"N","mobileNo":" 03058","regionName":"수원","stationId":202000248,"stationName":"화성행궁.종로","x":127.0173333,"y":37.2819,"distance":245}
             ]}}}
            """, MediaType.APPLICATION_JSON));

    List<GbisStationClient.Station> stations = client.findAround(37.28188, 127.01439);

    server.verify();
    assertThat(stations).extracting(GbisStationClient.Station::name)
        .containsExactly("화성행궁.종로", "팔달구청.화성행궁.남문시장");
    GbisStationClient.Station first = stations.get(0);
    assertThat(first.stationId()).isEqualTo(202000248L);
    assertThat(first.mobileNo()).isEqualTo("03058");
    assertThat(first.distanceMeters()).isEqualTo(245);
    assertThat(first.latitude()).isEqualTo(37.2819);
    assertThat(first.longitude()).isEqualTo(127.0173333);
    assertThat(first.centerLane()).isFalse();
  }

  @Test
  void 한_건이면_배열이_아닌_객체로_와도_받음() {
    server.expect(requestTo(Matchers.startsWith(GbisStationClient.AROUND_URL)))
        .andRespond(withSuccess("""
            {"response":{"msgHeader":{"resultCode":0},"msgBody":{"busStationAroundList":
              {"centerYn":"Y","mobileNo":"03139","stationId":202000252,"stationName":"화성행궁","x":127.01,"y":37.28,"distance":297}}}}
            """, MediaType.APPLICATION_JSON));

    List<GbisStationClient.Station> stations = client.findAround(37.28, 127.01);

    assertThat(stations).hasSize(1);
    assertThat(stations.get(0).centerLane()).isTrue();
  }

  @Test
  void 결과없음_resultCode_4_는_빈_목록() {
    server.expect(requestTo(Matchers.startsWith(GbisStationClient.AROUND_URL)))
        .andRespond(withSuccess("""
            {"response":{"comMsgHeader":"","msgHeader":{"resultCode":4,"resultMessage":"결과가 존재하지 않습니다."}}}
            """, MediaType.APPLICATION_JSON));

    assertThat(client.findAround(37.0, 126.5)).isEmpty();
  }

  @Test
  void 경유노선_버스번호가_숫자로_와도_글자로() {
    server.expect(requestTo(Matchers.startsWith(GbisStationClient.VIA_ROUTE_URL)))
        .andExpect(request -> {
          var params = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams();
          assertThat(params.getFirst("stationId")).isEqualTo("202000248");
        })
        .andRespond(withSuccess("""
            {"response":{"msgHeader":{"resultCode":0},"msgBody":{"busRouteList":[
              {"regionName":"화성","routeDestName":"수원대학교","routeId":234000015,"routeName":1007,"routeTypeCd":11,"routeTypeName":"직행좌석형시내버스"},
              {"regionName":"수원","routeDestName":"권선동","routeId":234001759,"routeName":"60-1","routeTypeCd":13,"routeTypeName":"일반형시내버스"}
            ]}}}
            """, MediaType.APPLICATION_JSON));

    List<GbisStationClient.Route> routes = client.findViaRoutes(202000248L);

    assertThat(routes).containsExactly(
        new GbisStationClient.Route(234000015L, "1007", "직행좌석형시내버스", "수원대학교"),
        new GbisStationClient.Route(234001759L, "60-1", "일반형시내버스", "권선동"));
  }

  @Test
  void 키의_특수문자는_한_번만_인코딩() {
    GbisStationClient special = new GbisStationClient(restTemplate, "a+b/c=");
    server.expect(requestTo(Matchers.containsString("serviceKey=a%2Bb%2Fc%3D")))
        .andRespond(withSuccess("{\"response\":{\"msgHeader\":{\"resultCode\":4}}}", MediaType.APPLICATION_JSON));

    assertThat(special.findAround(37.28, 127.01)).isEmpty();
    server.verify();
  }

  @Test
  void 키가_비어_있으면_호출하지_않고_빈_목록() {
    GbisStationClient noKey = new GbisStationClient(restTemplate, "  ");

    assertThat(noKey.isConfigured()).isFalse();
    assertThat(noKey.findAround(37.28, 127.01)).isEmpty();
    assertThat(noKey.findViaRoutes(1L)).isEmpty();
    server.verify(); // 기대한 요청 0개 → 아무 요청도 안 감
  }

  @Test
  void 키_미등록_403_은_오류코드만_남기고_키는_안_남김() {
    server.expect(requestTo(Matchers.startsWith(GbisStationClient.AROUND_URL)))
        .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
            {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{"errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR","returnReasonCode":"30"}}}
            """));

    assertThatThrownBy(() -> client.findAround(37.28, 127.01))
        .isInstanceOf(GbisException.class)
        .hasMessageContaining("HTTP 403")
        .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
        .hasMessageNotContaining(KEY)
        .hasNoCause();
  }

  @Test
  void 연결_실패_메시지에도_키가_든_주소를_남기지_않음() {
    server.expect(requestTo(Matchers.startsWith(GbisStationClient.AROUND_URL)))
        .andRespond(withException(new IOException("Read timed out")));

    assertThatThrownBy(() -> client.findAround(37.28, 127.01))
        .isInstanceOf(GbisException.class)
        .hasMessageNotContaining(KEY)
        .hasMessageNotContaining("apis.data.go.kr")
        .hasNoCause();
  }

  @Test
  void 정상_200_이어도_게이트웨이_XML_오류면_예외() {
    server.expect(requestTo(Matchers.startsWith(GbisStationClient.VIA_ROUTE_URL)))
        .andRespond(withSuccess("""
            <OpenAPI_ServiceResponse><cmmMsgHeader><errMsg>SERVICE ERROR</errMsg>
            <returnAuthMsg>LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR</returnAuthMsg></cmmMsgHeader></OpenAPI_ServiceResponse>
            """, MediaType.TEXT_XML));

    assertThatThrownBy(() -> client.findViaRoutes(1L))
        .isInstanceOf(GbisException.class)
        .hasMessageContaining("응답 형식이 다름")
        .hasMessageContaining("LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR")
        .hasMessageNotContaining(KEY);
  }

  @Test
  void 그_밖의_resultCode_는_예외() {
    server.expect(requestTo(Matchers.startsWith(GbisStationClient.AROUND_URL)))
        .andRespond(withSuccess("""
            {"response":{"msgHeader":{"resultCode":8,"resultMessage":"요청 제한을 초과하였습니다."}}}
            """, MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.findAround(37.28, 127.01))
        .isInstanceOf(GbisException.class)
        .hasMessageContaining("resultCode 8");
  }
}
