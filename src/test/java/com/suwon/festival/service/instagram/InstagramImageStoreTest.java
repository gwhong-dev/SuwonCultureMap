package com.suwon.festival.service.instagram;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.suwon.festival.service.instagram.FakeImageServer.Answer;

// 인스타 사진 저장: 가짜 이미지 서버로 저장 · 실패 · 크기 초과 · 삭제 (docs/instagram-images.md)
class InstagramImageStoreTest {

  static final String ID = "1790000000000001";

  @TempDir Path dir;
  private FakeImageServer server;
  private InstagramImageStore store;

  @BeforeEach
  void setUp() throws Exception {
    server = new FakeImageServer();
    store = new InstagramImageStore(dir, List.of("127.0.0.1"), true);
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  void 처리_이유를_나눠_돌려줌() {
    server.answers.put("/a.jpg", new Answer(200, "image/jpeg", FakeImageServer.JPEG, false));
    InstagramImageStore real = new InstagramImageStore(dir, InstagramImageStore.CDN_SUFFIXES, false);
    String other = "1790000000000002";

    assertThat(store.resolveWithReason(other, null).reason()).isEqualTo(InstagramImageStore.Reason.NO_URL);
    assertThat(store.resolveWithReason(other, " ").reason()).isEqualTo(InstagramImageStore.Reason.NO_URL);
    assertThat(real.resolveWithReason(other, "https://example.com/a.jpg"))
        .isEqualTo(new InstagramImageStore.Resolution(null, InstagramImageStore.Reason.DOMAIN_REJECTED));
    assertThat(store.resolveWithReason(other, server.url("/없음.jpg")).reason())
        .isEqualTo(InstagramImageStore.Reason.DOWNLOAD_FAILED);
    assertThat(store.resolveWithReason(ID, server.url("/a.jpg")).reason()).isEqualTo(InstagramImageStore.Reason.STORED_NEW);
    assertThat(store.resolveWithReason(ID, null))
        .isEqualTo(new InstagramImageStore.Resolution("/media/insta/" + ID + ".jpg", InstagramImageStore.Reason.ALREADY_STORED));
    assertThat(InstagramImageStore.disabled().resolveWithReason(ID, server.url("/a.jpg")).reason())
        .isEqualTo(InstagramImageStore.Reason.DISABLED);
  }

  @Test
  void 받아서_저장하고_우리_주소를_돌려주며_다음엔_다시_받지_않음() throws Exception {
    server.answers.put("/a.jpg", new Answer(200, "image/jpeg", FakeImageServer.JPEG, false));

    String url = store.resolve(ID, server.url("/a.jpg"));

    assertThat(url).isEqualTo("/media/insta/" + ID + ".jpg");
    assertThat(Files.readAllBytes(dir.resolve(ID + ".jpg"))).isEqualTo(FakeImageServer.JPEG);
    assertThat(store.resolve(ID, server.url("/a.jpg"))).isEqualTo(url);
    assertThat(store.resolve(ID, null)).isEqualTo(url); // 원래 주소가 없어도 받아 둔 파일로
    assertThat(server.requests.get()).isEqualTo(1);
  }

  @Test
  void 확장자는_응답_종류가_아니라_파일_앞부분으로_정함() {
    server.answers.put("/p", new Answer(200, "image/jpeg", FakeImageServer.PNG, false));

    assertThat(store.resolve(ID, server.url("/p"))).isEqualTo("/media/insta/" + ID + ".png");
  }

  @Test
  void 받기_실패면_원래_주소를_그대로_쓰고_파일을_남기지_않음() throws Exception {
    server.answers.put("/500", new Answer(500, "text/plain", "x".getBytes(), false));
    server.answers.put("/html", new Answer(200, "text/html", "<html>".getBytes(), false));
    server.answers.put("/fake", new Answer(200, "image/jpeg", "not an image at all".getBytes(), false));

    server.answers.put("/moved", new Answer(302, "text/plain", "x".getBytes(), false)); // redirect 는 안 따라감

    for (String path : List.of("/500", "/html", "/fake", "/없음", "/moved")) {
      assertThat(store.resolve(ID, server.url(path))).isEqualTo(server.url(path));
    }
    assertThat(files()).isEmpty();
  }

  @Test
  void 크기_5MB_를_넘으면_길이를_밝혀도_안_밝혀도_저장하지_않음() throws Exception {
    byte[] big = Arrays.copyOf(FakeImageServer.JPEG, (int) InstagramImageStore.MAX_BYTES + 1);
    server.answers.put("/big", new Answer(200, "image/jpeg", big, false));
    server.answers.put("/big-chunked", new Answer(200, "image/jpeg", big, true));

    assertThat(store.resolve(ID, server.url("/big"))).isEqualTo(server.url("/big"));
    assertThat(store.resolve(ID, server.url("/big-chunked"))).isEqualTo(server.url("/big-chunked"));
    assertThat(files()).isEmpty(); // 임시 파일(.part)도 없음

    byte[] limit = Arrays.copyOf(FakeImageServer.JPEG, (int) InstagramImageStore.MAX_BYTES);
    server.answers.put("/limit", new Answer(200, "image/jpeg", limit, true));
    assertThat(store.resolve(ID, server.url("/limit"))).isEqualTo("/media/insta/" + ID + ".jpg");
  }

  @Test
  void 본문이_오래_걸리면_끊고_원래_주소() throws Exception {
    java.time.Duration before = InstagramImageStore.BODY_TIMEOUT;
    InstagramImageStore.BODY_TIMEOUT = java.time.Duration.ofMillis(300);
    var slow = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
    try {
      slow.createContext("/", ex -> {
        ex.getResponseHeaders().set("Content-Type", "image/jpeg");
        ex.sendResponseHeaders(200, 0);
        try {
          ex.getResponseBody().write(FakeImageServer.JPEG);
          ex.getResponseBody().flush();
          Thread.sleep(3000); // 머리는 바로, 본문은 멈춤
        } catch (InterruptedException ignore) {
          Thread.currentThread().interrupt();
        } finally {
          ex.close();
        }
      });
      slow.start();
      String url = "http://127.0.0.1:" + slow.getAddress().getPort() + "/slow.jpg";
      long t0 = System.nanoTime();
      assertThat(store.resolve(ID, url)).isEqualTo(url);
      assertThat(java.time.Duration.ofNanos(System.nanoTime() - t0)).isLessThan(java.time.Duration.ofSeconds(2));
      assertThat(files()).isEmpty();
    } finally {
      slow.stop(0);
      InstagramImageStore.BODY_TIMEOUT = before;
    }
  }

  @Test
  void 허용_도메인이_아니거나_https_가_아니면_요청하지_않고_null() {
    InstagramImageStore real = new InstagramImageStore(dir.toString());

    assertThat(real.allowedUri("https://scontent-ssn1-1.cdninstagram.com/v/t51/a.jpg?oh=1")).isNotNull();
    assertThat(real.allowedUri("https://scontent.xx.fbcdn.net/a.jpg")).isNotNull();
    assertThat(real.allowedUri("http://scontent.cdninstagram.com/a.jpg")).isNull();
    assertThat(real.allowedUri("https://cdninstagram.com.evil.example/a.jpg")).isNull();
    assertThat(real.allowedUri("https://evilcdninstagram.com/a.jpg")).isNull();
    assertThat(real.allowedUri("https://user@scontent.cdninstagram.com/a.jpg")).isNull();
    assertThat(real.allowedUri("https://169.254.169.254/latest/meta-data")).isNull();
    assertThat(real.resolve(ID, "https://example.com/a.jpg")).isNull();
    assertThat(real.resolve(ID, server.url("/a.jpg"))).isNull(); // http · localhost
    assertThat(server.requests.get()).isZero();
  }

  @Test
  void 게시물_id_가_숫자가_아니거나_기능이_꺼져_있으면_null() {
    server.answers.put("/a.jpg", new Answer(200, "image/jpeg", FakeImageServer.JPEG, false));

    assertThat(store.resolve("../etc", server.url("/a.jpg"))).isNull();
    assertThat(InstagramImageStore.disabled().resolve(ID, server.url("/a.jpg"))).isNull();
    assertThat(new InstagramImageStore("  ").isEnabled()).isFalse();
    assertThat(server.requests.get()).isZero();
  }

  @Test
  void 정리는_쓰지_않는_사진과_임시_파일만_지우고_다른_파일은_둠() throws Exception {
    Files.write(dir.resolve("111.jpg"), FakeImageServer.JPEG);
    Files.write(dir.resolve("222.png"), FakeImageServer.PNG);
    Files.write(dir.resolve("333.part"), new byte[1]);
    Files.write(dir.resolve("readme.txt"), new byte[1]);

    int deleted = store.cleanup(Set.of("111"));

    assertThat(deleted).isEqualTo(2);
    assertThat(files()).containsExactlyInAnyOrder("111.jpg", "readme.txt");
    assertThat(InstagramImageStore.disabled().cleanup(Set.of())).isZero();
  }

  @Test
  void 우리_주소에서만_게시물_id_를_꺼냄() {
    assertThat(InstagramImageStore.mediaIdOf("/media/insta/123.jpg")).isEqualTo("123");
    assertThat(InstagramImageStore.mediaIdOf("/media/insta/123.webp")).isEqualTo("123");
    assertThat(InstagramImageStore.mediaIdOf("https://scontent.cdninstagram.com/123.jpg")).isNull();
    assertThat(InstagramImageStore.mediaIdOf("/media/insta/../123.jpg")).isNull();
    assertThat(InstagramImageStore.mediaIdOf(null)).isNull();
  }

  private List<String> files() throws Exception {
    try (var s = Files.list(dir)) {
      return s.map(p -> p.getFileName().toString()).toList();
    }
  }
}
