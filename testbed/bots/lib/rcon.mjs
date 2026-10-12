import { execFile } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const here = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(here, '..', '..', '..')
const composeFile = path.join(repoRoot, 'testbed', 'docker-compose.yml')

export const BACKENDS = ['hub', 'minigame', 'fabric']

// Profile flags let `exec` find optional services whether or not they are listed in the default set.
const composeBase = [
  'compose',
  '-f',
  composeFile,
  '--profile',
  'minigame',
  '--profile',
  'fabric',
]

function run(args, { timeoutMs = 30_000 } = {}) {
  return new Promise((resolve, reject) => {
    execFile(
      'docker',
      [...composeBase, ...args],
      { cwd: repoRoot, timeout: timeoutMs, maxBuffer: 4 * 1024 * 1024 },
      (error, stdout, stderr) => {
        if (error) {
          error.message = `docker ${args.join(' ')} failed: ${stderr || error.message}`
          reject(error)
          return
        }
        resolve(stdout)
      },
    )
  })
}

/** Remove Minecraft section-sign colour codes and ANSI escapes from rcon-cli output. */
export function stripFormatting(text) {
  return text
    .replace(/\u001b\[[0-9;]*m/g, '')
    .replace(/§x(?:§?[0-9a-fA-F]){6}/g, '')
    .replace(/§[0-9a-fk-orA-FK-OR]/g, '')
    .replace(/\[[0-9;]*m/g, '')
    .trim()
}

/** Run one console command on a backend through rcon-cli inside its container. */
export async function rcon(server, command, opts) {
  if (!BACKENDS.includes(server)) {
    throw new Error(`rcon: unknown backend "${server}" (expected ${BACKENDS.join(', ')})`)
  }
  const out = await run(['exec', '-T', server, 'rcon-cli', command], opts)
  return stripFormatting(out)
}

/** Names of players currently on one backend, from the vanilla `list` command. */
export async function onlinePlayers(server) {
  const out = await rcon(server, 'list')
  const tail = out.split(':').slice(1).join(':')
  return tail
    .split(',')
    .map((name) => name.trim())
    .filter(Boolean)
}

/** Run a program inside a compose service container (used for Velocity, which has no RCON). */
export async function composeExec(service, args, opts) {
  return run(['exec', '-T', service, ...args], opts)
}

/** Write a text file inside a running service container (content travels base64 encoded). */
export async function writeContainerFile(service, containerPath, content) {
  const b64 = Buffer.from(content, 'utf8').toString('base64')
  await composeExec(service, ['sh', '-c', `printf %s '${b64}' | base64 -d > '${containerPath}'`])
}

export async function readContainerFile(service, containerPath) {
  return composeExec(service, ['cat', containerPath])
}

/** Copy a host file into a running service container. */
export async function copyIntoContainer(service, hostPath, containerPath) {
  await run(['cp', hostPath, `${service}:${containerPath}`])
}

/** Whether a compose service container is running. */
export async function serviceRunning(service) {
  try {
    const out = await run(['ps', '--status', 'running', '--services'])
    return out.split('\n').map((s) => s.trim()).includes(service)
  } catch {
    return false
  }
}
