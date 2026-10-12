import { readContainerFile, rcon, writeContainerFile } from '../lib/rcon.mjs'
import { chatAs, runAsOp, waitForChat, withBot } from '../lib/harness.mjs'

const CONFIG = '/data/plugins/cull/config.yml'
const MOBS = 12
const LOW_CAP = 5

export default {
  name: 'cull',
  requires: ['velocity', 'hub'],
  async run(t) {
    const original = await readContainerFile('hub', CONFIG)
    await withBot('Bot1', async (bot) => {
      try {
        await t.step('status reports mode and per-world caps', async () => {
          await runAsOp(bot, '/cull status')
          await waitForChat(bot, /Mode all, cancelled spawns \d+, culled entities \d+/)
          await waitForChat(bot, /world: cap \d+, \d+ loaded chunks, \d+ over cap/)
        })

        await t.step('overspawn: chickens in one chunk while the cap is still high', async () => {
          await Promise.all(
            Array.from({ length: MOBS }, () => rcon('hub', 'execute at Bot1 run summon minecraft:chicken ~ ~ ~')),
          )
          await chatAs(bot, '/cull here')
          const line = await waitForChat(bot, /Chunk -?\d+,-?\d+: \d+\/\d+/)
          const count = Number(line.match(/: (\d+)\//)[1])
          if (count < MOBS) throw new Error(`expected at least ${MOBS} entities, saw: ${line}`)
        })

        await t.step('low cap: reload makes the chunk over cap in status and scan', async () => {
          // Keep the periodic cleanup out of the way so `run` is the only remover.
          await writeContainerFile(
            'hub',
            CONFIG,
            `max-entities-per-chunk: ${LOW_CAP}\ncleanup-interval-ticks: 72000\n`,
          )
          await chatAs(bot, '/cull reload')
          await waitForChat(bot, /reload/i)
          await chatAs(bot, '/cull status world')
          await waitForChat(bot, new RegExp(`cap ${LOW_CAP}, \\d+ loaded chunks, [1-9]\\d* over cap`))
          await chatAs(bot, '/cull scan world 3')
          await waitForChat(bot, /Top \d+ of \d+ chunks with entities/)
          await waitForChat(bot, new RegExp(`#1 world -?\\d+,-?\\d+ \\d+/${LOW_CAP}`))
        })

        await t.step('run removes the excess and the chunk drops to the cap', async () => {
          await chatAs(bot, '/cull run')
          const line = await waitForChat(bot, /Cleanup removed \d+ entities/)
          if (Number(line.match(/removed (\d+)/)[1]) < 1) throw new Error(`nothing removed: ${line}`)
          await chatAs(bot, '/cull here')
          const here = await waitForChat(bot, /Chunk -?\d+,-?\d+: \d+\/\d+/)
          const count = Number(here.match(/: (\d+)\//)[1])
          if (count > LOW_CAP) throw new Error(`chunk still over cap: ${here}`)
        })

        await t.step('counters record the cull', async () => {
          await chatAs(bot, '/cull status')
          const line = await waitForChat(bot, /culled entities \d+/)
          if (/culled entities 0\b/.test(line)) throw new Error(`culled counter did not move: ${line}`)
        })
      } finally {
        await rcon('hub', 'kill @e[type=minecraft:chicken]').catch(() => {})
        await writeContainerFile('hub', CONFIG, original)
        await chatAs(bot, '/cull reload')
      }
    })
  },
}
