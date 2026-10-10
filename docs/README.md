# Documentation

The [README](../README.md) is the quickstart and the configuration reference. These pages go
deeper on the topics it only introduces.

| Page | What it covers |
| --- | --- |
| [authentication.md](authentication.md) | The four auth modes — basic, OIDC, header, none — the OPERATOR/VIEWER split, and the management plane's role split |
| [compose.md](compose.md) | Building the image, persisting captured mail in a container, the per-engine compose profiles, and the Dex/oauth2-proxy auth profiles |
| [api.md](api.md) | The HTTP API in detail: endpoints, filtering, paging, error shapes, and the release and delete semantics |
| [ui.md](ui.md) | Working on the Vue SPA: dev server, tests, generated types, styling |

## Why these are files and not the README

A README that covers all four modes, six databases and the whole API gets long enough that the
quickstart disappears into it. These pages are versioned in the same commit as the code they
describe, so they cannot drift the way a separate wiki does.

If you change behaviour that one of these pages describes, change that page in the same commit.