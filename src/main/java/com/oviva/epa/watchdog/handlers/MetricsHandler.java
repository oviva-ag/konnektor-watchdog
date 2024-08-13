package com.oviva.epa.watchdog.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import java.nio.charset.StandardCharsets;

public class MetricsHandler implements HttpHandler {

  private final PrometheusMetrics metrics;

  public MetricsHandler(PrometheusMetrics metrics) {
    this.metrics = metrics;
  }

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {

    var res = metrics.scrape();

    exchange.setStatusCode(200);
    exchange
        .getResponseHeaders()
        .put(HttpString.tryFromString("content-type"), "text/plain; version=0.0.4");
    exchange.getResponseSender().send(res, StandardCharsets.UTF_8);
    exchange.endExchange();
  }

  public interface PrometheusMetrics {
    String scrape();
  }
}
