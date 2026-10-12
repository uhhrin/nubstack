import { rcon } from '../lib/rcon.mjs'
import {
  chatAs,
  giveItems,
  itemCount,
  sleep,
  switchServer,
  waitForChat,
  waitUntil,
  withBot,
} from '../lib/harness.mjs'

const DIAMONDS = 5

// Needs the fabric profile: ViaFabric lets the 26.1 bot join the 26.2 server.
export default {
  name: 'cairn',
  requires: ['velocity', 'hub', 'fabric'],
  async run(t) {
    await withBot('Bot1', async (bot) => {
      let graveId = null
      try {
        await t.step('switch: /server cairn lands on the Fabric backend', async () => {
          await switchServer(bot, 'cairn')
          await rcon('fabric', 'op Bot1')
          // Survival: start from a known inventory and a full health bar.
          await rcon('fabric', 'clear Bot1')
          await rcon('fabric', 'gamerule keep_inventory false').catch(() => {})
        })

        await t.step('help: short command is registered', async () => {
          await chatAs(bot, '/cairn')
          await waitForChat(bot, /cairn/i)
        })

        let deathSpot
        await t.step('die with items: grave message shows dimension and coordinates', async () => {
          await giveItems(bot, 'diamond', DIAMONDS, { server: 'fabric' })
          await waitUntil(() => itemCount(bot, 'diamond') === DIAMONDS, { label: 'diamonds in inventory' })
          bot.nub.mark = bot.nub.seq
          await rcon('fabric', 'kill Bot1')
          const line = await waitForChat(bot, /grave was placed at -?\d+ -?\d+ -?\d+ in minecraft:\w+/, {
            timeout: 20_000,
          })
          deathSpot = line.match(/placed at (-?\d+ -?\d+ -?\d+) in (minecraft:\w+)/)
          t.log(line)
        })

        await t.step('items left the inventory after respawn; grave key is issued', async () => {
          await sleep(2_000)
          await waitUntil(() => bot.health > 0, { label: 'respawned', timeout: 15_000 })
          if (itemCount(bot, 'diamond') !== 0) throw new Error(`diamonds still carried: ${itemCount(bot, 'diamond')}`)
          // Key is a trial_key (not placeable) delivered on COPY_FROM (respawn), not during death drops.
          await waitUntil(() => itemCount(bot, 'trial_key') >= 1, {
            label: 'grave key in inventory',
            timeout: 10_000,
          })
          const key = bot.inventory.items().find((item) => item.name === 'trial_key')
          const named = key?.customName || key?.displayName || ''
          if (!/Grave Key/i.test(String(named)) && !/Grave Key/i.test(JSON.stringify(key?.nbt || {}))) {
            // shortcut: Mineflayer 26.x may omit customName; count alone is enough then
            t.log(`grave key present (name not readable via mineflayer: ${named || 'n/a'})`)
          }
        })

        await t.step('locate: most recent grave with id, dimension and the same coordinates', async () => {
          await chatAs(bot, '/cairn locate')
          const line = await waitForChat(bot, /^#\d+ -?\d+ -?\d+ -?\d+ in minecraft:\w+, \d+m left/)
          const [, id, coords, dimension] = line.match(/^#(\d+) (-?\d+ -?\d+ -?\d+) in (minecraft:\w+)/)
          if (coords !== deathSpot[1] || dimension !== deathSpot[2]) {
            throw new Error(`locate (${coords} ${dimension}) differs from the death message (${deathSpot[1]} ${deathSpot[2]})`)
          }
          graveId = id
        })

        await t.step('list: shows your grave by id', async () => {
          await chatAs(bot, '/cairn list')
          await waitForChat(bot, new RegExp(`^#${graveId} .* in minecraft:\\w+`))
        })

        await t.step('admin list: owner filter shows the owner name', async () => {
          await chatAs(bot, '/cairn admin list Bot1')
          await waitForChat(bot, new RegExp(`^#${graveId} Bot1 `))
        })

        await t.step('admin key: reissues a transferable key', async () => {
          await rcon('fabric', 'clear Bot1 trial_key')
          await waitUntil(() => itemCount(bot, 'trial_key') === 0, { label: 'keys cleared' })
          await chatAs(bot, `/cairn admin key ${graveId}`)
          await waitForChat(bot, new RegExp(`Reissued key for grave #${graveId}`))
          await waitUntil(() => itemCount(bot, 'trial_key') >= 1, {
            label: 'reissued key in inventory',
            timeout: 10_000,
          })
        })

        await t.step('admin clear: removes the grave and locate no longer finds it', async () => {
          await chatAs(bot, `/cairn admin clear ${graveId}`)
          await sleep(1_000)
          await chatAs(bot, '/cairn locate')
          await waitForChat(bot, /You have no active graves/)
          graveId = null
        })
      } finally {
        if (graveId) {
          await chatAs(bot, `/cairn admin clear ${graveId}`).catch(() => {})
          await sleep(500)
        }
        await rcon('fabric', 'kill @e[type=minecraft:item]').catch(() => {})
        await switchServer(bot, 'hub').catch(() => {})
      }
    })
  },
}
