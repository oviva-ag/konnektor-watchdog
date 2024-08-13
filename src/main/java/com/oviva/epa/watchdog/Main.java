package com.oviva.epa.watchdog;

import com.oviva.epa.client.konn.KonnektorConnectionFactory;
import com.oviva.epa.client.konn.KonnektorConnectionFactoryBuilder;
import com.oviva.epa.watchdog.cfg.ConfigProvider;
import com.oviva.epa.watchdog.cfg.EnvConfigProvider;
import com.oviva.epa.watchdog.handlers.MetricsHandler;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.function.Supplier;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  private final ConfigProvider configProvider;
  private Undertow server;

  public Main(ConfigProvider configProvider) {
    this.configProvider = configProvider;
  }

  public static void main(String[] args) {
    logger.atDebug().log("initialising application");
    try (var app = new Main(new EnvConfigProvider("EPA_FM_WATCHDOG", System::getenv))) {
      app.run();
    }
  }

  public void run() {
    logger.atDebug().log("running application");

    var config = loadConfig(configProvider);
    logger.atInfo().log("config loaded: {}", config);

    var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    registerGauges(registry, config);
    logger.atDebug().log("gauges registered");

    var host = config.watchdogAddress();
    var port = config.watchdogPort();

    logger.atDebug().log("booting server at http://{}:{}/", host, port);

    server = buildServer(host, port, new MetricsHandler(registry::scrape));
    server.start();

    logger.atInfo().log("server ready at http://{}:{}/", host, port);
  }

  private Undertow buildServer(String host, int port, HttpHandler metricsHandler) {

    return Undertow.builder()
        .addHttpListener(port, host)
        .setHandler(
            Handlers.path()
                .addExactPath(
                    "/health",
                    ex -> {
                      ex.setStatusCode(200);
                      ex.endExchange();
                    })
                .addExactPath("/metrics", metricsHandler))
        .build();
  }

  private void registerGauges(MeterRegistry registry, KonnektorConfig config) {

    var konnektorFactory = buildFactory(config);

    Gauge.builder(
            "konnektor_status",
            konnektorFactory,
            k -> {
              try {
                k.connect();
                return 1;
              } catch (Exception e) {
                return 0;
              }
            })
        .tag("konnektor", config.konnektorUri().toString())
        .register(registry);
    logger.atInfo().log("registered 'up' gauge for konnektor {}", config.konnektorUri().toString());

    var gauges =
        MultiGauge.builder("card_status")
            .tag("konnektor", config.konnektorUri().toString())
            .description("the status of all plugged in cards")
            .register(registry);

    logger.atInfo().log(
        "registered gauges for all cards in konnektor {}", config.konnektorUri().toString());

    gauges.register(new CardCheckGauges(config, konnektorFactory), true);
  }

  @Override
  public void close() {
    if (server != null) {
      server.stop();
    }
  }

  record KonnektorConfig(
      URI konnektorUri,
      String proxyAddress,
      int proxyPort,
      List<KeyManager> clientKeys,
      String workplaceId,
      String mandantId,
      String clientSystemId,
      String userId,
      String watchdogAddress,
      int watchdogPort) {}

  private KonnektorConfig loadConfig(ConfigProvider configProvider) {

    var address = configProvider.get("address").orElse("0.0.0.0");
    var port = configProvider.get("port").map(Integer::parseInt).orElse(8080);

    var uri = mustLoad("konnektor.uri").map(URI::create).orElseThrow();

    var proxyAddress = mustLoad("proxy.address").orElseThrow();

    var proxyPort = configProvider.get("proxy.port").map(Integer::parseInt).orElse(3128);

    var pw = configProvider.get("credentials.password").orElse("0000");

    var keys =
        configProvider
            .get("credentials.path")
            .map(Path::of)
            .or(() -> Optional.of(Path.of("./credentials.p12")))
            .map(p -> loadKeys(p, pw))
            .orElseThrow(configNotValid("credentials.path"));

    var workplace = configProvider.get("workplace.id").orElse("a");

    var clientSystem = configProvider.get("client_system.id").orElse("c");

    var mandant = configProvider.get("mandant.id").orElse("m");

    var user = configProvider.get("user.id").orElse("admin");

    return new KonnektorConfig(
        uri, proxyAddress, proxyPort, keys, workplace, mandant, clientSystem, user, address, port);
  }

  private Optional<String> mustLoad(String key) {

    var v = configProvider.get(key);
    if (v.isEmpty()) {

      throw configNotFound(key).get();
    }

    return v;
  }

  private <T extends RuntimeException> Supplier<T> configNotFound(String key) {
    return () -> (T) new IllegalStateException("configuration for '%s' not found".formatted(key));
  }

  private <T extends RuntimeException> Supplier<T> configNotValid(String key) {
    return () -> (T) new IllegalStateException("configuration for '%s' not valid".formatted(key));
  }

  private KonnektorConnectionFactory buildFactory(KonnektorConfig cfg) {
    return KonnektorConnectionFactoryBuilder.newBuilder()
        .clientKeys(cfg.clientKeys())
        .konnektorUri(cfg.konnektorUri())
        .proxyServer(cfg.proxyAddress(), cfg.proxyPort())
        .trustAllServers() // currently we don't validate the server's certificate
        .build();
  }

  private List<KeyManager> loadKeys(Path keystorePath, String password) {

    try {
      var ks = loadKeyStore(keystorePath, password);

      final KeyManagerFactory keyFactory =
          KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyFactory.init(ks, password.toCharArray());
      return Arrays.asList(keyFactory.getKeyManagers());
    } catch (UnrecoverableKeyException
        | CertificateException
        | IOException
        | KeyStoreException
        | NoSuchAlgorithmException e) {
      throw new IllegalStateException(
          "failed to load keystore from: %s".formatted(keystorePath), e);
    }
  }

  private KeyStore loadKeyStore(Path p, String password)
      throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {

    try (var fis = Files.newInputStream(p)) {
      var keyStore = KeyStore.getInstance("PKCS12");
      keyStore.load(fis, password.toCharArray());

      return keyStore;
    }
  }
}
