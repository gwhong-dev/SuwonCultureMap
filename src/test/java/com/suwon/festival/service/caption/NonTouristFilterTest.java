package com.suwon.festival.service.caption;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.suwon.festival.service.caption.CaptionParser.BlockResult;

// 관광 대상 아님 거르기 — 11/13 운영 인스타 행사 제목 모양을 본뜬 가상 제목
class NonTouristFilterTest {

  @Test
  void 정책_포럼_기념식_강연_포럼_기업_박람회는_거름() {
    assertThat(NonTouristFilter.reason("제12회 가상 교통정책발전포럼", "가상 시청 중회의실 (별관1층)")).isEqualTo("관광 대상 아님: 포럼");
    assertThat(NonTouristFilter.reason("가상 건강가정지원센터 20주년 기념식", "가상 아트홀")).isEqualTo("관광 대상 아님: 기념식");
    assertThat(NonTouristFilter.reason("가상 청소년센터 개관 10주년 기념식", "가상 대강당")).isEqualTo("관광 대상 아님: 기념식");
    assertThat(NonTouristFilter.reason("수원새빛포럼 <가상 작가 편>", "가상 시청 대강당")).isEqualTo("관광 대상 아님: 포럼");
    assertThat(NonTouristFilter.reason("2026년 가상 중소기업융합대전", "가상 컨벤션센터")).isEqualTo("관광 대상 아님: 융합대전");
  }

  @Test
  void 기업_구직자_대상_낱말은_띄어쓰기_대소문자와_상관없이_거름() {
    assertThat(NonTouristFilter.reason("가상 채용 박람회", "가상 체육관")).isEqualTo("관광 대상 아님: 채용박람회");
    assertThat(NonTouristFilter.reason("가상 일자리박람회", "가상 체육관")).isEqualTo("관광 대상 아님: 일자리박람회");
    assertThat(NonTouristFilter.reason("가상 수출 상담회", "가상 컨벤션센터")).isEqualTo("관광 대상 아님: 상담회");
    assertThat(NonTouristFilter.reason("가상 B2B 매칭데이", "가상 컨벤션센터")).isEqualTo("관광 대상 아님: b2b");
  }

  @Test
  void 장소가_회의실이면_거름() {
    assertThat(NonTouristFilter.reason("가상 시민 모임", "가상 시청 대회의실")).isEqualTo("관광 대상 아님: 회의실");
  }

  @Test
  void 일반인이_구경_구매하는_박람회_페스타_쇼와_공연_경기는_남김() {
    List<String> kept = List.of("가상 커피박람회", "가상 애견용품 박람회", "2026 가상 주류페스타", "2026 #가상뷰티썸",
        "2026 가상 우수중소기업 & 밀리언쇼", "가상 시립교향악단 제306회 정기연주회", "[축구] 가상 FC VS 다른 FC",
        "가상 가족지원센터 '제15회 힐링토크콘서트'", "제8회 가상 의장배 족구대회", "전시 <가상의 시간>", "가상 엑스포");
    for (String title : kept) {
      assertThat(NonTouristFilter.reason(title, "가상 아트홀")).as(title).isNull();
    }
  }

  @Test
  void 개관_주년_정책은_문화_낱말이_같이_있으면_남김() {
    assertThat(NonTouristFilter.reason("가상 미술관 개관 10주년 특별전", "가상 미술관")).isNull();
    assertThat(NonTouristFilter.reason("창립 30주년 기념 음악회", "가상 아트홀")).isNull();
    assertThat(NonTouristFilter.reason("가상 도서관 개관식", "가상 도서관")).isEqualTo("관광 대상 아님: 개관");
    assertThat(NonTouristFilter.reason("가상 복지관 30주년", "가상 복지관")).isEqualTo("관광 대상 아님: 30주년");
    assertThat(NonTouristFilter.reason("가상 청년정책 박람회", "가상 광장")).isEqualTo("관광 대상 아님: 정책");
  }

  @Test
  void 캡션_정리에서_SKIPPED_로_바꾸고_읽은_값은_남김() {
    String c = "제12회 가상 교통정책발전포럼\n🗓️ 2026. 11. 5.(목) 14:00\n📍 가상 시청 중회의실";
    BlockResult r = new CaptionParser(null).parse(c).get(0);
    assertThat(r.status()).isEqualTo(CandidateStatus.SKIPPED);
    assertThat(r.reason()).isEqualTo("관광 대상 아님: 포럼");
    assertThat(r.event().title()).isEqualTo("제12회 가상 교통정책발전포럼"); // 블록 키가 READY 때와 같게
    assertThat(NonTouristFilter.isNonTourist(r.reason())).isTrue();
  }

  @Test
  void AI가_읽은_행사도_거름() {
    String prose = "가상 시청에서 2026년 11월 5일 세미나가 열려요";
    CaptionAiClient ai = t -> java.util.Optional.of(new ParsedEvent(true, "가상 세미나", "행사",
        java.time.LocalDate.of(2026, 11, 5), null, null, "가상 시청"));
    BlockResult r = new CaptionParser(ai).parse(prose).get(0);
    assertThat(r.status()).isEqualTo(CandidateStatus.SKIPPED);
    assertThat(r.reason()).isEqualTo("관광 대상 아님: 세미나");
  }
  @Test
  void 띄어쓰기를_넘어_걸리지_않고_축성_230주년_투어_같은_관광_행사는_남김() {
    List<String> kept = List.of("화성행궁 야간 공개 관람", "어린이날 기념 식물 나눔", "우정 책방 북토크", "굿바이 어린이 인형극",
        "수원화성 축성 230주년 기념 야간 투어", "3·1운동 107주년 만세 재현", "가상 미술관 개관 기념 특별전");
    for (String title : kept) {
      assertThat(NonTouristFilter.reason(title, "가상 광장")).as(title).isNull();
    }
  }

  @Test
  void 공청회_심포지엄_시상식_준공식도_거름() {
    assertThat(NonTouristFilter.reason("가상 도시계획 공청회", "가상 시민홀")).isEqualTo("관광 대상 아님: 공청회");
    assertThat(NonTouristFilter.reason("가상 국제 심포지엄", "가상 컨벤션센터")).isEqualTo("관광 대상 아님: 심포지엄");
    assertThat(NonTouristFilter.reason("가상 시민상 시상식", "가상 대강당")).isEqualTo("관광 대상 아님: 시상식");
    assertThat(NonTouristFilter.reason("가상 복합센터 준공식", "가상 복합센터")).isEqualTo("관광 대상 아님: 준공식");
  }

  @Test
  void 날짜를_못_읽은_블록도_관광_대상_아니면_SKIPPED() {
    String c = "가상 정책 토론회\n🗓️ 매주 금~일요일\n📍 가상 아트홀";
    BlockResult r = new CaptionParser(null).parse(c).get(0);
    assertThat(r.status()).isEqualTo(CandidateStatus.SKIPPED);
    assertThat(r.reason()).isEqualTo("관광 대상 아님: 토론회");
    assertThat(r.event().startDate()).isNull();
  }
}
