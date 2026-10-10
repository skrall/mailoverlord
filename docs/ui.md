# Web UI

The UI is a Vue 3 and TypeScript single-page app in [`ui/`](../ui). It is built by Vite into
the application jar, so the same process serves the UI and the API and the browser only ever
talks to one origin. There is no CORS configuration, and no second container to run.

## What it does

* `/` shows captured messages newest first, 25 to a page, sortable by received time, sender,
  recipient and subject. Select rows to release or delete them, click one to read it in the
  side panel, and page with the standard Spring Data parameters. The list refreshes every
  ten seconds, so captured mail appears without a manual reload.
* The list endpoint returns summaries only, never message bodies, so a page of large
  messages stays small. Opening a message fetches its body on demand.
* Filters live in a collapsible, debounced pane — subject, from, to and two date bounds — and
  its open/closed state persists in `localStorage`. `/` focuses the subject box.
* The detail panel shows the subject, addresses as per-address chips, arrival time, the MIME
  part list and the raw body.
* The release dialog offers the two overrides — recipients and sender — as checkboxes with
  free-text comma-separated address fields. It traps focus, closes on Escape, and restores
  focus on exit.
* Keyboard navigation covers the table: `j`/`k` to move, `Home`/`End` for the ends, `Space` to
  toggle, `Delete` to delete the selection.
* The UI follows the browser's colour scheme preference, with no toggle. Every colour is a
  custom property in [`ui/src/style.css`](../ui/src/style.css), and the dark values are in a
  `prefers-color-scheme: dark` block beside the light ones.

## Developing against it

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

## Tests

```bash
npm test
```

runs the Vitest suite, which is also part of `./mvnw verify`. The spec files are typechecked
along with the rest of the UI by `npm run build`. Spec files live beside the code they cover
and end in `.spec.ts`, so `ui/src/format.ts` is tested by `ui/src/format.spec.ts`.

## Generated API types

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
cannot silently fall behind the API. That check is why a response change is a coordinated
commit: the server type, `openapi.json` and `schema.d.ts` all move together.

## Node version

The Maven build pins the Node version and downloads it into `ui/node/node`, so Node does not
have to be installed on the machine. Use that binary rather than a system Node when running
the commands above:

```bash
export PATH="$PWD/ui/node/node:$PATH"
```

If you change `node.version` in the Maven build, remove `ui/node` first — leaving the old
download in place mixes two Node trees and `npm ci` fails with
`Class extends value undefined is not a constructor or null`. Changing a dependency in
`ui/package.json` also needs the lockfile regenerated with
`ui/node/node/npm install`, or `npm ci` refuses to run.