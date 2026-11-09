package com.suwon.festival.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.Event;
import com.suwon.festival.entity.Member;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.MemberRepository;

// POST /api/favorites/merge — 비회원 찜(이 기기)을 로그인한 계정에 합침
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FavoriteMergeControllerTest {

  @Autowired private WebApplicationContext context;
  @Autowired private MemberRepository memberRepository;
  @Autowired private EventRepository eventRepository;

  private MockMvc mvc() {
    return MockMvcBuilders.webAppContextSetup(context).build();
  }

  @Test
  void 로그인하지_않으면_합치지_않음() throws Exception {
    mvc().perform(post("/api/favorites/merge").contentType(MediaType.APPLICATION_JSON).content("{\"eventIds\":[1]}"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("로그인이 필요합니다."));
  }

  @Test
  void 로그인하면_더한_개수를_돌려줌() throws Exception {
    Long memberId = memberRepository.saveAndFlush(Member.create()).getId();
    Long eventId = eventRepository.saveAndFlush(Event.builder()
        .idx(990501L).title("가상 행사 합치기").category("공연").latitude(37.28).longitude(127.01)
        .pubDate(LocalDateTime.of(2026, 11, 1, 12, 0))
        .startDate(LocalDate.of(2026, 11, 5)).endDate(LocalDate.of(2026, 11, 5)).build()).getId();
    MockHttpSession session = new MockHttpSession();
    session.setAttribute("memberId", memberId);

    mvc().perform(post("/api/favorites/merge").session(session).contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventIds\":[" + eventId + "," + eventId + ",-1]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.added").value(1));
  }
}
