import {
  chatAs,
  sleep,
  switchServer,
  waitForChat,
  waitUntil,
  withBot,
} from '../lib/harness.mjs'
import { onlinePlayers } from '../lib/rcon.mjs'

// Bot1 is staff (harbor.admin) and Bot3 is vip (harbor.priority.vip) through the LuckPerms seed.
export default {
  name: 'harbor',
  requires: ['velocity', 'hub', 'minigame'],
  async run(t) {
    await withBot('Bot1', async (staff) => {
      // A failed earlier run may have left the backend paused.
      await chatAs(staff, '/harbor admin resume minigame')
      await sleep(500)

      await withBot('Bot2', async (plain) => {
        await withBot('Bot3', async (vip) => {
          try {
            await t.step('status: shape shows server, health, queue and your position', async () => {
              await chatAs(plain, '/harbor status hub')
              await waitForChat(plain, /hub \. healthy \. queue 0 \. you not queued/)
            })

            await t.step('queue alias answers like /harbor', async () => {
              await chatAs(plain, '/queue')
              await waitForChat(plain, /\[harbor\] Help/)
            })

            await t.step('list: every registered server with health and queue', async () => {
              await chatAs(plain, '/harbor list')
              await waitForChat(plain, /minigame \. (healthy|unhealthy|paused) \. queue \d+/)
              await waitForChat(plain, /hub \. healthy \. queue \d+/)
            })

            await t.step('leave when not queued is harmless', async () => {
              await chatAs(plain, '/harbor leave')
              await waitForChat(plain, /\[harbor\]/)
            })

            await t.step('non-staff cannot pause a backend', async () => {
              await chatAs(plain, '/harbor admin pause minigame')
              await waitForChat(plain, /No permission/)
            })

            await t.step('admin pause: sends stop for the server', async () => {
              await chatAs(staff, '/harbor admin pause minigame')
              await waitForChat(staff, /Paused sends for minigame/)
              await chatAs(plain, '/harbor status minigame')
              await waitForChat(plain, /minigame \. paused/)
            })

            await t.step('join while paused: player is queued, not sent', async () => {
              await chatAs(plain, '/harbor join minigame')
              await waitForChat(plain, /(queue|Queued|position|#\d+)/i)
              await chatAs(plain, '/harbor status minigame')
              await waitForChat(plain, /minigame \. paused \. queue 1 \. you #1/)
            })

            await t.step('priority: vip joins behind but is placed ahead of the default player', async () => {
              await chatAs(vip, '/harbor join minigame')
              await waitForChat(vip, /(queue|Queued|position|#\d+)/i)
              await chatAs(vip, '/harbor status minigame')
              await waitForChat(vip, /you #1 \(priority 10\)/)
              await chatAs(plain, '/harbor status minigame')
              await waitForChat(plain, /you #2/)
            })

            await t.step('admin kickqueue removes one player', async () => {
              await chatAs(staff, '/harbor admin kickqueue Bot2')
              await waitForChat(staff, /Bot2/)
              await chatAs(plain, '/harbor status minigame')
              await waitForChat(plain, /you not queued/)
            })

            await t.step('admin clear empties the queue', async () => {
              await chatAs(staff, '/harbor admin clear minigame')
              await waitForChat(staff, /(Cleared|cleared)/)
              await chatAs(vip, '/harbor status minigame')
              await waitForChat(vip, /queue 0 \. you not queued/)
            })

            await t.step('queued player leaves with /harbor leave', async () => {
              await chatAs(plain, '/harbor join minigame')
              await waitForChat(plain, /(queue|Queued|position|#\d+)/i)
              await chatAs(plain, '/harbor leave')
              await waitForChat(plain, /(left|Left|removed)/i)
            })
          } finally {
            await chatAs(staff, '/harbor admin clear minigame')
            await sleep(300)
            await chatAs(staff, '/harbor admin resume minigame')
            await sleep(500)
          }

          await t.step('resume: join connects straight through when the backend is healthy', async () => {
            await chatAs(staff, '/harbor admin resume minigame')
            await waitForChat(staff, /Resumed sends for minigame/)
            await chatAs(plain, '/harbor join minigame')
            await waitUntil(async () => (await onlinePlayers('minigame')).includes('Bot2'), {
              timeout: 30_000,
              label: 'Bot2 on minigame via /harbor join',
            })
            plain.nub.server = 'minigame'
            await switchServer(plain, 'hub')
          })
        })
      })
    })
  },
}
