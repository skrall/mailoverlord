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

| Property | Default | Description |
| --- | --- | --- |
| `mailoverlord.smtp.port` | `2025` | Port the embedded SMTP server listens on to collect incoming mail |
| `mailoverlord.smtp.max-message-size` | `10485760` | Maximum message size in bytes (10 MB) before Mailoverlord refuses it |
| `spring.mail.host` | `localhost` | Host released messages are sent to |
| `spring.mail.port` | `25` | Port released messages are sent to |
| `server.address` | `127.0.0.1` | IP address the web server binds to |
| `server.port` | `8080` | Web UI and API port |
| `mailoverlord.smtp.bind-address` | `127.0.0.1` | IP address the embedded SMTP server binds to |
| `mailoverlord.release.allowed-destinations` | empty (unrestricted) | Comma-separated list of glob patterns; if unset, all destinations are allowed |
| `spring.datasource.url` | `jdbc:h2:mem:mailoverlord;DB_CLOSE_DELAY=-1` | Message store |
| `mailoverlord.security.mode` | `basic` | Authentication mechanism: `basic` requires HTTP Basic on every request, `oidc` signs browsers in against an identity provider, `header` trusts the identity a reverse proxy asserts, `none` turns authentication off (how the test suite runs) |
| `mailoverlord.security.operator-users` | empty | Usernames that may also release and delete; they share the password below |
| `mailoverlord.security.viewer-users` | empty | Usernames that may only read |
| `mailoverlord.security.roles-claim` | `groups` | OIDC claim that carries group membership (Okta `groups`, Entra ID `roles`) |
| `mailoverlord.security.operator-groups` | empty | OIDC or trusted-header group names that may release and delete |
| `mailoverlord.security.header` | empty | Trusted-header mode: HTTP header the proxy sets with the signed-in user's name (`X-Auth-Request-User`, `X-Remote-User`) |
| `mailoverlord.security.groups-header` | empty | Trusted-header mode: header carrying the user's comma-separated groups; absent, nobody is an OPERATOR |
| `mailoverlord.security.trusted-proxies` | empty | Trusted-header mode: CIDRs whose connections come from the proxy; required, and a matching header from anywhere else is answered 401 |
| `spring.security.user.name` | `operator` | The documented sign-in identity, always an OPERATOR |
| `spring.security.user.password` | `change-me-on-deploy` | The one shared password. Override it with `MAILOVERLORD_PASSWORD`; the generated `spring.security.user.password` also works |

### Authentication

`basic` mode requires HTTP Basic on every request. Sign in once as the documented
`spring.security.user.*` identity (an OPERATOR, so it can read, release, and delete) or as a
name added to `operator-users` or `viewer-users`; everyone shares the one
`spring.security.user.password`. Set `mailoverlord.security.mode=none` only to run with no
authentication at all.

Because Basic does not pop its dialog for `fetch`, the UI sends the browser to `/login` when the
API answers 401, which is what makes the browser ask for credentials; after that it reloads and
the calls carry the credentials. A VIEWER reaching release or delete is told it is a permission
problem. `curl --user operator:password ...` works the same way against the API. See #56.

### Browser login against a provider (OIDC)

The same jar can sign people in at the browser through Okta or Entra ID instead, with the
authorization code flow and the vendor difference living in configuration. The SPA never sees a
token: the provider hands the browser a session cookie, and that cookie is all the API needs.
Set `mailoverlord.security.mode=oidc`, configure one client registration, and tell the provider
the redirect URI `{base-url}/login/oauth2/code/{registrationId}`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          okta:                    # or "entra" for Microsoft Entra ID
            client-id: ...
            client-secret: ...
            provider: okta
        provider:
          okta:
            issuer-uri: https://yourdomain.okta.com/oauth2/default
          # entra:
          #   issuer-uri: https://login.microsoftonline.com/{tenant-id}/v2.0
```

`issuer-uri` is the whole vendor difference: Spring reads the provider's
`/.well-known/openid-configuration` from it and derives every endpoint. For Entra ID, create the
app registration under *App registrations*, add this redirect URI under *Authentication → Web*,
and under *App roles* define the operator group you name below so it lands in the `roles` claim
(Entra's `groups` claim needs admin consent and caps at 150 members; `roles` needs neither).

Which groups may release and delete is decided per deployment. Set the claim the provider uses
and the group names that carry the OPERATOR role; everyone else who can sign in reads only:

```yaml
mailoverlord:
  security:
    roles-claim: groups           # Okta default; use "roles" for Entra ID
    operator-groups: [mailoverlord-operators]
```

The claim is read whether it arrives as an array (`groups: [mailoverlord-operators]`) or as a
single string, and matched case-insensitively. An authenticated user is always a VIEWER, so
reads never depend on what the provider chose to put in a claim.

Logout is `POST /logout` with the CSRF token (the `XSRF-TOKEN` cookie echoed back as
`X-XSRF-TOKEN`, which the UI does for you). This clears the local session but leaves the IdP
session alive, so signing in again is instant; provider side, OIDC back-channel logout is a
separate piece of work. The session cookie's `SameSite` default is fine because the UI and API
share one origin; if they ever move apart, set `server.servlet.session.cookie.same-site` to
match.

To send released mail back to Mailoverlord itself, set `--spring.mail.port=2025`; the
released messages are then re-captured and show up in the UI again.

### Trusting a reverse proxy's identity (header)

The same jar can also live behind a reverse proxy that signs people in, with Mailoverlord
trusting the identity the proxy put on the request. This is the smallest possible thing to sit
behind something like oauth2-proxy or an nginx `auth_request`: the SPA never sees a token, and
the app holds no session of its own — the proxy *is* the sign-in page.

Set `mailoverlord.security.mode=header`, name the two headers, and say whose connections should
be believed:

```yaml
mailoverlord:
  security:
    mode: header
    header: X-Auth-Request-User        # the signed-in user, set by the proxy
    groups-header: X-Auth-Request-Groups
    operator-groups: [mailoverlord-operators]
    trusted-proxies: [127.0.0.1/32]    # where the proxy connects from
server:
  forward-headers-strategy: framework  # parse X-Forwarded-* for the source check
```

The proxy authenticates and must **overwrite** the identity headers on every request it
forwards — dropping any identity header a client sent rather than passing it through. oauth2-proxy
sets `X-Auth-Request-User` and `X-Auth-Request-Groups` itself; with nginx, derive them from the
sign-in cookie or an `auth_request` endpoint. A client that can reach Mailoverlord's port
directly must never be able to present a header nobody wrote. Header names are up to you:
`X-Auth-Request-User`, `X-Forwarded-User`, `X-Remote-User`, `SM_USER` all work, as long as proxy
and app agree on the spelling.

Three things guard the boundary, and all three are deliberate:

1. **Source allowlist.** The identity header is read only on requests whose connection came from
   a machine in `trusted-proxies`. A request carrying the same header from any other address is
   answered 401 whatever it says: the address in the four bytes of the TCP peer is the one thing
   the client cannot rewrite, so it is what vouches for the header.
2. **Bind the app to the proxy only.** Keep Mailoverlord on loopback (its default) or a private
   address behind the proxy. The header is only as trustworthy as the path it arrived on, so the
   link between proxy and app must not be reachable from the internet.
3. **Let the framework parse forwarded headers.** With `server.forward-headers-strategy:
   framework` the app parses `X-Forwarded-For` and friends and uses the *real* connection peer
   for the allowlist, instead of taking a client-spoofable header on trust.

`trusted-proxies` ships empty, and `mode: header` without both an identity header and an
allowlist is refused at startup rather than run — a header nobody vouches for would be one line
of forgery away.

A 401 under `header` mode names no login page for the browser to go to (the proxy owns that), so
the UI stays put and shows the reason instead of reloading in a loop.

Two limits are chosen, not accidental. The app never sees the proxy's session and cannot tell
the upstream is gone, so a wrong `trusted-proxies` entry fails closed rather than slow:
outsiders get 401s and an operator notices the door is closed, not a lingering hint of access.
And anyone who can reach a trusted proxy can act as whoever the proxy signs in as — compromising
the edge is compromising Mailoverlord, by design. See #58.

Captured mail is kept in memory by default, so restarting Mailoverlord discards it. To
keep messages across restarts, point H2 at a file instead:

```bash
java -jar target/mailoverlord-2.0.0-SNAPSHOT.jar \
  '--spring.datasource.url=jdbc:h2:file:./data/mailoverlord;DB_CLOSE_ON_EXIT=FALSE'
```

Any database Hibernate supports will work, so for longer-lived data it is usually better
to override the datasource to Postgres or MySQL.

## Docker

Build an OCI image with [Cloud Native Buildpacks](https://buildpacks.io):

```bash
./mvnw spring-boot:build-image
docker run --rm -p 127.0.0.1:8080:8080 -p 127.0.0.1:2025:2025 mailoverlord:2.0.0-SNAPSHOT
```

The application defaults to loopback binds on the host, and the image overrides them to
`0.0.0.0` via `SERVER_ADDRESS` and `MAILOVERLORD_SMTP_BIND_ADDRESS`: a container that only
listens on its own loopback cannot be reached through a published port. Loopback-only access
on the host then comes from the `-p 127.0.0.1:...:...` publishes, which keep the host side
private; publish without the `127.0.0.1:` prefix to expose the ports to other hosts.

The image has no shell, so `docker exec -it <container> sh` will not work. To inspect the
JVM directly, override the entrypoint with the buildpack's JRE. The application is an
exploded layered jar rather than a single file, so it is launched by class name:

```bash
docker run --rm -p 127.0.0.1:8080:8080 -p 127.0.0.1:2025:2025 \
  --entrypoint /layers/paketo-buildpacks_bellsoft-liberica/jre/bin/java \
  mailoverlord:2.0.0-SNAPSHOT \
  -cp '/workspace/BOOT-INF/classes:/workspace/BOOT-INF/lib/*:/workspace' \
  org.springframework.boot.loader.launch.JarLauncher
```

The JRE path is buildpack specific and may change between buildpack versions.

### Persisting captured mail in a container

The in-memory default works in a container, but a file-backed H2 does **not**, and the
failure is not obvious. The image runs as uid 1002 while `/workspace` is owned by uid 1001,
so the working directory is not writable, H2 cannot create its `data` directory, and startup
dies while building the `EntityManagerFactory`:

```
Error while creating file "/workspace/data"
```

This looks like a JPA problem but is only a filesystem permission one.

A named volume does not fix it either — Docker creates the volume root-owned, so the app
still gets `AccessDeniedException: /data/mailoverlord.mv.db`. Run the container as root
and the volume is writable:

```bash
docker volume create mailoverlord-data
docker run --rm --user 0 -p 127.0.0.1:8080:8080 -p 127.0.0.1:2025:2025 \
  -v mailoverlord-data:/data \
  -e SPRING_DATASOURCE_URL='jdbc:h2:file:/data/mailoverlord;DB_CLOSE_ON_EXIT=FALSE' \
  mailoverlord:2.0.0-SNAPSHOT
```

Captured mail then survives container restarts. The alternative, which avoids running as
root, is to bind-mount a host directory that the image's uid 1002 already owns, or to point
`SPRING_DATASOURCE_URL` at a real Postgres or MySQL, which is the better choice anyway for
anything long-lived.

### Databases with Docker Compose

The image bundles JDBC drivers for Postgres, MySQL, MariaDB, Oracle and SQL Server alongside
the built-in H2, and `docker-compose.yml` offers one profile per engine:

```bash
./mvnw spring-boot:build-image   # produce the mailoverlord:2.0.0-SNAPSHOT image
cp .env.example .env             # then set MAILOVERLORD_PASSWORD and a database password
docker compose --profile postgres up
```

Each profile starts the database next to the application, waits for the database to report
healthy, and points the application at it. Only the application's ports are published, and
only on the host's loopback (`127.0.0.1:8080` and `127.0.0.1:2025`); the database stays on
the compose network. Data lives in a named volume per engine, so it survives `down` and `up`;
`docker compose --profile <engine> down -v` deletes it.

Schema updates only add what is missing: a volume that was created before a column type
change keeps its old DDL. If Hibernate ever grew a column's declared size (the message body
length was fixed once), recreate the volume with `down -v` rather than expecting an in-place
alter.

| Profile     | Image                          | JDBC URL / notes                                            |
|-------------|--------------------------------|-------------------------------------------------------------|
| `postgres`  | `postgres:17`                  | `jdbc:postgresql://postgres:5432/mailoverlord`              |
| `mysql`     | `mysql:8.4`                    | `...?allowPublicKeyRetrieval=true&useSSL=false`             |
| `mariadb`   | `mariadb:11`                   | `jdbc:mariadb://mariadb:3306/mailoverlord`                  |
| `oracle`    | `gvenzl/oracle-free:23-slim`   | slow first start; large image                               |
| `mssql`     | `mcr.microsoft.com/mssql/server:2022-latest` | amd64 only                 |
| `h2`        | — (the app ships H2)           | file-backed H2 on a volume, run as root                     |

Secrets come from `.env`, which is git-ignored; the committed `.env.example` is the
template. `MAILOVERLORD_PASSWORD` is required by every profile. Each engine's password is
only needed for its own profile (the database container refuses to start without it), and the
`h2` profile needs none. SQL Server's password must satisfy its own complexity rules.

A few caveats from the table deserve detail:

- `mssql` is the only amd64-only image here; Apple Silicon runs it under emulation.
  Its entrypoint creates the `mailoverlord` database on first start (SQL Server has no
  `CREATE DATABASE IF NOT EXISTS`-style flow from a volume, so the app's
  `databaseName=mailoverlord` would otherwise fail to connect), and the healthcheck waits
  for that database rather than just the server. The URL passes
  `encrypt=false;trustServerCertificate=true` because mssql-jdbc 10+ insists on TLS by
  default and the internal compose network does not carry certificates.
- `mysql` passes `allowPublicKeyRetrieval=true&useSSL=false`: connector/J 8+ needs that
  flag for `caching_sha2_password` over the compose network's non-TLS socket.
- `h2` runs the exact same file-backed setup as the section above (root, named volume), so
  it shares that caveat.

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

The UI is a Vue 3 and TypeScript single-page app in [`ui/`](ui). It is built by Vite into
the application jar, so the same process serves the UI and the API and the browser only
ever talks to one origin. There is no CORS configuration, and no second container to run.

* `/` shows captured messages newest first, 25 to a page, sortable by received time, sender,
  recipient and subject. Select rows to release or delete them, click one to read it in the
  side panel, and page with the standard Spring Data parameters. The list refreshes every
  ten seconds, so captured mail appears without a manual reload.
* The list endpoint returns summaries only, never message bodies, so a page of large
  messages stays small. Opening a message fetches its body on demand.
* The UI follows the browser's colour scheme preference, with no toggle. Every colour is a
  custom property in [`ui/src/style.css`](ui/src/style.css), and the dark values are in a
  `prefers-color-scheme: dark` block beside the light ones.

## Working on the UI

```bash
cd ui
npm install
npm run dev
```

The dev server runs on port 5173 and proxies `/messages` and `/v3` to the application on
port 8080, so start Mailoverlord separately with `./mvnw spring-boot:run` and the browser
still sees a single origin. CSS and component changes reload without rebuilding the jar or
the native image.

`-Dskip.ui=true` builds the Java side without rebuilding the UI, which is useful when only
backend code changed.

```bash
npm test
```

runs the Vitest suite, which is also part of `./mvnw verify`. The spec files are typechecked
along with the rest of the UI by `npm run build`.

### Generated API types

The TypeScript types come from the OpenAPI document that
[springdoc](https://springdoc.org/) publishes at `/v3/api-docs`, via
[openapi-typescript](https://openapi-ts.dev/). After changing a request or response type on
the server, regenerate them:

```bash
cd ui
npm run generate:spec   # writes openapi.json, starting the app if it is not running
npm run generate:types  # writes src/api/schema.d.ts
```

Both files are committed, and CI regenerates them and fails if they differ, so the types
cannot silently fall behind the API.

## API

* `GET /messages/list` — one page of message summaries. Returns a `PageResponse` with
  `content`, `number`, `size`, `totalElements`, `totalPages`, `first` and `last`. Accepts
  the standard Spring Data paging and sorting parameters, e.g. `?size=50&page=2` (0-based)
  or `?sort=from,asc`. Defaults to 25 per page, newest first.
* `GET /messages/{id}` — one message in full, including its `body`. Returns 404 if no
  message has that id.
* `POST /messages/delete` — delete messages, body `{"messageIds": [1, 2]}`.
* `POST /messages/release` — release messages. All fields are optional; with only
  `messageIds` the original addresses are used.
  * `overrideTo` / `overrideToAddresses` — replace the `To` recipients (comma separated).
  * `overrideFrom` / `overrideFromAddress` — replace the `From` address.
* `GET /v3/api-docs` — the OpenAPI document the UI types are generated from.

Summaries carry a `subject`, decoded from the RFC 2047 encoding the SMTP server stores, and
a `sizeBytes`.

The subject is a column rather than a header parsed out of the stored content on each
request. The database cannot read a header out of a MIME blob, so ordering by subject has to
become an `ORDER BY`, and it has to happen in SQL rather than in Java after the rows come
back: a page of messages cannot be sorted by subject otherwise. It is decoded and truncated
once, when the message is captured.

`sizeBytes` is the length of the stored content, so listing a page still loads each row's
blob even though nothing else in a summary needs it.

Releasing mail that Mailoverlord cannot reach returns HTTP 200 with
`{"successful": false, "errorMessage": "..."}` and leaves the messages captured, so you
can fix the target and try again.

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
application is already running. By default both the web API and SMTP server bind only to
`127.0.0.1` (loopback) for safety; use the properties in the table to expose them to other
interfaces if needed. The recipient allowlist for `POST /messages/release` is disabled by
default (unrestricted) but warns at startup when unset.

The UI has its own Vitest suite, which `./mvnw verify` runs as part of the build. To run it
on its own:

```bash
cd ui
npm test
```

Spec files live beside the code they cover and end in `.spec.ts`, so `ui/src/format.ts` is
tested by `ui/src/format.spec.ts`.
