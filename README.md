# ePA Fachmodul Konnektor Watchdog

This is a simple application to expose Prometheus metrics for an ePA Fachmodul Konnektor.
Most notably:

- whether it is reachable and serving the service-discovery document (SDS)
- which SMC-B cards are plugged and whether they are verified or not

## Configuration

The application is configured by environment variables, here the available options and their defaults:

| name                                | description                                                                             | default             |
|-------------------------------------|-----------------------------------------------------------------------------------------|---------------------|
| `KONNEKTOR_WATCHDOG_LOG_LEVEL`*        | Log level for the entire application.                                                   | `INFO`              |
| `KONNEKTOR_WATCHDOG_ADDRESS`*          | Address to bind the Prometheus server to.                                               | `0.0.0.0`           |
| `KONNEKTOR_WATCHDOG_PORT`*             | Port to bind the Prometheus server to.                                                  | `8080`              |
| `KONNEKTOR_WATCHDOG_KONNEKTOR_URI`*    | URI of the Konnektor to watch, e.g. `https://10.0.0.1:443`.                             |                     |
| `KONNEKTOR_WATCHDOG_PROXY_ADDRESS`*    | Address of the forward proxy infront of the Konnektor, e.g. `127.0.0.1`.                |                     | 
| `KONNEKTOR_WATCHDOG_PROXY_PORT`*       | Port of the forward proxy infront of the Konnektor.                                     | `3128`              | 
| `KONNEKTOR_WATCHDOG_CREDENTIALS_PATH`* | The PKCS#12 keystore containing the TLS client certificate to connect to the Konnektor. | `./credentials.p12` | 
| `KONNEKTOR_WATCHDOG_WORKPLACE_ID`*     | The workplace ID configured in the Konnektor.                                           | `a`                 | 
| `KONNEKTOR_WATCHDOG_CLIENT_SYSTEM_ID`* | The client system ID configured in the Konnektor.                                       | `c`                 | 
| `KONNEKTOR_WATCHDOG_MANDANT_ID`*       | The mandant ID configured in the Konnektor.                                             | `m`                 | 
| `KONNEKTOR_WATCHDOG_USER_ID`*          | The user ID configured in the Konnektor.                                                | `admin`             | 

`*` required

## Wishlist

- option to run watchdog without forward proxy