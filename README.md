# Mailoverlord

Mailoverlord is an SMTP mail server targeted at testing and QA environments. Point an
application at it as its SMTP server and it will receive mail without ever delivering it,
storing each message in a database instead. The messages can be browsed in a web UI and
later released to their original recipients, or to different addresses, so testers can see
exactly what real users would have received.

## Requirements

* Java 25
* Maven 3.9+ (or use the pinned toolchain via [mise](https://mise.jdx.dev/): `mise install`)

Commands below use the Maven wrapper (`./mvnw`), but plain `mvn` works identically.

That's it. Mailoverlord is a self-contained executable JAR with an embedded Tomcat, so
there is no servlet container, application server, or JNDI to set up. Captured mail is
stored in an embedded in-memory H2 database.

The web UI is built from the `ui/` directory during the Maven build, using a Node
distribution the build downloads itself, so Node does not have to be installed.

An SMTP server to release to is only needed if you actually release messages.

## Build

```bash
./mvnw clean package
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

The properties worth knowing about, and the ones that differ from a plain Spring Boot default.
Everything else is commented in `application.yml`, which is the authoritative list.

| Property | Default | Description |
| --- | --- | --- |
| `MAILOVERLORD_PASSWORD` | — | The one shared sign-in password. Set this; the built-in default is published |
| `mailoverlord.security.mode` | `basic` | `basic`, `oidc`, `header` or `none` — see [docs/authentication.md](docs/authentication.md) |
| `mailoverlord.release.allowed-destinations` | empty (unrestricted) | Glob patterns restricting who mail may be released to; unset means any recipient, and warns at startup |
| `mailoverlord.smtp.max-message-size` | `10485760` | Bytes (10 MB) before the SMTP server refuses a message |
| `spring.mail.host` / `spring.mail.port` | `localhost` / `25` | Where released messages are sent |
| `spring.datasource.url` | `jdbc:h2:mem:mailoverlord;DB_CLOSE_DELAY=-1` | Message store; in-memory, so a restart discards captured mail |
| `SERVER_ADDRESS` / `MAILOVERLORD_SMTP_BIND_ADDRESS` | `127.0.0.1` | Loopback binds. The Docker image overrides them to `0.0.0.0` |

Ports: the web UI and API on `8080`, SMTP on `2025`, and the actuator management plane on
`8090` (loopback only, and separate so a caller who reaches the application port never sees an
actuator route — see [docs/authentication.md](docs/authentication.md#management-plane-actuator)).

Captured mail is kept in memory by default, so restarting Mailoverlord discards it. To keep
messages across restarts, point H2 at a file:

```bash
java -jar target/mailoverlord-2.0.0-SNAPSHOT.jar \
  '--spring.datasource.url=jdbc:h2:file:./data/mailoverlord;DB_CLOSE_ON_EXIT=FALSE'
```

Any database Hibernate supports will work, so for longer-lived data it is usually better to
override the datasource to Postgres or MySQL.

### Authentication

`basic` mode (the default) requires HTTP Basic on every request. Sign in as the documented
`spring.security.user.*` identity — an OPERATOR, so it can read, release and delete — or add
names to `operator-users` and `viewer-users`; everyone shares one password. Because Basic does
not pop its dialog for `fetch`, the UI sends the browser to `/login` when the API answers 401,
which is what makes the browser ask; after that it reloads and the calls carry the credentials.

Two roles apply: **OPERATOR** may read, release and delete; **VIEWER** may only read. The
management plane follows the same split.

`mode` also selects `oidc` (sign browsers in against Okta or Entra ID), `header` (trust the
identity a reverse proxy asserts) or `none` (no authentication at all, which is how the test
suite runs). All four are documented in [docs/authentication.md](docs/authentication.md).

To try OIDC or header mode without a real provider, `docker-compose.yml` ships `oidc` and
`proxy` profiles that stand them up against a throwaway Dex — see
[docs/compose.md](docs/compose.md#signing-in-with-docker-compose-oidc-and-header-modes).

## Docker

See [docs/compose.md](docs/compose.md) for the image, persisting captured mail in a
container, the per-engine compose profiles, and the Dex/oauth2-proxy auth profiles.

```bash
./mvnw spring-boot:build-image   # produces mailoverlord:2.0.0-SNAPSHOT
docker run --rm -p 127.0.0.1:8080:8080 -p 127.0.0.1:2025:2025 mailoverlord:2.0.0-SNAPSHOT
```

The application defaults to loopback binds and the image overrides them to `0.0.0.0`, so keep
the `127.0.0.1:` prefix on the publishes to stay private to the host.

## Native image

Mailoverlord also builds as a GraalVM native image, which is what the Paketo `native-image`
buildpack produces:

```bash
./mvnw -Pnative native:compile
```

This needs a GraalVM JDK with `native-image` available, and yields a self-contained
`target/mailoverlord` binary that starts in well under a second. Run it like the jar:

```bash
./target/mailoverlord
```

Two sets of runtime hints keep the image working, both registered in
`MailoverlordApplication`:

* `HibernateRuntimeHints` registers the `<Interface>_$logger` implementations that JBoss
  Logging generates for Hibernate, plus the message bundles they read. They are found by
  scanning hibernate-core, so a Hibernate upgrade cannot silently break them. Without them
  startup dies with `Invalid logger interface org.hibernate.jpa.internal.JpaLogger
  (implementation not found)`.
* `ApiModelRuntimeHints` registers the API response types. They are records, so Jackson
  needs both their accessors and their constructors to be reachable. Without them the JSON
  API answers with empty objects in a native image.

The build needs a GraalVM JDK with `native-image` available. If `JAVA_HOME` points at a
plain JDK, set `JAVA_HOME` or `GRAALVM_HOME` to the GraalVM installation first, otherwise
the build fails with `native-image is not installed in your JAVA_HOME`.

## Web UI

The UI is a Vue 3 and TypeScript single-page app in [`ui/`](ui), built by Vite into the
application jar, so one process serves the UI and the API and the browser only ever talks to
one origin. Captured mail appears without a reload: the list refreshes every ten seconds.

See [docs/ui.md](docs/ui.md) for the feature tour, the dev server, the Vitest suite, and how
the generated API types stay in step with the OpenAPI document.

## API

Five endpoints; the UI's TypeScript types are generated from the OpenAPI document at
`/v3/api-docs`.

* `GET /messages/list` — one page of message summaries. Accepts the standard Spring Data
  paging and sorting parameters (`?size=50&page=2`, `?sort=from,asc`) and optional
  `subject`, `from`, `to`, `receivedFrom` and `receivedTo` filters, combined with AND as
  case-insensitive substring matches. Defaults to 25 per page, newest first.
* `GET /messages/{id}` — one message in full, including its body. 404 if no message has
  that id.
* `POST /messages/delete` — delete messages, body `{"messageIds": [1, 2]}`. OPERATOR only.
* `POST /messages/release` — release messages. OPERATOR only. All fields are optional; with
  only `messageIds` the original addresses are used.
  * `overrideTo` / `overrideToAddresses` — replace the `To` recipients (comma separated).
  * `overrideFrom` / `overrideFromAddress` — replace the `From` address.
* `GET /v3/api-docs` — the OpenAPI document.

See [docs/api.md](docs/api.md) for the filtering rules, error shapes, the release allowlist
and the per-id outcome semantics.

## Documentation

The README is the quickstart and the configuration reference. Longer topics live in
[`docs/`](docs/README.md), versioned alongside the code so they cannot drift:

| Page | Covers |
| --- | --- |
| [authentication.md](docs/authentication.md) | The four auth modes, the OPERATOR/VIEWER split, the management plane |
| [compose.md](docs/compose.md) | The image, container persistence, the per-engine profiles, the Dex/oauth2-proxy profiles |
| [api.md](docs/api.md) | Endpoints, filtering, paging, error shapes, release semantics, the allowlist |
| [ui.md](docs/ui.md) | The Vue SPA: dev server, tests, generated types, Node version |

## Notes on this version

* Timestamps are ISO-8601 (`Instant`) rather than epoch milliseconds.
* The UI is a Vue 3 single-page app, not a server-rendered template. There is no
  `src/main/webapp`, no JSP, and no Thymeleaf.
* `receivedTimestamp` and `remoteAddress` are stored as `TIMESTAMP` and `VARCHAR`.
  An existing database from 1.x will need those columns migrated.
* `GET /messages/list` returns a `PageResponse` envelope rather than a bare array, and its
  rows no longer contain the `data` field. Use `GET /messages/{id}` for message bodies.
* The Spring Data REST resource at `/message` has been removed in favour of the hand
  written endpoints above.

## Tests

```bash
./mvnw test
```

Tests bind the SMTP port for real, so they cannot run two at a time or while the packaged
application is already running. If something is already listening on 2025, run the suite on
another port:

```bash
./mvnw -B verify -Dmailoverlord.smtp.port=2026 -Dspring.mail.port=2026
```

By default both the web API and SMTP server bind only to `127.0.0.1` (loopback) for safety;
use the properties in the table to expose them to other interfaces if needed. The recipient
allowlist for `POST /messages/release` is disabled by default (unrestricted) but warns at
startup when unset.

The UI has its own Vitest suite, which `./mvnw verify` runs as part of the build. To run it
on its own:

```bash
cd ui
npm test
```

Spec files live beside the code they cover and end in `.spec.ts`, so `ui/src/format.ts` is
tested by `ui/src/format.spec.ts`.
