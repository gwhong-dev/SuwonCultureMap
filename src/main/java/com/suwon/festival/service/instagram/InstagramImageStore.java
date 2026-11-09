package com.suwon.festival.service.instagram;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

// 인스타 행사 사진 1장을 받아 서버 디스크(운영 = Docker 볼륨 /app/images)에 저장 (설계 docs/instagram-images.md)
//  - Graph API 의 media_url 은 시간이 지나면 만료됨 → 받아 두고 우리 주소(/media/insta/{게시물id}.jpg)로 내보냄
//  - 게시물 하나 = 파일 하나 (그 글에서 나온 행사들이 같이 씀). 이미 있으면 다시 받지 않음
//  - 받을 곳은 https + 인스타 · 페이스북 CDN 도메인만 (다른 주소로 요청을 보내게 만드는 공격 차단), 넘겨주기(redirect)도 안 따라감
//  - 5MB 까지 · 파일 앞부분이 JPEG · PNG · WEBP 일 때만 저장. 실패하면 원래 주소를 그대로 씀 (만료되면 화면이 사진 칸을 숨김)
//  - 지울 때: 동기화가 끝날 때마다 DB 행사가 더 쓰지 않는 파일을 지움 (cleanup) → 행사가 지워지면 사진도 같이
//  - 저장 폴더(instagram.image-dir)가 비어 있으면 이 기능 전체를 끔 (로컬 · 시험 기본)
@Component
public class InstagramImageStore {

  private static final Logger log = LoggerFactory.getLogger(InstagramImageStore.class);

  public static final String PUBLIC_PREFIX = "/media/insta/";
  static final long MAX_BYTES = 5L * 1024 * 1024;
  static Duration BODY_TIMEOUT = Duration.ofSeconds(20); // 본문 받기 전체 시간 (시험에서 줄임)
  static final List<String> CDN_SUFFIXES = List.of(".cdninstagram.com", ".fbcdn.net");

  private static final Pattern MEDIA_ID = Pattern.compile("^[0-9]{1,64}$");
  // 저장 파일 이름: {게시물 id}.{jpg|png|webp}. 이 모양이 아닌 파일은 지우지도 내보내지도 않음
  static final Pattern FILE_NAME = Pattern.compile("^([0-9]{1,64})\\.(jpg|png|webp)$");
  private static final Pattern PUBLIC_URL = Pattern.compile("^" + Pattern.quote(PUBLIC_PREFIX) + "([0-9]{1,64})\\.(jpg|png|webp)$");
  private static final String[] EXTENSIONS = {"jpg", "png", "webp"};

  private final Path dir;
  private final List<String> allowedHostSuffixes;
  private final boolean allowHttp;
  private final HttpClient http;

  @Autowired
  public InstagramImageStore(@Value("${instagram.image-dir:}") String dir) {
    this(dir == null || dir.isBlank() ? null : Path.of(dir.strip()), CDN_SUFFIXES, false);
  }

  // 시험용: 가짜 이미지 서버(localhost · http)를 허용
  InstagramImageStore(Path dir, List<String> allowedHostSuffixes, boolean allowHttp) {
    this.dir = dir == null ? null : dir.toAbsolutePath().normalize();
    this.allowedHostSuffixes = allowedHostSuffixes;
    this.allowHttp = allowHttp;
    this.http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build();
  }

  public static InstagramImageStore disabled() {
    return new InstagramImageStore(null, CDN_SUFFIXES, false);
  }

  public boolean isEnabled() {
    return dir != null;
  }

  public Optional<Path> directory() {
    return Optional.ofNullable(dir);
  }

  // 행사에 넣을 사진 주소. 저장 성공(또는 이미 있음) = 우리 주소, 실패 = 원래 주소, 기능 꺼짐 · 주소 없음 = null
  public String resolve(String mediaId, String sourceUrl) {
    if (!isEnabled() || mediaId == null || !MEDIA_ID.matcher(mediaId).matches()) return null;
    Optional<String> stored = existing(mediaId);
    if (stored.isPresent()) return PUBLIC_PREFIX + stored.get();
    URI uri = allowedUri(sourceUrl);
    if (uri == null) {
      if (sourceUrl != null && !sourceUrl.isBlank()) log.warn("인스타 사진 주소가 허용 도메인이 아님 → 저장 안 함 mediaId={}", mediaId);
      return null; // 허용 안 되는 주소는 화면에도 내보내지 않음
    }
    try {
      return PUBLIC_PREFIX + download(mediaId, uri);
    } catch (IOException | RuntimeException e) {
      log.warn("인스타 사진 저장 실패 → 원래 주소 유지 mediaId={} {}", mediaId, e.getMessage());
      return sourceUrl;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return sourceUrl;
    }
  }

  // keepMediaIds 에 없는 저장 파일 · 남은 임시 파일을 지움. 지운 개수
  public int cleanup(Set<String> keepMediaIds) {
    if (!isEnabled() || !Files.isDirectory(dir)) return 0;
    int deleted = 0;
    try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
      for (Path file : files) {
        String name = file.getFileName().toString();
        Matcher m = FILE_NAME.matcher(name);
        boolean orphan = m.matches() ? !keepMediaIds.contains(m.group(1)) : name.endsWith(".part");
        if (orphan && Files.isRegularFile(file) && Files.deleteIfExists(file)) deleted++;
      }
    } catch (IOException e) {
      log.warn("인스타 사진 정리 실패 {}", e.getMessage());
    }
    if (deleted > 0) log.info("인스타 사진 정리: 쓰지 않는 파일 {}개 삭제", deleted);
    return deleted;
  }

  // 우리 주소(/media/insta/123.jpg)에서 게시물 id. 아니면 null
  public static String mediaIdOf(String imageUrl) {
    if (imageUrl == null) return null;
    Matcher m = PUBLIC_URL.matcher(imageUrl);
    return m.matches() ? m.group(1) : null;
  }

  private Optional<String> existing(String mediaId) {
    return Arrays.stream(EXTENSIONS).map(ext -> mediaId + "." + ext)
        .filter(name -> Files.isRegularFile(dir.resolve(name))).findFirst();
  }

  URI allowedUri(String url) {
    if (url == null || url.isBlank()) return null;
    URI uri;
    try {
      uri = URI.create(url.strip());
    } catch (IllegalArgumentException e) {
      return null;
    }
    String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
    if (!scheme.equals("https") && !(allowHttp && scheme.equals("http"))) return null;
    if (uri.getUserInfo() != null) return null;
    String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
    boolean allowed = allowedHostSuffixes.stream().anyMatch(s -> host.endsWith(s) || host.equals(s.replaceFirst("^\\.", "")));
    return allowed ? uri : null;
  }

  private String download(String mediaId, URI uri) throws IOException, InterruptedException {
    HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build();
    HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
    // 요청 timeout 은 응답 머리까지만 → 본문은 전체 시간(BODY_TIMEOUT)이 지나면 스트림을 닫아 읽기를 끊음 (DB 연결을 오래 안 붙잡게)
    InputStream stream = response.body();
    java.util.concurrent.CompletableFuture.delayedExecutor(BODY_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        .execute(() -> {
          try {
            stream.close();
          } catch (IOException ignore) {
            // 이미 닫힘
          }
        });
    try (InputStream body = stream) {
      if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
      String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
      if (!type.startsWith("image/")) throw new IOException("이미지가 아님 (" + cut(type) + ")");
      long declared = response.headers().firstValueAsLong("Content-Length").orElse(-1);
      if (declared > MAX_BYTES) throw new IOException("크기 초과 " + declared + "B");

      Files.createDirectories(dir);
      Path part = dir.resolve(mediaId + ".part");
      try {
        byte[] head = copyLimited(body, part);
        String ext = extensionOf(head);
        if (ext == null) throw new IOException("JPEG · PNG · WEBP 가 아님");
        Path target = dir.resolve(mediaId + "." + ext);
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return target.getFileName().toString();
      } finally {
        Files.deleteIfExists(part);
      }
    }
  }

  // MAX_BYTES 를 넘으면 중단. 앞 12바이트를 돌려줌 (파일 종류 판단용)
  private static byte[] copyLimited(InputStream in, Path target) throws IOException {
    byte[] head = new byte[12];
    int headLen = 0;
    long total = 0;
    byte[] buf = new byte[8192];
    try (OutputStream out = Files.newOutputStream(target)) {
      int n;
      while ((n = in.read(buf)) != -1) {
        total += n;
        if (total > MAX_BYTES) throw new IOException("크기 초과 " + MAX_BYTES + "B 넘음");
        if (headLen < head.length) {
          int c = Math.min(n, head.length - headLen);
          System.arraycopy(buf, 0, head, headLen, c);
          headLen += c;
        }
        out.write(buf, 0, n);
      }
    }
    return headLen == head.length ? head : Arrays.copyOf(head, headLen);
  }

  static String extensionOf(byte[] h) {
    if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "jpg";
    if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G') return "png";
    if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F'
        && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P') return "webp";
    return null;
  }

  private static String cut(String s) {
    return s.length() <= 40 ? s : s.substring(0, 40);
  }
}
