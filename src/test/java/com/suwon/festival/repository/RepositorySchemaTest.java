package com.suwon.festival.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// 엔티티 4개의 테이블이 실제로 만들어졌는지 확인
//  (테이블 생성이 실패해도 Hibernate 는 경고만 남기고 앱은 뜨기 때문에, 실제로 조회해 봐야 잡힘)
@SpringBootTest
@ActiveProfiles("test")
class RepositorySchemaTest {

  @Autowired private EventRepository eventRepository;
  @Autowired private MemberRepository memberRepository;
  @Autowired private LoginMethodRepository loginMethodRepository;
  @Autowired private FavoriteRepository favoriteRepository;

  @Test
  void allTablesAreCreated() {
    assertThat(eventRepository.count()).isZero();
    assertThat(memberRepository.count()).isZero();
    assertThat(loginMethodRepository.count()).isZero();
    assertThat(favoriteRepository.count()).isZero();
  }
}
