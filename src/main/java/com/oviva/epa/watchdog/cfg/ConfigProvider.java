package com.oviva.epa.watchdog.cfg;

import java.util.Optional;

public interface ConfigProvider {
  Optional<String> get(String name);
}
