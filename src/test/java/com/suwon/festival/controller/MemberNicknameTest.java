package com.suwon.festival.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.MemberRepository;

// 닉네임: /api/auth/me 가 없으면 붙여 주고, PATCH 로 바꾸고, 겹치거나 규칙에 안 맞으면 400
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class MemberNicknameTest {

  @Autowired private WebApplicationContext context;
  @Autowired private MemberRepository memberRepository;

  private MockMvc mockMvc;
  private Member member;
  private Member other;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    member = memberRepository.saveAndFlush(Member.create());
    other = memberRepository.saveAndFlush(Member.create());
  }

  private MockHttpSession sessionOf(Member m) {
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("memberId", m.getId());
    return session;
  }

  private MockHttpServletResponse changeTo(Member m, String json) throws Exception {
    return mockMvc.perform(patch("/api/members/me/nickname").session(sessionOf(m))
        .contentType(MediaType.APPLICATION_JSON).content(json)).andReturn().getResponse();
  }

  @Test
  void 옛_회원은_me_에서_닉네임이_붙고_다음엔_그대로() throws Exception {
    assertThat(member.getNickname()).isNull();
    String first = mockMvc.perform(get("/api/auth/me").session(sessionOf(member)))
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    String nickname = memberRepository.findById(member.getId()).orElseThrow().getNickname();
    assertThat(nickname).isNotBlank();
    assertThat(first).contains("\"nickname\":\"" + nickname + "\"");

    String second = mockMvc.perform(get("/api/auth/me").session(sessionOf(member)))
        .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    assertThat(second).contains("\"nickname\":\"" + nickname + "\"");
  }

  @Test
  void 비로그인_me_에는_닉네임이_없음() throws Exception {
    String body = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse()
        .getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).doesNotContain("nickname");
  }

  @Test
  void 닉네임을_바꾸면_정리해서_저장() throws Exception {
    MockHttpServletResponse res = changeTo(member, "{\"nickname\":\"  수원   러버 \"}");
    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains("수원 러버");
    assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname()).isEqualTo("수원 러버");
  }

  @Test
  void 다른_회원이_쓰는_이름은_400() throws Exception {
    assertThat(changeTo(other, "{\"nickname\":\"화성 산책러\"}").getStatus()).isEqualTo(200);
    MockHttpServletResponse res = changeTo(member, "{\"nickname\":\"화성 산책러\"}");
    assertThat(res.getStatus()).isEqualTo(400);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains("이미 다른 회원");
  }

  @Test
  void 내_이름을_그대로_저장하면_통과() throws Exception {
    assertThat(changeTo(member, "{\"nickname\":\"화성 산책러\"}").getStatus()).isEqualTo(200);
    assertThat(changeTo(member, "{\"nickname\":\"화성 산책러\"}").getStatus()).isEqualTo(200);
  }

  @Test
  void 규칙에_안_맞으면_400() throws Exception {
    assertThat(changeTo(member, "{\"nickname\":\"가\"}").getStatus()).isEqualTo(400);
    assertThat(changeTo(member, "{\"nickname\":\"<script>\"}").getStatus()).isEqualTo(400);
    assertThat(changeTo(member, "{}").getStatus()).isEqualTo(400);
  }

  @Test
  void 비로그인은_바꿀_수_없음() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(patch("/api/members/me/nickname")
        .contentType(MediaType.APPLICATION_JSON).content("{\"nickname\":\"수원 러버\"}"))
        .andReturn().getResponse();
    assertThat(res.getStatus()).isEqualTo(400);
  }

  @Test
  void 다시_뽑기는_저장하지_않음() throws Exception {
    MockHttpServletResponse res = mockMvc.perform(get("/api/members/me/nickname/suggestion")
        .session(sessionOf(member))).andReturn().getResponse();
    assertThat(res.getStatus()).isEqualTo(200);
    assertThat(res.getContentAsString(StandardCharsets.UTF_8)).contains("nickname");
    assertThat(memberRepository.findById(member.getId()).orElseThrow().getNickname()).isNull();
  }
}
