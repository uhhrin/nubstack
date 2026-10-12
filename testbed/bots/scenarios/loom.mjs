import { rcon } from '../lib/rcon.mjs'
import {
  chatAs,
  currentBackend,
  giveItems,
  itemCount,
  runAsOp,
  sleep,
  switchServer,
  waitForActionBar,
  waitForChat,
  waitUntil,
  withBot,
} from '../lib/harness.mjs'

const DIAMONDS = 16
const INGOTS = 3

async function inventoryIs(bot, expected) {
  await waitUntil(
    () => Object.entries(expected).every(([name, count]) => itemCount(bot, name) === count),
    {
      timeout: 15_000,
      label: `inventory ${JSON.stringify(expected)} (has diamond=${itemCount(bot, 'diamond')} ingot=${itemCount(bot, 'netherite_ingot')})`,
    },
  )
}

export default {
  name: 'loom',
  requires: ['velocity', 'hub', 'minigame'],
  async run(t) {
    await withBot('Bot1', async (bot) => {
      // Joining restores whatever the last session saved; wait for that before staging items.
      await waitForActionBar(bot, /Inventory restored/, { since: 0, timeout: 20_000 })
      await rcon('hub', 'clear Bot1')
      await sleep(500)

      await t.step('hub: items given on the hub', async () => {
        await giveItems(bot, 'diamond', DIAMONDS)
        await giveItems(bot, 'netherite_ingot', INGOTS)
        await inventoryIs(bot, { diamond: DIAMONDS, netherite_ingot: INGOTS })
      })

      await t.step('hub to minigame: items follow with the restore action bar', async () => {
        const since = Date.now()
        await switchServer(bot, 'minigame')
        await waitForActionBar(bot, /Inventory restored/, { since, timeout: 20_000 })
        await inventoryIs(bot, { diamond: DIAMONDS, netherite_ingot: INGOTS })
      })

      await t.step('status: shows save age and lease holder for the player', async () => {
        await runAsOp(bot, '/loom status Bot1')
        await waitForChat(bot, /Bot1: saved .*lease held by \S+, \d+ bytes, version \d+/)
      })

      await t.step('force-save keeps the lease and reports success', async () => {
        await chatAs(bot, '/loom force-save')
        await waitForChat(bot, /(Saved|saved)/)
      })

      await t.step('rapid switching never kicks and never duplicates items', async () => {
        // Back to back switches as fast as the proxy allows: the second starts before the first settles.
        for (const target of ['hub', 'minigame', 'hub', 'minigame']) {
          await switchServer(bot, target, { fast: true })
          if (bot.nub.kicked) throw new Error(`kicked while switching: ${bot.nub.kicked}`)
        }
        const backend = await waitUntil(() => currentBackend(bot, ['hub', 'minigame']), { label: 'bot on a backend' })
        t.log(`ended on ${backend}`)
        bot.nub.server = backend
        await sleep(3_000)
        await inventoryIs(bot, { diamond: DIAMONDS, netherite_ingot: INGOTS })
        if (bot.nub.kicked) throw new Error(`kicked after switching: ${bot.nub.kicked}`)
      })

      await t.step('minigame to hub: items change on one side show on the other', async () => {
        if (bot.nub.server !== 'hub') await switchServer(bot, 'hub')
        await inventoryIs(bot, { diamond: DIAMONDS, netherite_ingot: INGOTS })
        await rcon('hub', 'clear Bot1 minecraft:netherite_ingot')
        await waitUntil(() => itemCount(bot, 'netherite_ingot') === 0, { label: 'ingots cleared on hub' })
        await switchServer(bot, 'minigame')
        await inventoryIs(bot, { diamond: DIAMONDS, netherite_ingot: 0 })
      })

      if (bot.nub.server !== 'hub') await switchServer(bot, 'hub')
      await rcon('hub', 'clear Bot1')
    })
  },
}
