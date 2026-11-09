package com.suwon.festival.service.instagram;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

// 시험용 가짜 이미지 서버 (127.0.0.1, http). 경로마다 상태 · 종류 · 본문을 정해 둠
class FakeImageServer implements AutoCloseable {

  record Answer(int status, String contentType, byte[] body, boolean chunked) {
  }

  static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16, 'J', 'F', 'I', 'F', 0, 1, 1};
  static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13, 'I', 'H', 'D', 'R'};

  final Map<String, Answer> answers = new HashMap<>();
  final AtomicInteger requests = new AtomicInteger();
  private final HttpServer server;

  FakeImageServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      requests.incrementAndGet();
      Answer a = answers.getOrDefault(exchange.getRequestURI().getPath(), new Answer(404, "text/plain", new byte[0], false));
      exchange.getResponseHeaders().set("Content-Type", a.contentType());
      exchange.sendResponseHeaders(a.status(), a.chunked() ? 0 : (a.body().length == 0 ? -1 : a.body().length));
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(a.body());
      }
    });
    server.start();
  }

  String url(String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
