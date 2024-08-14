package com.oviva.konnektor.watchdog.cfg;

import java.util.Optional;

public interface ConfigProvider {
  Optional<String> get(String name);
}
