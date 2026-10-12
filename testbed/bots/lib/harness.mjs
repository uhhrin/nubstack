import mineflayer from 'mineflayer'
import { onlinePlayers, rcon } from './rcon.mjs'

export const HOST = process.env.PROXY_HOST || '127.0.0.1'
export const PORT = Number(process.env.PROXY_PORT || 25565)
// Upstream Mineflayer protocol data stops at 26.1; ViaBackwards on the backends translates.
export const VERSION = process.env.BOT_VERSION || '26.1'
const RING_SIZE = 20

export const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** Thrown by a scenario to skip itself or a step with a reason. */
export class Skip extends Error {
  constructor(reason) {
    super(reason)
    this.name = 'Skip'
  }
}

/** Thrown for a path that fails because of the environment, never to hide a plugin bug. */
export class KnownFail extends Error {
  constructor(reason) {
    super(reason)
    this.name = 'KnownFail'
  }
}

/** Plain text from a chat component, a JSON string, or nested NBT-ish objects. */
export function flattenText(value) {
  if (value == null) return ''
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (trimmed.startsWith('{') || trimmed.startsWith('[') || trimmed.startsWith('"')) {
      try {
        return flattenText(JSON.parse(trimmed))
      } catch {
        return value
      }
    }
    return value
  }
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) return value.map(flattenText).join('')
  if (typeof value === 'object') {
    if ('value' in value && 'type' in value && Object.keys(value).length <= 3) {
      return flattenText(value.value)
    }
    let out = ''
    if (value.text != null) out += flattenText(value.text)
    if (value.translate != null && value.text == null) out += String(value.translate)
    if (value.with) out += ' ' + flattenText(value.with)
    if (value.extra) out += flattenText(value.extra)
    if (!out) out = Object.values(value).map(flattenText).join('')
    return out
  }
  return ''
}

function matches(pattern, text) {
  return pattern instanceof RegExp ? pattern.test(text) : text.includes(String(pattern))
}

function attachRecorder(bot, server) {
  const state = {
    server,
    lines: [],
    seq: 0,
    mark: 0,
    actionBars: [],
    titles: [],
    kicked: null,
    ended: false,
    waiters: new Set(),
  }
  bot.nub = state

  const push = (text) => {
    if (!text) return
    state.seq += 1
    state.lines.push({ seq: state.seq, text })
    if (state.lines.length > RING_SIZE) state.lines.shift()
    for (const waiter of state.waiters) waiter(text, state.seq)
  }

  bot.on('messagestr', (text, position) => {
    if (position === 'game_info') return
    push(text)
  })
  // 26.x sends the action bar and titles as their own packets; Mineflayer only maps the old chat position.
  bot._client.on('action_bar', (packet) => {
    const text = flattenText(packet.text)
    state.actionBars.push({ at: Date.now(), text })
    if (state.actionBars.length > RING_SIZE) state.actionBars.shift()
    for (const waiter of state.waiters) waiter(null, state.seq, text)
  })
  for (const [packetName, kind] of [['set_title_text', 'title'], ['set_subtitle_text', 'subtitle']]) {
    bot._client.on(packetName, (packet) => {
      state.titles.push({ at: Date.now(), kind, text: flattenText(packet.text) })
      if (state.titles.length > RING_SIZE) state.titles.shift()
    })
  }
  bot.on('kicked', (reason) => {
    state.kicked = flattenText(reason) || String(reason)
    state.kickedRaw = typeof reason === 'string' ? reason : JSON.stringify(reason)
    for (const waiter of state.waiters) waiter(null, state.seq, null, state.kicked)
  })
  bot.on('end', () => {
    state.ended = true
    for (const waiter of state.waiters) waiter(null, state.seq, null, null, true)
  })
  bot.on('error', () => {})
}

/** Last chat lines seen by a bot, oldest first. */
export function chatTail(bot) {
  return (bot.nub?.lines ?? []).map((line) => line.text)
}

function dump(bot, label) {
  const lines = chatTail(bot)
  const bars = (bot.nub?.actionBars ?? []).slice(-5).map((b) => b.text)
  const out = [`--- ${bot.nub?.name ?? bot.username} (${label}) last ${lines.length} chat lines ---`, ...lines]
  if (bars.length) out.push('--- action bar ---', ...bars)
  if (bot.nub?.kicked) out.push(`--- kicked: ${bot.nub.kicked}`)
  return out.join('\n')
}

function spawnPromise(bot, name, timeoutMs) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${name} login timeout`)), timeoutMs)
    const cleanup = () => {
      clearTimeout(timer)
      bot.removeListener('spawn', onSpawn)
      bot.removeListener('kicked', onKick)
      bot.removeListener('error', onError)
    }
    const onSpawn = () => {
      cleanup()
      resolve()
    }
    const onKick = (reason) => {
      cleanup()
      reject(new Error(`${name} kicked: ${flattenText(reason)}`))
    }
    const onError = (error) => {
      cleanup()
      reject(error)
    }
    bot.once('spawn', onSpawn)
    bot.once('kicked', onKick)
    bot.once('error', onError)
  })
}

export function createBot(name, { server = 'hub', host = HOST, port = PORT } = {}) {
  const bot = mineflayer.createBot({
    host,
    port,
    username: name,
    auth: 'offline',
    version: VERSION,
    checkTimeoutInterval: 60_000,
  })
  attachRecorder(bot, server)
  return bot
}

/**
 * Join through the proxy as `name`, run `fn(bot)`, always quit. On failure the last
 * chat lines are attached to the error as `chatDump` and printed.
 */
export async function withBot(name, fn, options = {}) {
  const bot = createBot(name, options)
  try {
    await spawnPromise(bot, name, options.loginTimeoutMs ?? 60_000)
    // Let the join messages and plugin nags settle so they do not match later waits.
    await sleep(options.settleMs ?? 1_500)
    return await fn(bot)
  } catch (error) {
    if (error instanceof Skip || error instanceof KnownFail) throw error
    const text = dump(bot, 'failure')
    error.chatDump = text
    console.error(text)
    throw error
  } finally {
    await quit(bot)
  }
}

export async function quit(bot) {
  if (bot.nub.ended) return
  try {
    bot.quit('done')
  } catch {
    // already closed
  }
  await Promise.race([new Promise((resolve) => bot.once('end', resolve)), sleep(2_000)])
  // Velocity keeps the session a moment; give it room before a same-name rejoin.
  await sleep(500)
}

// Velocity sometimes forwards a proxy command to the backend ("Unknown or incomplete command")
// when two commands from one player arrive within a few milliseconds. Space sends out.
const MIN_SEND_GAP_MS = Number(process.env.BOT_SEND_GAP_MS || 350)

/**
 * Send chat or a slash command and start a new match window, so earlier lines are ignored.
 * Await it: sends from one bot are spaced by BOT_SEND_GAP_MS.
 */
export async function chatAs(bot, text) {
  const wait = (bot.nub.lastSend ?? 0) + MIN_SEND_GAP_MS - Date.now()
  if (wait > 0) await sleep(wait)
  bot.nub.mark = bot.nub.seq
  bot.nub.lastSend = Date.now()
  bot.chat(text)
}

/**
 * Resolve with the first chat line (after the last `chatAs`) matching `pattern`.
 * Rejects with the recent lines on timeout.
 */
export function waitForChat(bot, pattern, { timeout = 10_000, since = bot.nub.mark } = {}) {
  const state = bot.nub
  return new Promise((resolve, reject) => {
    const existing = state.lines.find((line) => line.seq > since && matches(pattern, line.text))
    if (existing) {
      resolve(existing.text)
      return
    }
    const timer = setTimeout(() => {
      state.waiters.delete(waiter)
      reject(
        new Error(
          `${bot.username}: no chat matching ${pattern} within ${timeout}ms\n${dump(bot, 'timeout')}`,
        ),
      )
    }, timeout)
    const waiter = (text, seq, _bar, kicked, ended) => {
      if (kicked || ended) {
        clearTimeout(timer)
        state.waiters.delete(waiter)
        reject(
          new Error(
            `${bot.username}: connection closed while waiting for ${pattern}` +
              (state.kicked ? ` (kicked: ${state.kicked})` : '') +
              `\n${dump(bot, 'closed')}`,
          ),
        )
        return
      }
      if (text != null && seq > since && matches(pattern, text)) {
        clearTimeout(timer)
        state.waiters.delete(waiter)
        resolve(text)
      }
    }
    state.waiters.add(waiter)
  })
}

/** Assert no chat line matches `pattern` for `quietMs` after the last `chatAs`. */
export async function expectNoChat(bot, pattern, { quietMs = 2_000 } = {}) {
  await sleep(quietMs)
  const hit = bot.nub.lines.find((line) => line.seq > bot.nub.mark && matches(pattern, line.text))
  if (hit) throw new Error(`${bot.username}: unexpected chat ${pattern}: ${hit.text}\n${dump(bot, 'unexpected')}`)
}

/** Resolve with the first action bar text matching `pattern` seen after `since` (ms epoch). */
export function waitForActionBar(bot, pattern, { timeout = 8_000, since = Date.now() - 1_000 } = {}) {
  const state = bot.nub
  return new Promise((resolve, reject) => {
    const existing = state.actionBars.find((bar) => bar.at >= since && matches(pattern, bar.text))
    if (existing) {
      resolve(existing.text)
      return
    }
    const timer = setTimeout(() => {
      state.waiters.delete(waiter)
      const bars = state.actionBars.slice(-5).map((b) => b.text)
      reject(new Error(`${bot.username}: no action bar matching ${pattern} (saw: ${JSON.stringify(bars)})`))
    }, timeout)
    const waiter = (_text, _seq, bar) => {
      if (bar != null && matches(pattern, bar)) {
        clearTimeout(timer)
        state.waiters.delete(waiter)
        resolve(bar)
      }
    }
    state.waiters.add(waiter)
  })
}

/** Retry `fn` until it returns a truthy value or the timeout passes. */
export async function waitUntil(fn, { timeout = 10_000, interval = 250, label = 'condition' } = {}) {
  const deadline = Date.now() + timeout
  let last
  while (Date.now() < deadline) {
    try {
      last = await fn()
      if (last) return last
    } catch (error) {
      last = error
    }
    await sleep(interval)
  }
  throw new Error(`timed out waiting for ${label}${last instanceof Error ? `: ${last.message}` : ''}`)
}

/** Backend that currently holds this bot, via RCON `list`. */
export async function currentBackend(bot, candidates = ['hub', 'minigame', 'fabric']) {
  for (const server of candidates) {
    try {
      if ((await onlinePlayers(server)).includes(bot.username)) return server
    } catch {
      // backend not running
    }
  }
  return null
}

/** Op the bot on its current backend, then send a command as that bot. */
export async function runAsOp(bot, command, { server = bot.nub.server } = {}) {
  await rcon(server, `op ${bot.username}`)
  // Paper applies op to permission checks immediately; give the command tree a moment to resend.
  await sleep(400)
  await chatAs(bot, command)
}

/** Give items through RCON on the bot's backend. */
export async function giveItems(bot, item, count = 1, { server = bot.nub.server } = {}) {
  const id = item.includes(':') ? item : `minecraft:${item}`
  const out = await rcon(server, `give ${bot.username} ${id} ${count}`)
  if (!/Gave/i.test(out)) throw new Error(`give failed on ${server}: ${out}`)
  return out
}

/** Total count of an item in the bot's inventory. */
export function itemCount(bot, name) {
  return bot.inventory.items().filter((item) => item.name === name).reduce((sum, i) => sum + i.count, 0)
}

/**
 * Send `/server <target>` and wait until the bot logs in again (or respawns) on the new backend.
 * Confirms with RCON `list` so a same-dimension switch is not mistaken for no move,
 * unless `fast` is set.
 */
export async function switchServer(bot, target, { timeout = 30_000, fast = false } = {}) {
  const state = bot.nub
  const moved = new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`no login/respawn after /server ${target}`)), timeout)
    // Velocity re-enters configuration on a switch, which Mineflayer reports as a new `login`.
    const done = () => {
      clearTimeout(timer)
      bot.removeListener('login', done)
      bot.removeListener('respawn', done)
      bot.removeListener('kicked', onKick)
      resolve()
    }
    const onKick = (reason) => {
      clearTimeout(timer)
      reject(new Error(`kicked during /server ${target}: ${flattenText(reason)}`))
    }
    bot.once('login', done)
    bot.once('respawn', done)
    bot.once('kicked', onKick)
  })
  await chatAs(bot, `/server ${target}`)
  await moved
  if (fast) {
    // No RCON round trip or settle delay: lets the caller fire the next switch at once.
    state.server = target === 'cairn' ? 'fabric' : target
    return
  }
  const backend = target === 'cairn' ? 'fabric' : target
  await waitUntil(async () => (await onlinePlayers(backend)).includes(bot.username), {
    timeout,
    label: `${bot.username} on ${backend}`,
  })
  state.server = backend
  await sleep(1_000)
}

/**
 * Connect as `name` and expect to be refused (login disconnect or kick right after join).
 * Returns the kick text. Fails if the bot stays connected.
 */
export async function assertKick(name, pattern, { timeout = 20_000, ...options } = {}) {
  const bot = createBot(name, options)
  try {
    const result = await new Promise((resolve, reject) => {
      const timer = setTimeout(() => reject(new Error(`${name} was not kicked within ${timeout}ms`)), timeout)
      bot.once('kicked', (reason) => {
        clearTimeout(timer)
        resolve(flattenText(reason) || String(reason))
      })
      bot.once('end', (reason) => {
        // Some disconnects arrive only as `end` with a reason string.
        setTimeout(() => {
          clearTimeout(timer)
          resolve(bot.nub.kicked ?? String(reason))
        }, 200)
      })
      bot.once('error', (error) => {
        clearTimeout(timer)
        reject(error)
      })
      bot.once('spawn', () => {
        // Spawned: a kick may still follow quickly (ban enforced after join).
        setTimeout(() => {
          if (!bot.nub.kicked && !bot.nub.ended) {
            clearTimeout(timer)
            reject(new Error(`${name} joined and stayed connected; expected a kick matching ${pattern}`))
          }
        }, 3_000)
      })
    })
    if (pattern != null && !matches(pattern, result) && !matches(pattern, bot.nub.kickedRaw ?? '')) {
      throw new Error(`${name} kick did not match ${pattern}: ${result}`)
    }
    return result
  } finally {
    await quit(bot)
  }
}

/** Wait for an already connected bot to be kicked. Returns the kick text. */
export async function waitForKick(bot, pattern, { timeout = 15_000 } = {}) {
  const state = bot.nub
  if (state.kicked) return state.kicked
  const reason = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${bot.username} was not kicked within ${timeout}ms\n${dump(bot, 'no kick')}`)), timeout)
    const waiter = (_t, _s, _b, kicked, ended) => {
      if (kicked || ended) {
        clearTimeout(timer)
        state.waiters.delete(waiter)
        resolve(state.kicked ?? 'connection ended')
      }
    }
    state.waiters.add(waiter)
  })
  if (pattern != null && !matches(pattern, reason)) throw new Error(`kick did not match ${pattern}: ${reason}`)
  return reason
}

/** Fail with a registration bug message when only the namespaced form of a command works. */
export async function expectShortCommand(bot, command, pattern, { timeout = 8_000 } = {}) {
  await chatAs(bot, `/${command}`)
  try {
    return await waitForChat(bot, pattern, { timeout })
  } catch (error) {
    if (bot.nub.lines.some((line) => /unknown or incomplete command|Unknown command/i.test(line.text))) {
      throw new Error(`/${command} is not registered in short form (registration bug): ${error.message}`)
    }
    throw error
  }
}
