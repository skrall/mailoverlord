#!/usr/bin/env node
/**
 * Writes the OpenAPI document that the application publishes into openapi.json.
 *
 * The TypeScript types are generated from that checked-in file rather than from a running
 * server, so `npm run build` never depends on a live backend. Run this after changing the
 * JSON API, then run `npm run generate:types` to refresh the types to match.
 *
 * If the application is already running it is used as is. Otherwise this starts one with
 * Maven, waits for the document, and shuts it down again.
 */

import { spawn } from 'node:child_process'
import { writeFile } from 'node:fs/promises'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const uiDir = resolve(dirname(fileURLToPath(import.meta.url)), '..')
const projectDir = resolve(uiDir, '..')
const port = process.env.MAILOVERLORD_PORT ?? '8080'
const specUrl = `http://localhost:${port}/v3/api-docs`

const sleep = (ms) => new Promise((done) => setTimeout(done, ms))

async function fetchSpec() {
  const response = await fetch(specUrl, { signal: AbortSignal.timeout(3000) })
  if (!response.ok) {
    throw new Error(`${specUrl} answered ${response.status} ${response.statusText}`)
  }
  return response.json()
}

async function waitForSpec(attempts) {
  for (let attempt = 1; attempt <= attempts; attempt++) {
    try {
      return await fetchSpec()
    } catch {
      process.stderr.write(`  waiting for ${specUrl} (${attempt}/${attempts})\r`)
      await sleep(1000)
    }
  }
  throw new Error(`${specUrl} never became available`)
}

async function main() {
  let spec
  let server = null

  try {
    spec = await fetchSpec()
    process.stderr.write(`Using the application already running on port ${port}.\n`)
  } catch {
    process.stderr.write(`Starting the application to read its OpenAPI document.\n`)
    server = spawn('./mvnw', ['-q', 'spring-boot:run', `-Dspring-boot.run.arguments=--server.port=${port}`], {
      cwd: projectDir,
      stdio: 'ignore',
    })
    spec = await waitForSpec(120)
  } finally {
    server?.kill('SIGTERM')
  }

  await writeFile(resolve(uiDir, 'openapi.json'), `${JSON.stringify(spec, null, 2)}\n`, 'utf8')
  process.stderr.write(`Wrote openapi.json with ${Object.keys(spec.paths ?? {}).length} paths.\n`)
  process.stderr.write('Now run: npm run generate:types\n')
}

main().catch((error) => {
  process.stderr.write(`${error.message}\n`)
  process.exitCode = 1
})
