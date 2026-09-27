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
| `spring.mail.host` | `localhost` | Host released messages are sent to |
| `spring.mail.port` | `25` | Port released messages are sent to |
| `server.port` | `8080` | Web UI and API port |
| `spring.datasource.url` | `jdbc:h2:mem:mailoverlord;DB_CLOSE_DELAY=-1` | Message store |

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
docker run --rm -p 8080:8080 -p 2025:2025 mailoverlord:2.0.0-SNAPSHOT
```

The image has no shell, so `docker exec -it <container> sh` will not work. To inspect the
JVM directly, override the entrypoint with the buildpack's JRE. The application is an
exploded layered jar rather than a single file, so it is launched by class name:

```bash
docker run --rm -p 8080:8080 -p 2025:2025 \
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
docker run --rm --user 0 -p 8080:8080 -p 2025:2025 \
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
* `ViewModelRuntimeHints` registers the model types that the Thymeleaf template reads
  through SpEL. Without them every request for the UI fails with `EL1008E: Property or field
  'page' cannot be found on object of type 'MessageViewData'`.

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
./mvnw test
```

Tests bind the SMTP port for real, so they cannot run two at a time or while the packaged
application is already running.
