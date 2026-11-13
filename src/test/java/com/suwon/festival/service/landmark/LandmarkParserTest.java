package com.suwon.festival.service.landmark;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

// 재단 명소 XML 읽기 — 가짜 응답(src/test/resources/landmark/fortress-sample.xml, 실제 59_C 모양을 본뜸)
class LandmarkParserTest {

  static String sample() throws Exception {
    try (var in = LandmarkParserTest.class.getResourceAsStream("/landmark/fortress-sample.xml")) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static LandmarkParser.Parsed find(List<LandmarkParser.Parsed> list, String key) {
    return list.stream().filter(p -> p.sourceKey().equals(key)).findFirst().orElseThrow();
  }

  @Test
  void 항목_4개를_모두_읽음() throws Exception {
    List<LandmarkParser.Parsed> list = LandmarkParser.parse(sample(), LandmarkFeed.FORTRESS);

    assertThat(list).extracting(LandmarkParser.Parsed::sourceKey).containsExactly("9001", "9002", "9003", "9004");
    assertThat(list).allSatisfy(p -> assertThat(p.content().kind()).isEqualTo("수원화성"));
  }

  @Test
  void 뒤바뀐_좌표를_위도_경도로_바로잡음() throws Exception {
    LandmarkParser.Parsed gate = find(LandmarkParser.parse(sample(), LandmarkFeed.FORTRESS), "9001");

    assertThat(gate.lat()).isEqualTo(37.28891080512977); // 재단 lng 칸
    assertThat(gate.lng()).isEqualTo(127.01426159649375); // 재단 lat 칸
  }

  @Test
  void 재단이_좌표를_바르게_고쳐도_그대로_맞음() throws Exception {
    LandmarkParser.Parsed hall = find(LandmarkParser.parse(sample(), LandmarkFeed.FORTRESS), "9003");

    assertThat(hall.lat()).isEqualTo(37.28333808810258);
    assertThat(hall.lng()).isEqualTo(127.01241684344076);
  }

  @Test
  void 좌표_판정_규칙() {
    assertThat(LandmarkParser.coordinates("127.01", "37.28")).containsExactly(37.28, 127.01);
    assertThat(LandmarkParser.coordinates("37.28", "127.01")).containsExactly(37.28, 127.01);
    assertThat(LandmarkParser.coordinates("", "")).isNull();
    assertThat(LandmarkParser.coordinates(null, "37.28")).isNull();
    assertThat(LandmarkParser.coordinates("0", "0")).isNull(); // 범위 밖
    assertThat(LandmarkParser.coordinates("가", "37.28")).isNull();
  }

  @Test
  void 좌표가_빈_곳은_null() throws Exception {
    LandmarkParser.Parsed pond = find(LandmarkParser.parse(sample(), LandmarkFeed.FORTRESS), "9002");

    assertThat(pond.lat()).isNull();
    assertThat(pond.lng()).isNull();
  }

  @Test
  void 비공개는_빼지_않고_표시만() throws Exception {
    List<LandmarkParser.Parsed> list = LandmarkParser.parse(sample(), LandmarkFeed.FORTRESS);

    assertThat(find(list, "9003").content().name()).isEqualTo("가상전(假想殿)");
    assertThat(find(list, "9003").content().nonPublic()).isTrue();
    assertThat(find(list, "9001").content().nonPublic()).isFalse();
  }

  @Test
  void 첫머리_창건_복원_줄을_한_줄로() throws Exception {
    List<LandmarkParser.Parsed> list = LandmarkParser.parse(sample(), LandmarkFeed.FORTRESS);

    assertThat(find(list, "9001").content().history())
        .isEqualTo("1794년(정조 18) 창건 · 한국전쟁 때 문루 파괴 · 1975년 문루 복원");
    assertThat(find(list, "9002").content().history()).isEqualTo("1796년(정조 20) 조성");
    assertThat(find(list, "9003").content().history()).isNull(); // 첫 줄이 문장
    assertThat(find(list, "9004").content().history()).isNull();
  }

  @Test
  void 본문은_국문만_HTML_없이() throws Exception {
    List<LandmarkParser.Parsed> list = LandmarkParser.parse(sample(), LandmarkFeed.FORTRESS);
    String gate = find(list, "9001").content().description();

    assertThat(gate).startsWith("가상북문은 시험용 성의 북문이다. 이름은 ‘오래 편안하다’는 뜻이다.");
    assertThat(gate).contains("* 옹성 : 성문 밖에 쌓은 작은 성");
    assertThat(gate).doesNotContain("1794년").doesNotContain("<").doesNotContain("&").doesNotContain("North Gate");
    // 구분선 없이 영문이 섞여도 버림
    assertThat(find(list, "9004").content().description()).isEqualTo("이곳은 성벽이 끊어진 구간이다.");
    assertThat(find(list, "9002").content().description()).contains("<파랑새>"); // 한글 꺾쇠는 태그가 아님
  }

  @Test
  void 사진_링크_게시일() throws Exception {
    List<LandmarkParser.Parsed> list = LandmarkParser.parse(sample(), LandmarkFeed.FORTRESS);
    LandmarkParser.Parsed gate = find(list, "9001");

    assertThat(gate.content().imageUrl()).isEqualTo("https://www.swcf.or.kr/_File/test/gate.jpg");
    assertThat(gate.content().link()).isEqualTo("http://www.swcf.or.kr/?p=59&viewMode=view&idx=9001");
    assertThat(gate.content().pubDate()).isEqualTo(LocalDateTime.of(2020, 6, 1, 0, 0, 46));
    assertThat(find(list, "9003").content().imageUrl()).isNull(); // 빈 photos
  }

  @Test
  void 지오코딩용_한글_이름() {
    assertThat(LandmarkParser.koreanName("용연(龍淵)")).isEqualTo("용연");
    assertThat(LandmarkParser.koreanName("북동적대 北東敵臺")).isEqualTo("북동적대");
    assertThat(LandmarkParser.koreanName("미복원 동쪽 성벽")).isEqualTo("미복원 동쪽 성벽");
  }

  @Test
  void 목록_모양이_아니면_실패_빈_목록은_0건() throws Exception {
    assertThatThrownBy(() -> LandmarkParser.parse("<html><body>점검 중</body></html>", LandmarkFeed.FORTRESS))
        .isInstanceOf(Exception.class);
    assertThat(LandmarkParser.parse("<rss><channel><title>t</title></channel></rss>", LandmarkFeed.PALACE)).isEmpty();
  }
}
