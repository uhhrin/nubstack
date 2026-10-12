import { chatAs, runAsOp, Skip, waitForChat, withBot } from '../lib/harness.mjs'

// shortcut: stops before the Discord side (link, linked status, confirmed unlink) because that needs a human or a bot token round trip, add it when a Discord test account exists
export default {
  name: 'tether',
  requires: ['velocity', 'hub'],
  async run(t) {
    if (process.env.TETHER_LIVE !== '1') {
      throw new Skip('set TETHER_LIVE=1 (needs the Discord bot configured in testbed/.env; the chat bridge then posts bot chat to that channel)')
    }

    await withBot('Bot1', async (bot) => {
      let first

      await t.step('link: card shows the code, copy button and expiry', async () => {
        await runAsOp(bot, '/tether link')
        await waitForChat(bot, /Your link code expires in \d+:\d{2}/)
        const hint = await waitForChat(bot, /In Discord, send !link [A-Z0-9]{4,16}/)
        first = hint.match(/!link ([A-Z0-9]+)/)[1]
        await waitForChat(bot, /\[Copy code\] \[Cancel code\]/)
      })

      await t.step('link again reuses the pending code', async () => {
        await chatAs(bot, '/tether link')
        const hint = await waitForChat(bot, /In Discord, send !link [A-Z0-9]{4,16}/)
        if (!hint.includes(first)) throw new Error(`expected ${first} to be reused: ${hint}`)
      })

      await t.step('link renew replaces the code', async () => {
        await chatAs(bot, '/tether link renew')
        const hint = await waitForChat(bot, /In Discord, send !link [A-Z0-9]{4,16}/)
        if (hint.includes(first)) throw new Error(`code was not replaced: ${hint}`)
      })

      await t.step('status: not linked, code pending, bridge state', async () => {
        await chatAs(bot, '/tether status')
        await waitForChat(bot, /Not linked\. A code is pending for \d+m/)
        await waitForChat(bot, /Discord bridge: (connected|disabled|not connected)/)
      })

      await t.step('admin codes counts the pending code without printing it', async () => {
        await chatAs(bot, '/tether admin codes')
        const line = await waitForChat(bot, /Pending codes: \d+/)
        if (/Pending codes: 0/.test(line)) throw new Error(`expected a pending code: ${line}`)
      })

      await t.step('cancel removes the code, unlink says not linked', async () => {
        await chatAs(bot, '/tether cancel')
        await waitForChat(bot, /Link code cancelled/)
        await chatAs(bot, '/tether unlink')
        await waitForChat(bot, /You are not linked/)
      })
    })
  },
}
