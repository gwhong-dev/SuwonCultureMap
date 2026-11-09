package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.suwon.festival.entity.Event;
import com.suwon.festival.repository.EventRepository;
import com.suwon.festival.repository.InstagramBlockRepository;
import com.suwon.festival.repository.InstagramMediaRepository;
import com.suwon.festival.service.caption.CaptionParser;
import com.suwon.festival.service.instagram.FakeImageServer.Answer;
import com.suwon.festival.service.place.PlaceGeocoder;
import com.suwon.festival.service.schedule.EventScheduleWriter;

// 인스타 수집 → 사진 저장 → 우리 주소로 내보내기 → 행사가 지워지면 다음 동기화 끝에 파일 삭제 (docs/instagram-images.md)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class InstagramImageSyncTest {

  static final Path DIR;
  static {
    try {
      DIR = Files.createTempDirectory("insta-images-test");
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @DynamicPropertySource
  static void imageDir(DynamicPropertyRegistry registry) {
    registry.add("instagram.image-dir", DIR::toString);
  }

  static final OffsetDateTime POSTED = OffsetDateTime.of(2026, 10, 30, 9, 0, 0, 0, ZoneOffset.UTC);
  static final String ID = "1790000000000101";
  static final String CAPTION = String.join("\n",
      "가을 음악회",
      "🗓️ 2026. 11. 5.(목) 19:00",
      "📍 가상 아트홀");

  @Autowired private InstagramMediaRepository mediaRepository;
  @Autowired private InstagramBlockRepository blockRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private StaleBlockRetirer retirer;
  @Autowired private EventScheduleWriter scheduleWriter;
  @Autowired private WebApplicationContext context;

  private InstagramSyncServiceTest.FakeClient client;
  private FakeImageServer server;
  private InstagramSyncService service;

  @BeforeEach
  void setUp() throws Exception {
    clearDir();
    server = new FakeImageServer();
    client = new InstagramSyncServiceTest.FakeClient();
    InstagramImageStore store = new InstagramImageStore(DIR, List.of("127.0.0.1"), true);
    Clock clock = Clock.fixed(Instant.parse("2026-10-31T03:00:00Z"), InstagramPostProcessor.KST);
    InstagramPostProcessor processor = new InstagramPostProcessor(mediaRepository, blockRepository, eventRepository,
        new PlaceGeocoder(new InstagramSyncServiceTest.FakeSearch()), new CaptionParser(null), clock, retirer,
        scheduleWriter, store);
    service = new InstagramSyncService(client, processor, new InstagramTokenExpiry("", clock), new InstagramSwitch(true),
        "suwon_city", 10, store, eventRepository);
  }

  @AfterEach
  void tearDown() throws IOException {
    server.close();
    clearDir();
  }

  @Test
  void 행사가_나온_글의_사진을_저장하고_우리_주소로_내보냄() throws Exception {
    server.answers.put("/a.jpg", new Answer(200, "image/jpeg", FakeImageServer.JPEG, false));
    client.answers.put("suwon_city", List.of(post(ID, CAPTION, server.url("/a.jpg"))));

    service.sync();

    assertThat(instagramEvents()).singleElement()
        .extracting(Event::getImageUrl).isEqualTo("/media/insta/" + ID + ".jpg");
    assertThat(DIR.resolve(ID + ".jpg")).exists();

    MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).build();
    mvc.perform(get("/media/insta/" + ID + ".jpg"))
        .andExpect(status().isOk())
        .andExpect(content().contentType("image/jpeg"))
        .andExpect(content().bytes(FakeImageServer.JPEG))
        .andExpect(header().string("Cache-Control", "max-age=604800, public"));
    mvc.perform(get("/media/insta/999.jpg")).andExpect(status().isNotFound());
  }

  @Test
  void 다시_동기화해도_다시_받지_않고_같은_주소() {
    server.answers.put("/a.jpg", new Answer(200, "image/jpeg", FakeImageServer.JPEG, false));
    client.answers.put("suwon_city", List.of(post(ID, CAPTION, server.url("/a.jpg"))));

    service.sync();
    service.sync();

    assertThat(server.requests.get()).isEqualTo(1);
    assertThat(instagramEvents()).singleElement()
        .extracting(Event::getImageUrl).isEqualTo("/media/insta/" + ID + ".jpg");
  }

  @Test
  void 행사가_없는_글의_사진은_받지_않음() {
    server.answers.put("/a.jpg", new Answer(200, "image/jpeg", FakeImageServer.JPEG, false));
    client.answers.put("suwon_city", List.of(post(ID, "오늘의 수원 풍경 사진입니다", server.url("/a.jpg"))));

    service.sync();

    assertThat(server.requests.get()).isZero();
    assertThat(DIR.toFile().list()).isEmpty();
  }

  @Test
  void 받기에_실패하면_원래_주소를_그대로_씀() {
    client.answers.put("suwon_city", List.of(post(ID, CAPTION, server.url("/없는파일.jpg"))));

    service.sync();

    assertThat(instagramEvents()).singleElement()
        .extracting(Event::getImageUrl).isEqualTo(server.url("/없는파일.jpg"));
    assertThat(DIR.toFile().list()).isEmpty();
  }

  @Test
  void 행사가_지워지면_다음_동기화_끝에_사진_파일도_지움() throws Exception {
    server.answers.put("/a.jpg", new Answer(200, "image/jpeg", FakeImageServer.JPEG, false));
    client.answers.put("suwon_city", List.of(post(ID, CAPTION, server.url("/a.jpg"))));
    service.sync();
    Files.write(DIR.resolve("1790000000000999.jpg"), FakeImageServer.JPEG); // 처리가 되돌려져 남은 파일 같은 것

    service.sync();
    assertThat(DIR.resolve(ID + ".jpg")).exists(); // 쓰는 파일은 그대로
    assertThat(DIR.resolve("1790000000000999.jpg")).doesNotExist();

    // 행사 삭제 (내리기 · 정리 규칙 · 삭제 요청과 같은 결과) → 수집이 꺼져 있어도 정리는 함
    blockRepository.findAll().forEach(b -> b.update(b.getStatus(), b.getReason(), null, b.getUpdatedAt()));
    eventRepository.deleteAll(instagramEvents());
    client.configured = false;
    service.sync();

    assertThat(DIR.resolve(ID + ".jpg")).doesNotExist();
  }

  private static InstagramPost post(String id, String caption, String imageUrl) {
    return new InstagramPost(id, caption, "https://www.instagram.com/p/" + id + "/", POSTED, "IMAGE", imageUrl);
  }

  private List<Event> instagramEvents() {
    return eventRepository.findAll().stream().filter(e -> Event.SOURCE_INSTAGRAM.equals(e.getSource())).toList();
  }

  private static void clearDir() throws IOException {
    try (Stream<Path> files = Files.list(DIR)) {
      for (Path f : files.toList()) Files.deleteIfExists(f);
    }
  }
}
