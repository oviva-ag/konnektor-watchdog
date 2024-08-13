package com.oviva.epa.watchdog.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.spi.ContextAwareBase;
import com.google.auto.service.AutoService;
import java.util.Optional;

@AutoService(Configurator.class)
public class LogConfigurator extends ContextAwareBase implements Configurator {
  @Override
  public ExecutionStatus configure(LoggerContext context) {
    addInfo("Setting up default configuration.");

    var ca = new ConsoleAppender<ILoggingEvent>();
    ca.setContext(context);
    ca.setName("console");

    var encoder = new JsonEncoder();
    encoder.setContext(context);

    ca.setEncoder(encoder);
    ca.start();

    var rootLogger = context.getLogger(Logger.ROOT_LOGGER_NAME);
    rootLogger.addAppender(ca);

    rootLogger.setLevel(getLevel());

    return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
  }

  private Level getLevel() {
    return Optional.ofNullable(System.getenv("EPA_FM_WATCHDOG_LOG_LEVEL"))
        .map(Level::valueOf)
        .orElse(Level.INFO);
  }
}
