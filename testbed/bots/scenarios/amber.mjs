import { composeExec, rcon } from '../lib/rcon.mjs'
import { chatAs, runAsOp, waitForChat, withBot } from '../lib/harness.mjs'

const LABEL = 'botscenario'

export default {
  name: 'amber',
  requires: ['velocity', 'hub'],
  async run(t) {
    let file = null
    await withBot('Bot1', async (bot) => {
      try {
        await t.step('help shows snapshot disk use', async () => {
          await runAsOp(bot, '/amber')
          await waitForChat(bot, /Snapshot disk used: [\d.]+ \w+/)
        })

        await t.step('snapshot: copies the world and reports name and size', async () => {
          await chatAs(bot, `/amber snapshot world ${LABEL}`)
          const line = await waitForChat(bot, /Snapshot saved: \S+ \([\d.]+ \w+\)/, { timeout: 90_000 })
          file = line.match(/Snapshot saved: (\S+)/)[1]
          if (!file.endsWith(`-world-${LABEL}`)) throw new Error(`unexpected snapshot name: ${file}`)
        })

        await t.step('list: newest first with Restore and Delete buttons', async () => {
          await chatAs(bot, '/amber list')
          await waitForChat(bot, /Snapshots \(\d+\), newest first/)
          const row = await waitForChat(bot, new RegExp(file.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')))
          if (!/\[Restore\]/.test(row) || !/\[Delete\]/.test(row)) throw new Error(`missing buttons: ${row}`)
        })

        await t.step('info: world, label, size and creation time', async () => {
          await chatAs(bot, `/amber info ${file}`)
          await waitForChat(bot, /World: world/)
          await waitForChat(bot, new RegExp(`Label: ${LABEL}`))
          await waitForChat(bot, /Size: [\d.]+ \w+/)
          await waitForChat(bot, /Created: \d{4}-\d{2}-\d{2}/)
        })

        await t.step('info: path traversal is rejected', async () => {
          await chatAs(bot, '/amber info ../x')
          await waitForChat(bot, /Unsafe snapshot name/)
        })

        await t.step('restore prompt asks to type the world name back (no restore runs)', async () => {
          await chatAs(bot, `/amber restore ${file}`)
          await waitForChat(bot, /Type the world name back to continue/)
        })

        await t.step('prune applies retention', async () => {
          await chatAs(bot, '/amber prune')
          await waitForChat(bot, /Retention applied: keeping the newest \d+/)
        })

        await t.step('cancel with nothing running says so', async () => {
          await chatAs(bot, '/amber cancel')
          await waitForChat(bot, /No snapshot is running/)
        })
      } finally {
        // Delete asks for a click confirm, so remove the folder this run created directly.
        if (file && /^[\w.-]+$/.test(file)) {
          await composeExec('hub', ['rm', '-rf', `/data/plugins/amber/snapshots/${file}`]).catch(() => {})
        }
        await rcon('hub', 'op Bot1').catch(() => {})
      }
    })
  },
}
