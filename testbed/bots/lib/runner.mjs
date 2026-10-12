import { mkdir, writeFile } from 'node:fs/promises'
import { fileURLToPath, pathToFileURL } from 'node:url'
import path from 'node:path'
import { KnownFail, Skip } from './harness.mjs'
import { serviceRunning } from './rcon.mjs'

const here = path.dirname(fileURLToPath(import.meta.url))
const botsDir = path.resolve(here, '..')
const reportsDir = path.join(botsDir, 'reports')

// Suite order from improvements/mineflayer-bots.md: hub-only, moderation, minigame, isolated, opt-in.
export const SUITE = [
  'smoke',
  'ledger',
  'spotlight',
  'cull',
  'amber',
  'tether',
  'aegis',
  'loom',
  'harbor',
  'gatekeep',
  'cairn',
]

const hints = {
  velocity: 'docker compose -f testbed/docker-compose.yml up -d',
  hub: 'docker compose -f testbed/docker-compose.yml up -d',
  minigame: 'docker compose -f testbed/docker-compose.yml --profile minigame up -d',
  fabric: 'docker compose -f testbed/docker-compose.yml --profile fabric up -d',
}

function describe(error) {
  return { message: error.message.split('\n')[0], detail: error.message, chat: error.chatDump }
}

function makeContext(record) {
  const log = (line) => console.log(`    ${line}`)
  return {
    log,
    /** Run one named step; its failure fails the scenario with the step name attached. */
    async step(name, fn) {
      const started = Date.now()
      const step = { name, status: 'ok', ms: 0 }
      record.steps.push(step)
      try {
        const result = await fn()
        step.ms = Date.now() - started
        log(`ok   ${name} (${step.ms}ms)`)
        return result
      } catch (error) {
        step.ms = Date.now() - started
        if (error instanceof Skip) {
          step.status = 'skipped'
          step.reason = error.message
          log(`skip ${name}: ${error.message}`)
          return undefined
        }
        step.status = 'failed'
        step.error = describe(error)
        log(`FAIL ${name}: ${error.message.split('\n')[0]}`)
        error.step = name
        throw error
      }
    },
    skip(reason) {
      throw new Skip(reason)
    },
  }
}

async function runScenario(scenario) {
  const record = { name: scenario.name, status: 'passed', ms: 0, steps: [] }
  const started = Date.now()
  console.log(`\n== ${scenario.name}`)
  try {
    for (const service of scenario.requires ?? []) {
      if (!(await serviceRunning(service))) {
        throw new Skip(`requires ${service}: ${hints[service] ?? 'start the service'}`)
      }
    }
    await scenario.run(makeContext(record))
  } catch (error) {
    if (error instanceof Skip) {
      record.status = 'skipped'
      record.reason = error.message
      console.log(`  SKIPPED: ${error.message}`)
    } else if (error instanceof KnownFail) {
      record.status = 'known_fail'
      record.reason = error.message
      console.log(`  KNOWN_FAIL: ${error.message}`)
    } else {
      record.status = 'failed'
      record.error = describe(error)
      console.error(`  FAILED${error.step ? ` at "${error.step}"` : ''}: ${error.message}`)
    }
  }
  record.ms = Date.now() - started
  return record
}

async function loadScenario(name) {
  const file = path.join(botsDir, 'scenarios', `${name}.mjs`)
  try {
    const mod = await import(pathToFileURL(file).href)
    return mod.default
  } catch (error) {
    if (error.code === 'ERR_MODULE_NOT_FOUND' && error.message.includes(file)) {
      throw new Error(`unknown scenario "${name}" (expected ${file})`)
    }
    throw error
  }
}

export async function runNamed(names, { keepGoing = false } = {}) {
  const results = []
  let stop = false
  for (const name of names) {
    if (stop) {
      results.push({ name, status: 'not_run', ms: 0, steps: [] })
      continue
    }
    const scenario = await loadScenario(name)
    const record = await runScenario(scenario)
    results.push(record)
    if (record.status === 'failed' && !keepGoing) stop = true
  }
  return results
}

export async function writeReport(results, meta) {
  await mkdir(reportsDir, { recursive: true })
  const stamp = new Date().toISOString().replace(/[:.]/g, '-')
  const summary = {
    startedAt: meta.startedAt,
    finishedAt: new Date().toISOString(),
    botVersion: process.env.BOT_VERSION || '26.1',
    continue: meta.keepGoing,
    totals: Object.fromEntries(
      ['passed', 'failed', 'skipped', 'known_fail', 'not_run'].map((s) => [s, results.filter((r) => r.status === s).length]),
    ),
    results,
  }
  const file = path.join(reportsDir, `${stamp}.json`)
  await writeFile(file, JSON.stringify(summary, null, 2) + '\n')
  await writeFile(path.join(reportsDir, 'latest.json'), JSON.stringify(summary, null, 2) + '\n')
  return { file, summary }
}

async function main() {
  const args = process.argv.slice(2)
  const keepGoing = args.includes('--continue')
  let names = args.filter((a) => !a.startsWith('--'))
  if (names.length === 0 || names.includes('all')) names = SUITE
  const startedAt = new Date().toISOString()

  const results = await runNamed(names, { keepGoing })
  const { file, summary } = await writeReport(results, { startedAt, keepGoing })

  console.log('\n== summary')
  for (const r of results) {
    const extra = r.reason ? ` (${r.reason})` : r.error ? ` (${r.error.message})` : ''
    console.log(`  ${r.status.padEnd(10)} ${r.name}${extra}`)
  }
  console.log(`  report: ${path.relative(process.cwd(), file)}`)
  process.exitCode = summary.totals.failed > 0 ? 1 : 0
  // Mineflayer sockets can linger; the report is already written.
  setTimeout(() => process.exit(), 200)
}

if (process.argv[1] && pathToFileURL(process.argv[1]).href === import.meta.url) {
  main().catch((error) => {
    console.error(error)
    process.exit(2)
  })
}
