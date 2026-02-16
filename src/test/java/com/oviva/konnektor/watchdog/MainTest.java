package com.oviva.konnektor.watchdog;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;

@Disabled("needs a running TI Konnektor")
class MainTest {

  private static final String CONFIG_PROPERTIES =
      """
            konnektor.uri=https://10.156.145.103:443
            proxy.address=127.0.0.1
            """;

  private static final CountDownLatch exit = new CountDownLatch(1);
  private final Pattern cardStatusPattern =
      Pattern.compile(
          """
            card_status\\{card_handle="[a-f0-9-]+",holder="[^"]+",telematik_id="[^"]+",konnektor="https://10.156.145.103:443"} ([.0-9]+)""");

  private final Pattern konnektorStatusUpPattern =
      Pattern.compile(
          """
            konnektor_status\\{konnektor="https://10.156.145.103:443"} 1.0""");

  @BeforeAll
  static void beforeEach() throws IOException {
    bootApp();
  }

  @AfterAll
  static void afterAll() {
    exit.countDown();
  }

  @Test
  void metrics_cardStatus() throws Exception {

    var metrics = fetchMetrics();
    assertContains(cardStatusPattern, metrics);
  }

  @Test
  void metrics_konnekturStatusUp() throws Exception {

    var metrics = fetchMetrics();
    assertContains(konnektorStatusUpPattern, metrics);
  }

  private String fetchMetrics() throws IOException, InterruptedException {

    var client = HttpClient.newHttpClient();

    var req = HttpRequest.newBuilder(URI.create("http://localhost:8080/metrics")).build();

    var res = client.send(req, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    return res.body();
  }

  private void assertContains(Pattern pattern, String contents) {
    assertTrue(
        pattern.matcher(contents).find(),
        "expected '%s' to match '%s'".formatted(contents, pattern.pattern()));
  }

  private static void bootApp() throws IOException {

    var config = new Properties();
    config.load(new StringReader(CONFIG_PROPERTIES));

    var executor = Executors.newFixedThreadPool(1);

    var started = new CountDownLatch(1);
    executor.execute(
        () -> {
          try (var m = new Main(k -> Optional.ofNullable(config.getProperty(k)))) {
            m.run();
            started.countDown();
            exit.await();
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        });

    boolean ok = false;
    try {
      ok = started.await(5, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
    if (!ok) {
      fail("server failed to boot within timeout");
    }
  }
}
