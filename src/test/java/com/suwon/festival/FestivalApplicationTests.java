package com.suwon.festival;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 앱 전체 설정(빈·JPA·설정값 주입)이 문제없이 올라오는지 확인
@SpringBootTest
@ActiveProfiles("test")
class FestivalApplicationTests {

  @Test
  void contextLoads() {
  }
}
