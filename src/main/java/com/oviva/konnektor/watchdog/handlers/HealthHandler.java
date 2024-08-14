package com.oviva.konnektor.watchdog.handlers;

import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

public class HealthHandler implements HttpHandler {

  private IsUp isUp;

  @Override
  public void handleRequest(HttpServerExchange exchange) throws Exception {

    var up = false;
    try {
      up = isUp.isUp();
    } catch (Exception e) {
      // all fine, its down
    }

    var status = up ? 200 : 503;
    exchange.setStatusCode(status);
    exchange.endExchange();
  }

  interface IsUp {
    boolean isUp();
  }
}
