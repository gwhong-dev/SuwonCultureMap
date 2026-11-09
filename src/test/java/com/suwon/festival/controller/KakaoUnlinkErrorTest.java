package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.LoginMethod;
import com.suwon.festival.service.KakaoUnlinkService;

// 처리 중 예외(같은 알림 동시 두 번 → 늦은 쪽 삭제 실패 등)도 카카오에는 200 (오류가 쌓이면 콜백이 꺼질 수 있음)
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "kakao.admin-key=test-admin-key")
class KakaoUnlinkErrorTest {

  @Autowired private WebApplicationContext context;
  @MockitoBean private KakaoUnlinkService kakaoUnlinkService;

  @Test
  void 처리_중_예외가_나도_200() throws Exception {
    when(kakaoUnlinkService.handleUnlink(any()))
        .thenThrow(new ObjectOptimisticLockingFailureException(LoginMethod.class, 1L));

    int status = MockMvcBuilders.webAppContextSetup(context).build()
        .perform(post("/api/auth/kakao/unlink-callback").header("Authorization", "KakaoAK test-admin-key")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED).param("user_id", "1001"))
        .andReturn().getResponse().getStatus();

    assertThat(status).isEqualTo(200);
  }
}
