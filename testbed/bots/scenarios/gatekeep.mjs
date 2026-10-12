import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { copyIntoContainer } from '../lib/rcon.mjs'
import { assertKick, chatAs, sleep, waitForChat, withBot } from '../lib/harness.mjs'

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..')
const STRICT = path.join(root, 'plugin-config', 'velocity', 'gatekeep-strict', 'config.yml')
const DEFAULT = path.join(root, 'plugin-config', 'velocity', 'gatekeep', 'config.yml')
const TARGET = '/server/plugins/gatekeep/config.yml'
const STRICT_LIMIT = 3

// Bot1 is staff (gatekeep.admin) through the LuckPerms seed and stays connected the whole run,
// so it can lift the throttle for the address every bot shares.
export default {
  name: 'gatekeep',
  requires: ['velocity', 'hub'],
  async run(t) {
    let strictApplied = false
    await withBot('Bot1', async (staff) => {
      let ip
      try {
        await t.step('status: finds the address the proxy sees for the bots', async () => {
          await chatAs(staff, '/gatekeep status')
          await waitForChat(staff, /Tracking \d+ addresses, \d+ denied since start\. Limit \d+ per/)
          const row = await waitForChat(staff, /^\S+ \. \d+\/\d+/)
          ip = row.match(/^(\S+) \./)[1]
          t.log(`bots connect from ${ip}`)
        })

        await t.step('strict overlay: copy into the Velocity plugin dir and reload', async () => {
          await copyIntoContainer('velocity', STRICT, TARGET)
          strictApplied = true
          await chatAs(staff, '/gatekeep reload')
          await waitForChat(staff, /reload/i)
          await chatAs(staff, `/gatekeep clear ${ip}`)
          await waitForChat(staff, /(Cleared|cleared|Forgot|forgot)/)
          await chatAs(staff, '/gatekeep status')
          await waitForChat(staff, new RegExp(`Limit ${STRICT_LIMIT} per`))
        })

        await t.step('burst: connects under the limit are allowed', async () => {
          for (const name of ['Bot2', 'Bot3', 'Bot2']) {
            await withBot(name, async () => {}, { settleMs: 200 })
          }
        })

        await t.step('burst: the next connect is denied with a support code', async () => {
          const reason = await assertKick('Bot3', /GK-\w+/)
          t.log(`deny screen: ${reason.replace(/\s+/g, ' ')}`)
          if (!/Strict testbed throttle/.test(reason)) throw new Error(`strict deny message missing: ${reason}`)
        })

        await t.step('check: shows attempts and the support code for the address', async () => {
          await chatAs(staff, `/gatekeep check ${ip}`)
          await waitForChat(staff, /GK-\w+/)
        })

        await t.step('status: denied counter moved', async () => {
          await chatAs(staff, '/gatekeep status')
          const line = await waitForChat(staff, /\d+ denied since start/)
          if (/ 0 denied since start/.test(line)) throw new Error(`denied counter is zero: ${line}`)
        })

        await t.step('clear: lifting the address lets the next bot in', async () => {
          await chatAs(staff, `/gatekeep clear ${ip}`)
          await waitForChat(staff, /(Cleared|cleared|Forgot|forgot)/)
          await withBot('Bot3', async () => {}, { settleMs: 200 })
        })
      } finally {
        if (strictApplied) {
          await copyIntoContainer('velocity', DEFAULT, TARGET)
          try {
            await chatAs(staff, '/gatekeep reload')
            await waitForChat(staff, /reload/i)
            if (ip) await chatAs(staff, `/gatekeep clear ${ip}`)
            await sleep(500)
          } catch (error) {
            console.error(`default gatekeep config copied back but reload failed (restart velocity): ${error.message}`)
          }
        }
      }

      await t.step('reset: default limit is back after the overlay', async () => {
        await chatAs(staff, '/gatekeep status')
        await waitForChat(staff, /Limit 30 per/)
      })
    })
  },
}
