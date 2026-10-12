import { rcon } from '../lib/rcon.mjs'
import {
  assertKick,
  chatAs,
  chatTail,
  sleep,
  expectNoChat,
  waitForChat,
  waitForKick,
  withBot,
} from '../lib/harness.mjs'

// aegis enforces a per-staff punish cooldown (staff-cooldown-ms, default 1500).
const COOLDOWN_MS = 1_700

// Staff commands run on Velocity (aegis-velocity); Bot1 is staff through the LuckPerms seed.
// Mute chat enforcement runs on the Paper backend (aegis-paper).
export default {
  name: 'aegis',
  requires: ['velocity', 'hub'],
  async run(t) {
    await withBot('Bot1', async (staff) => {
      // Leave no active punishment behind from an earlier failed run.
      for (const cmd of ['/aegis unban Bot2', '/aegis unmute Bot2']) {
        await chatAs(staff, cmd)
        await sleep(COOLDOWN_MS)
      }

      await withBot('Bot2', async (target) => {
        await t.step('help hub lists the shipped subcommands', async () => {
          await chatAs(staff, '/aegis')
          await waitForChat(staff, /\[aegis\] Help/)
          await waitForChat(staff, /\/aegis ban <player> \[dur\] <reason>/)
        })

        await t.step('warn: recorded and shown in history', async () => {
          await sleep(COOLDOWN_MS)
          await chatAs(staff, '/aegis warn Bot2 scenario warning')
          await waitForChat(staff, /WARN applied to Bot2/)
          await chatAs(staff, '/aegis history Bot2')
          await waitForChat(staff, /WARN/)
        })

        await t.step('mute: blocks Bot2 chat, check lists it', async () => {
          await sleep(COOLDOWN_MS)
          await chatAs(staff, '/aegis mute Bot2 10m scenario spam')
          await waitForChat(staff, /MUTE applied to Bot2/)
          await chatAs(staff, '/aegis check Bot2')
          await waitForChat(staff, /MUTE Bot2 by Bot1 \[active\]/)
          await chatAs(target, 'muted bot says hello')
          t.log(`mute notice: ${await waitForChat(target, /You are muted/)}`)
          await expectNoChat(staff, /muted bot says hello/, { quietMs: 1_000 })
        })

        await t.step('unmute: chat flows again and check is clear of mutes', async () => {
          await sleep(COOLDOWN_MS)
          await chatAs(staff, '/aegis unmute Bot2')
          await waitForChat(staff, /Revoked/)
          await chatAs(target, 'unmuted bot says hello')
          await waitForChat(staff, /unmuted bot says hello/)
        })

        await t.step('ban: kicks the online player with the ban reason', async () => {
          await sleep(COOLDOWN_MS)
          await chatAs(staff, '/aegis ban Bot2 5m scenario ban')
          await waitForChat(staff, /BAN applied to Bot2/)
          await waitForKick(target, /banned|scenario ban/i)
        })
      })

      await t.step('reconnect while banned is denied', async () => {
        await assertKick('Bot2', /banned|scenario ban/i)
      })

      await t.step('unban: revoke lets the player in again', async () => {
        await chatAs(staff, '/aegis unban Bot2')
        await waitForChat(staff, /Revoked/)
        await withBot('Bot2', async (again) => {
          await rcon('hub', 'list') // backend reachable after the ban cycle
          if (again.nub.kicked) throw new Error(`still kicked: ${again.nub.kicked}`)
        })
      })

      await t.step('check: no mute or ban stays active after the revokes', async () => {
        await chatAs(staff, '/aegis check Bot2')
        await waitForChat(staff, /(Active for Bot2:|No active punishment)/)
        await sleep(800)
        const stale = chatTail(staff).filter((line) => /^(MUTE|BAN) Bot2 .*\[active\]/.test(line))
        if (stale.length) throw new Error(`still active: ${stale.join(' | ')}`)
      })

      await t.step('non-staff cannot run staff subcommands', async () => {
        await withBot('Bot3', async (plain) => {
          await chatAs(plain, '/aegis ban Bot2 nope')
          await sleep(1_500)
          await expectNoChat(plain, /BAN applied/, { quietMs: 500 })
        })
        await chatAs(staff, '/aegis check Bot2')
        await waitForChat(staff, /(Active for Bot2:|No active punishment)/)
        await sleep(800)
        if (chatTail(staff).some((line) => /^BAN Bot2 .*\[active\]/.test(line))) throw new Error('Bot3 managed to ban Bot2')
      })
    })
  },
}
