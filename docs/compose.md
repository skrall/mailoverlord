# Running in Docker

Everything here assumes the OCI image built by [Cloud Native Buildpacks](https://buildpacks.io).

```bash
./mvnw spring-boot:build-image   # produces mailoverlord:2.0.0-SNAPSHOT
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

## Persisting captured mail in a container

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

## Databases with Docker Compose

The image bundles JDBC drivers for Postgres, MySQL, MariaDB, Oracle and SQL Server alongside
the built-in H2, and `docker-compose.yml` offers one profile per engine:

```bash
./mvnw spring-boot:build-image   # produce the mailoverlord:2.0.0-SNAPSHOT image
cp .env.example .env             # then set MAILOVERLORD_PASSWORD and a database password
docker compose --profile postgres up
```

Each profile starts the database next to the application, waits for the database to report
healthy, and points the application at it. Only the application's ports are published, and
only on the host's loopback (`127.0.0.1:8080`, `127.0.0.1:2025`, and the actuator management
port `127.0.0.1:8090`); the database stays on the compose network. Data lives in a named volume
per engine, so it survives `down` and `up`; `docker compose --profile <engine> down -v` deletes
it.

Each application container carries a Docker healthcheck that probes
`http://127.0.0.1:8090/actuator/health`, so `docker compose ps` reports `(healthy)` once the app
and its database are up. The image is distroless — no shell, no `curl` — so the probe execs the
Paketo [tiny-health-checker](https://github.com/dmikusa/tiny-health-checker) binary
(`/workspace/health-check`) that the `health-checker` buildpack installs at build time (see
`pom.xml`); its target comes from the `THC_*` variables in `docker-compose.yml`.

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

## Signing in with Docker Compose (OIDC and header modes)

The engine profiles above all run the app in its default `basic` mode. Two further profiles
cover the other two authentication modes against a throwaway [Dex](https://dexidp.io) identity
provider, and combine with any engine:

```bash
# mode: oidc — the app itself signs the browser in against Dex
MAILOVERLORD_SECURITY_MODE=oidc   docker compose --profile postgres --profile oidc up

# mode: header — oauth2-proxy signs the browser in and asserts the identity
MAILOVERLORD_SECURITY_MODE=header docker compose --profile postgres --profile proxy up
```

A compose profile selects which services run, not another service's environment, so the mode
has to be set alongside the profile — in `.env` or on the command line — rather than inferred
from it. `oidc` starts Dex; `proxy` starts Dex and oauth2-proxy.

Once up:

| Where                                        | URL                        |
|----------------------------------------------|----------------------------|
| App, directly                                | `http://localhost:8080`    |
| App, through oauth2-proxy (only `proxy`)     | http://localhost:4180      |
| Dex                                           | http://localhost:5556/dex  |

Dex holds two users, both with the password `password`: `operator@example.com`, a member of
`mailoverlord-operators`, and `viewer@example.com`, who is not. `mailoverlord-operators` is the
default `operator-groups`, so the operator can release and delete while the viewer can only
read — sign in as the viewer first, then the operator, to see the difference.

In `oidc` mode the app is the OIDC client. The browser is sent to Dex at the published
`localhost:5556`, while the app redeems the code and reads the keys over the compose network at
`dex:5556`. Because those two addresses differ, the registration names its endpoints explicitly
instead of using `issuer-uri` discovery; nothing is fetched at startup, so the same variables
sit harmlessly in the other profiles.

In `proxy` mode oauth2-proxy is the OIDC client and the app runs in `mode: header`. The proxy
holds a fixed address on a dedicated `authnet` network and overwrites `X-Forwarded-User` and
`X-Forwarded-Groups` on the requests it forwards, and the app believes those headers only from
that one address. Reaching http://localhost:8080 directly still gets a 401 — the headers are
ignored because the connection did not come from the proxy — which is the boundary working, not
a fault. Go through http://localhost:4180 instead.

This is a local-development setup: Dex is in-memory, its users and the oauth2-proxy cookie
secret are the values in `docker-compose.yml`, and every port is published on the loopback
only. For a real provider, see [authentication.md](authentication.md).