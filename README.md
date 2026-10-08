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
| `mailoverlord.security.mode` | `basic` | Authentication mechanism: `basic` requires HTTP Basic on every request, `none` turns authentication off (how the test suite runs) |
| `mailoverlord.security.operator-users` | empty | Usernames that may also release and delete; they share the password below |
| `mailoverlord.security.viewer-users` | empty | Usernames that may only read |
| `spring.security.user.name` | `operator` | The documented sign-in identity, always an OPERATOR |
| `spring.security.user.password` | `change-me-on-deploy` | The one shared password. Override it with `MAILOVERLORD_PASSWORD`; the generated `spring.security.user.password` also works |

### Authentication

Every request needs HTTP Basic. Sign in once as the documented `spring.security.user.*`
identity (an OPERATOR, so it can read, release, and delete) or as a name added to
`operator-users` or `viewer-users`; everyone shares the one `spring.security.user.password`.
Set `mailoverlord.security.mode=none` only to run with no authentication at all.

Because Basic does not pop its dialog for `fetch`, the UI sends the browser to `/login` when
the API answers 401, which is what makes the browser ask for credentials; after that it
reloads and the calls carry the credentials. A VIEWER reaching release or delete is told it
is a permission problem. `curl --user operator:password ...` works the same way against the
API. See #56.

To send released mail back to Mailoverlord itself, set `--spring.mail.port=2025`; the
released messages are then re-captured and show up in the UI again.

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
