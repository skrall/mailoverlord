# Mailoverlord

Mailoverlord is an SMTP mail server targeted at testing and QA environments. Point an
application at it as its SMTP server and it will receive mail without ever delivering it,
storing each message in a database instead. The messages can be browsed in a web UI and
later released to their original recipients, or to different addresses, so testers can see
exactly what real users would have received.

## Requirements

* Java 25
* Maven 3.9+ (or use the pinned toolchain via [mise](https://mise.jdx.dev/): `mise install`)

That's it. Mailoverlord is a self-contained executable JAR with an embedded Tomcat, so
there is no servlet container, application server, or JNDI to set up. Captured mail is
stored in an embedded H2 database under `./data`.

An SMTP server to release to is only needed if you actually release messages.

## Build

```bash
mvn clean package
```

This produces `target/mailoverlord-2.0.0-SNAPSHOT.jar`.

## Run

```bash
java -jar target/mailoverlord-2.0.0-SNAPSHOT.jar
```

Mailoverlord then listens for SMTP on port `2025` and serves its web UI on
[http://localhost:8080](http://localhost:8080).

Configure your application to send its email to the Mailoverlord host, port `2025`.

## Configuration

Everything is set in `src/main/resources/application.yml`, and every value can be
overridden with the usual Spring Boot mechanisms, for example:

```bash
java -jar target/mailoverlord-2.0.0-SNAPSHOT.jar \
  --server.port=8080 \
  --mailoverlord.smtp.port=2525
```

| Property | Default | Description |
| --- | --- | --- |
| `mailoverlord.smtp.port` | `2025` | Port the embedded SMTP server listens on to collect incoming mail |
| `spring.mail.host` | `localhost` | Host released messages are sent to |
| `spring.mail.port` | `25` | Port released messages are sent to |
| `server.port` | `8080` | Web UI and API port |
| `spring.datasource.url` | `jdbc:h2:file:./data/mailoverlord` | Message store |

To send released mail back to Mailoverlord itself, set `--spring.mail.port=2025`; the
released messages are then re-captured and show up in the UI again.

To point Mailoverlord at a different database, override the datasource. The H2 defaults
are deliberately low-friction, but any database Hibernate supports will work.

## Web UI

* `/` lists captured messages, newest first, 20 to a page. Page with the standard
  Spring Data parameters, e.g. `/?size=50&page=2` (0-based) or `/?sort=from,asc`.

## API

* `GET /messages/list` — captured messages as JSON. Accepts the same paging and sorting
  parameters as the UI.
* `POST /messages/delete` — delete messages, body `{"messageIds": [1, 2]}`.
* `POST /messages/release` — release messages. All fields are optional; with only
  `messageIds` the original addresses are used.
  * `overrideTo` / `overrideToAddresses` — replace the `To` recipients (comma separated).
  * `overrideFrom` / `overrideFromAddress` — replace the `From` address.
* `GET /message` and `GET /message/{id}` — the same messages as a Spring Data REST
  resource, including HAL navigation and paging.

Releasing mail that Mailoverlord cannot reach returns HTTP 200 with
`{"successful": false, "errorMessage": "..."}` and leaves the messages captured, so you
can fix the target and try again.

## Notes on this version

* Timestamps are ISO-8601 (`Instant`) rather than epoch milliseconds.
* The UI is Thymeleaf; there is no `src/main/webapp` and no JSP.
* `receivedTimestamp` and `remoteAddress` are stored as `TIMESTAMP` and `VARCHAR`.
  An existing database from 1.x will need those columns migrated.

## Tests

```bash
mvn test
```

Tests bind the SMTP port for real, so they cannot run two at a time or while the packaged
application is already running.
