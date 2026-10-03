# AGENTS.md

Guidance for AI agents working in this repository.

## Commit messages

Keep them terse. One-line subject, and a body only when it earns its place.
The repo's own commits are short; match that, not a long-form essay.

## Running the build

- Tests bind the real SMTP port (2025). If an instance is already running on
  2025, run the suite on another port instead of killing it:
  `./mvnw -B verify -Dmailoverlord.smtp.port=2026 -Dspring.mail.port=2026`
- The UI build pins Node `v24.21.0` (Maven downloads it to `ui/node/node`).
  Use that binary rather than a system Node: `export PATH="$PWD/ui/node/node:$PATH"`.
- When changing `node.version`, `rm -rf ui/node` first. Leaving the old
  download in place mixes two Node trees and `npm ci` dies with
  `Class extends value undefined is not a constructor or null`.
- Changing a dependency in `ui/package.json` also needs the lockfile
  regenerated (`ui/node/node/npm install`), or `npm ci` refuses to run.
- `npm run build` runs `vue-tsc --noEmit` then `vite build`; typecheck first.
  The spec files are typechecked too, since `tsconfig.json` includes `src/**/*.ts`.
- `npm test` in `ui/` runs the Vitest suite. `./mvnw verify` runs it as well,
  so the Java build alone is not a shortcut past the UI tests.
- OpenAPI drift is checked in CI: `npm run generate:spec` + `generate:types`
  in `ui/`, then `git diff --exit-code -- ui/openapi.json ui/src/api/schema.d.ts`.
