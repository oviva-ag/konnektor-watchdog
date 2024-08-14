package com.oviva.epa.watchdog;

import com.oviva.epa.client.KonnektorService;
import com.oviva.epa.client.KonnektorServiceBuilder;
import com.oviva.epa.client.konn.KonnektorConnection;
import com.oviva.epa.client.konn.KonnektorConnectionFactory;
import com.oviva.epa.client.model.Card;
import com.oviva.epa.client.model.KonnektorException;
import com.oviva.epa.client.model.PinStatus;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.Tags;
import jakarta.ws.rs.ProcessingException;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class CardCheckGauges implements Iterable<MultiGauge.Row<?>> {

  private Logger logger = LoggerFactory.getLogger(CardCheckGauges.class);

  private final Main.KonnektorConfig config;
  private final KonnektorConnectionFactory konnektorFactory;

  CardCheckGauges(Main.KonnektorConfig config, KonnektorConnectionFactory konnektorFactory) {
    this.config = config;
    this.konnektorFactory = konnektorFactory;
  }

  @Override
  public Iterator<MultiGauge.Row<?>> iterator() {
    try {
      logger.atDebug().log("connecting to {}", config.konnektorUri());
      var conn = konnektorFactory.connect();

      var konnektorService = buildService(config, conn);

      logger.atDebug().log("fetching cards from {}", config.konnektorUri());
      var cards = konnektorService.getCardsInfo();

      var l =
          cards.stream()
              .filter(c -> c.type() == Card.CardType.SMC_B)
              .map(c -> checkCard(konnektorService, c))
              // explicit .collect(...) to make Java generics work
              .collect(ArrayList<MultiGauge.Row<?>>::new, ArrayList::add, ArrayList::addAll);

      logger.atInfo().log(
          "updated cards of konnektor {}, found {} cards", config.konnektorUri(), l.size());

      return l.iterator();
    } catch (KonnektorException | ProcessingException e) {
      logger
          .atError()
          .setCause(e)
          .log("failed to update cards of konnektor {}", config.konnektorUri());
      return Collections.emptyIterator();
    }
  }

  private KonnektorService buildService(Main.KonnektorConfig cfg, KonnektorConnection conn) {

    var userAgent = userAgent();
    logger.atDebug().log("client using user-agent: {}", userAgent);

    return KonnektorServiceBuilder.newBuilder()
        .connection(conn)
        .workplaceId(cfg.workplaceId())
        .clientSystemId(cfg.clientSystemId())
        .mandantId(cfg.mandantId())
        .userId(cfg.userId())
        .userAgent(userAgent)
        .build();
  }

  private String userAgent() {
    var agent =
        Optional.ofNullable(Main.class.getPackage().getImplementationTitle())
            .orElse("epa-fm-watchdog")
            .toUpperCase(Locale.ROOT)
            .replaceAll("[^A-Z0-9]", "_");

    var version =
        Optional.ofNullable(Main.class.getPackage().getImplementationVersion()).orElse("0.0.1");
    return "%s/%s".formatted(agent, version);
  }

  private MultiGauge.Row<Card> checkCard(KonnektorService service, Card card) {
    var tags = Tags.of("holder", card.holderName(), "card_handle", card.handle());
    try {
      return MultiGauge.Row.of(
          tags,
          card,
          (Card c) -> {
            try {
              var status = service.verifySmcPin(card.handle());
              if (status == PinStatus.VERIFIED) {
                return 1.0;
              }
              return 0.0;
            } catch (KonnektorException e) {
              return 0.0;
            }
          });
    } catch (Exception e) {
      return MultiGauge.Row.of(tags, (Card) null, s -> 0.0);
    }
  }
}
