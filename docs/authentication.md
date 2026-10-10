# Authentication

Mailoverlord has four authentication mechanisms. `mailoverlord.security.mode` picks which one
guards the API; they are alternatives, not layers, though some share building blocks.

| Mode | Who signs in | Held by the app | Use when |
| --- | --- | --- | --- |
| `basic` (default) | The user, via HTTP Basic | Nothing — the credential rides every request | Single team, one shared password |
| `oidc` | An identity provider, via authorization code flow | A session cookie | You already run Okta or Entra ID |
| `header` | A reverse proxy in front of the app | Nothing — the proxy asserts the identity | The edge already authenticates |
| `none` | Nobody | Nothing | Only the test suite |

Two roles apply in every mode except `none`. **OPERATOR** may read, release and delete.
**VIEWER** may only read. An OPERATOR always holds VIEWER as well, since releasing presupposes
being able to read. The split is enforced on the two mutating endpoints (`POST /messages/release`
and `POST /messages/delete`) and on the management plane; everything else is authenticated but
unrestricted.

Because Basic does not pop its dialog for `fetch`, the UI sends the browser to `/login` when the
API answers 401, which is what makes the browser ask for credentials; after that it reloads and
the calls carry the credentials. A VIEWER reaching release or delete is told it is a permission
problem. `curl --user operator:password ...` works the same way against the API. See #56.

## Basic mode

`basic` mode requires HTTP Basic on every request. Sign in once as the documented
`spring.security.user.*` identity (an OPERATOR, so it can read, release, and delete) or as a
name added to `operator-users` or `viewer-users`; everyone shares the one
`spring.security.user.password`. Set `mailoverlord.security.mode=none` only to run with no
authentication at all.

## Browser login against a provider (OIDC)

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

## Trusting a reverse proxy's identity (header)

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
forwards — dropping any identity header a client sent rather than passing it through. Header
names are up to you: `X-Auth-Request-User`, `X-Forwarded-User`, `X-Remote-User`, `SM_USER` all
work, as long as proxy and app agree on the spelling. Which ones a given proxy writes depends on
its mode: oauth2-proxy sets `X-Forwarded-User` and `X-Forwarded-Groups` on the requests it
proxies, and reserves `X-Auth-Request-*` for nginx `auth_request` mode, so the names in the
snippet above are not interchangeable.

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

## Trying the modes locally

`docker-compose.yml` ships two profiles that stand the OIDC and header modes up against a
throwaway [Dex](https://dexidp.io) identity provider, without a real IdP. See
[compose.md](compose.md#signing-in-with-docker-compose-oidc-and-header-modes).

## Management plane (actuator)

Actuator lives on its own port, `8090` by default, bound to `127.0.0.1` like the rest of the
app — an attacker who reaches the application port never sees an actuator route. From the host:

```bash
curl http://localhost:8090/actuator/health     # component status, DB and disk checks
curl http://localhost:8090/actuator/info
curl http://localhost:8090/actuator/metrics/jvm.memory.used
curl -u operator:your-password http://localhost:8090/actuator/env    # OPERATOR only
```

Read access for an operator and the probe is baked in: `health`, `info` and `metrics` need no
credential (the DB and disk checks are the health details), the rest needs an OPERATOR role, and
`shutdown` stays off. This holds in every mode, including `header`, where the proxy boundary
answers 401 before the role check just as it does for the API. See #72.

The Docker image's container healthcheck probes this same endpoint, so the compose stack reports
`(healthy)` when the app and its database are ready.