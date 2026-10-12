import { rcon } from '../lib/rcon.mjs'
import { chatAs, expectNoChat, runAsOp, waitForChat, withBot } from '../lib/harness.mjs'

const HOPPER_OFFSETS = [2, 3, 4]

async function placeHoppers(name, block) {
  for (const dy of HOPPER_OFFSETS) {
    await rcon('hub', `execute at ${name} run setblock ~ ~${dy} ~ minecraft:${block}`)
  }
}

export default {
  name: 'spotlight',
  requires: ['velocity', 'hub'],
  async run(t) {
    await withBot('Bot1', async (bot) => {
      try {
        await t.step('help shows last scan state', async () => {
          await runAsOp(bot, '/spotlight')
          await waitForChat(bot, /Last scan: /)
          await waitForChat(bot, /\/spotlight here/)
        })

        await t.step('fixture: three hoppers stacked above the bot', async () => {
          await placeHoppers('Bot1', 'hopper')
        })

        await t.step('scan ranks loaded chunks with coordinates', async () => {
          await chatAs(bot, '/spotlight scan')
          await waitForChat(bot, /Scanning \d+ chunks/)
          await waitForChat(bot, /Top \d+ of \d+ scored chunks/, { timeout: 20_000 })
          await waitForChat(bot, /#1 \S+ -?\d+,-?\d+ score=\d+/)
        })

        await t.step('here scores the current chunk including the hopper fixture', async () => {
          await chatAs(bot, '/spotlight here')
          const line = await waitForChat(bot, /Chunk -?\d+,-?\d+ score=\d+/)
          if (!/3 hoppers x8/.test(line)) throw new Error(`expected "3 hoppers x8" in: ${line}`)
        })

        await t.step('near lists hot chunks from the last scan', async () => {
          await chatAs(bot, '/spotlight near 64')
          await waitForChat(bot, /-?\d+,-?\d+/)
        })

        await t.step('weights match the documented defaults', async () => {
          await chatAs(bot, '/spotlight weights')
          await waitForChat(bot, /entities=1 hoppers=8 minecarts=4 redstone=2 item-frames=3 villagers=5/)
        })

        await t.step('non-op cannot see or run the command', async () => {
          await rcon('hub', 'deop Bot1')
          await new Promise((resolve) => setTimeout(resolve, 400))
          await chatAs(bot, '/spotlight scan')
          await waitForChat(bot, /Unknown or incomplete command/)
          await expectNoChat(bot, /Scanning \d+ chunks/, { quietMs: 1_000 })
        })
      } finally {
        await placeHoppers('Bot1', 'air')
        await rcon('hub', 'op Bot1')
      }
    })
  },
}
