# ePA Konnektor Watchdog

This is a simple application to expose Prometheus metrics for
a [Telematik Infrastruktur Konnektor (TI)](https://fachportal.gematik.de/hersteller-anbieter/komponenten-dienste/konnektor).

Most notably:

- Whether it is reachable and serving the service-discovery document (SDS).
- Which SMC-B cards are plugged and whether they are verified or not.

## Example

```prometheus
~> curl localhost:8080/metrics

# HELP card_status the status of all plugged in cards
# TYPE card_status gauge
card_status{card_handle="13f6842e-351a-4ed9-b822-21cd1fc5e510",holder="Beurmed für Computerangst",konnektor="https://10.156.123.103:443"} 1.0
card_status{card_handle="92a95a55-a0cc-403a-b6c8-453ed65ae613",holder="Examplicalea",konnektor="https://10.156.123.103:443"} 1.0
# HELP konnektor_status
# TYPE konnektor_status gauge
konnektor_status{konnektor="https://10.156.123.103:443"} 1.0
```

## Quickstart

> [!IMPORTANT]  
> Prerequisites:
> - running TI Konnektor
> - credentials to connect to the TI Konnektor

```shell
docker run \
  -v './credentials.p12:/secrets/credentials.p12' \
  -e 'KONNEKTOR_WATCHDOG_KONNEKTOR_URI=https://10.0.0.1:443' \
  -e 'KONNEKTOR_WATCHDOG_CREDENTIALS_PATH=/secrets/credentials.p12' \
  -e 'KONNEKTOR_WATCHDOG_PROXY_ADDRESS=172.0.0.42' \
  -e 'KONNEKTOR_WATCHDOG_CREDENTIALS_PASSWORD=0000' \
  -e 'KONNEKTOR_WATCHDOG_LOG_LEVEL=INFO' \
  -p 8080:8080 \
  ghcr.io/oviva-ag/konnektor-watchdog:latest
```

## API

- `/health` returning the health status of the watchdog, status 200 is healthy, 5xx is unhealthy
- `/metrics` Prometheus metrics
  in [text based format](https://github.com/prometheus/docs/blob/main/content/docs/instrumenting/exposition_formats.md#text-based-format).

## Configuration

The application is configured by environment variables.

Available options and their defaults:

| name                                       | description                                                                             | default             |
|--------------------------------------------|-----------------------------------------------------------------------------------------|---------------------|
| `KONNEKTOR_WATCHDOG_LOG_LEVEL`*            | Log level for the entire application.                                                   | `INFO`              |
| `KONNEKTOR_WATCHDOG_ADDRESS`*              | Address to bind the Prometheus server to.                                               | `0.0.0.0`           |
| `KONNEKTOR_WATCHDOG_PORT`*                 | Port to bind the Prometheus server to.                                                  | `8080`              |
| `KONNEKTOR_WATCHDOG_KONNEKTOR_URI`*        | URI of the Konnektor to watch, e.g. `https://10.0.0.1:443`.                             |                     |
| `KONNEKTOR_WATCHDOG_PROXY_ADDRESS`*        | Address of the forward proxy infront of the Konnektor, e.g. `127.0.0.1`.                |                     | 
| `KONNEKTOR_WATCHDOG_PROXY_PORT`*           | Port of the forward proxy infront of the Konnektor.                                     | `3128`              | 
| `KONNEKTOR_WATCHDOG_CREDENTIALS_PATH`*     | The PKCS#12 keystore containing the TLS client certificate to connect to the Konnektor. | `./credentials.p12` | 
| `KONNEKTOR_WATCHDOG_CREDENTIALS_PASSWORD`* | The password of the PKCS#12 keystore containing the TLS client certificate.             | `0000`              | 
| `KONNEKTOR_WATCHDOG_WORKPLACE_ID`*         | The workplace ID configured in the Konnektor.                                           | `a`                 | 
| `KONNEKTOR_WATCHDOG_CLIENT_SYSTEM_ID`*     | The client system ID configured in the Konnektor.                                       | `c`                 | 
| `KONNEKTOR_WATCHDOG_MANDANT_ID`*           | The mandant ID configured in the Konnektor.                                             | `m`                 | 
| `KONNEKTOR_WATCHDOG_USER_ID`*              | The user ID configured in the Konnektor.                                                | `admin`             | 

`*` required

## Wishlist

- option to run watchdog without forward proxy
- better timeouts in case of proxy or connector completely down