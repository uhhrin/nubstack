import { rcon } from '../lib/rcon.mjs'
import { chatAs, expectNoChat, runAsOp, waitForActionBar, waitForChat, withBot } from '../lib/harness.mjs'

// Balances are reset from the console (`/ledger set` runs directly there, players must click-confirm).
async function resetBalances() {
  for (const name of ['Bot1', 'Bot2']) {
    await rcon('hub', `ledger set ${name} 0 testbed-reset`)
  }
}

export default {
  name: 'ledger',
  requires: ['velocity', 'hub'],
  async run(t) {
    await resetBalances()

    await withBot('Bot1', async (bot1) => {
      await withBot('Bot2', async (bot2) => {
        await t.step('give: admin deposits and chat shows the amount', async () => {
          await runAsOp(bot1, '/ledger give Bot1 100 prize')
          await waitForChat(bot1, /Gave 100\.00 Credits to Bot1/)
        })

        await t.step('bal: shows balance with Pay and History buttons', async () => {
          await chatAs(bot1, '/bal')
          const line = await waitForChat(bot1, /Balance: 100\.00 Credits/)
          if (!/\[Pay\]/.test(line) || !/\[History\]/.test(line)) throw new Error(`missing buttons: ${line}`)
        })

        await t.step('money alias resolves like /bal', async () => {
          await chatAs(bot1, '/money')
          await waitForChat(bot1, /Balance: 100\.00 Credits/)
        })

        await t.step('pay: transfers, receipt copy button, receiver action bar', async () => {
          const since = Date.now()
          await chatAs(bot1, '/pay Bot2 25 thanks')
          const line = await waitForChat(bot1, /Paid 25\.00 Credits to Bot2/)
          if (!/\[Copy receipt\]/.test(line)) throw new Error(`no receipt button: ${line}`)
          await waitForActionBar(bot2, /\+25(\.00)? Credits from Bot1/, { since })
        })

        await t.step('balances after pay: sender 75, receiver 25', async () => {
          await chatAs(bot1, '/bal')
          await waitForChat(bot1, /Balance: 75\.00 Credits/)
          await chatAs(bot2, '/bal')
          await waitForChat(bot2, /Balance: 25\.00 Credits/)
        })

        await t.step('pay: insufficient funds is refused and balance unchanged', async () => {
          await chatAs(bot2, '/pay Bot1 400')
          await waitForChat(bot2, /Insufficient funds/)
          await chatAs(bot2, '/bal')
          await waitForChat(bot2, /Balance: 25\.00 Credits/)
        })

        await t.step('pay: invalid amounts and self pay are rejected', async () => {
          const cases = [
            ['abc', /must be a number/],
            ['0', /greater than zero/],
            ['-5', /(must be a number|greater than zero)/],
            ['1.234', /at most 2 decimals/],
          ]
          for (const [bad, expected] of cases) {
            await chatAs(bot1, `/pay Bot2 ${bad}`)
            await waitForChat(bot1, expected)
          }
          await chatAs(bot1, '/pay Bot1 5')
          await waitForChat(bot1, /You cannot pay yourself/)
        })

        await t.step('pay: large amount asks for confirmation and does not move funds', async () => {
          await runAsOp(bot1, '/ledger give Bot1 1000 bank')
          await waitForChat(bot1, /Gave 1,000\.00 Credits/)
          await chatAs(bot1, '/pay Bot2 750')
          await waitForChat(bot1, /confirm/i)
          await chatAs(bot2, '/bal')
          await waitForChat(bot2, /Balance: 25\.00 Credits/)
        })

        await t.step('history: newest first, then receipt by id', async () => {
          await chatAs(bot1, '/ledger history')
          await waitForChat(bot1, /History for Bot1, page 1/)
          const row = await waitForChat(bot1, /#\d+ 1,000\.00 Credits bank/)
          const id = row.match(/#(\d+)/)[1]
          await chatAs(bot1, `/ledger receipt ${id}`)
          await waitForChat(bot1, new RegExp(`(Receipt|#)\\s?${id}\\b`))
        })

        await t.step('baltop: leaderboard lists players', async () => {
          await chatAs(bot1, '/baltop')
          await waitForChat(bot1, /Bot1/)
        })

        await t.step('take: admin withdraw reduces balance; over-withdraw fails', async () => {
          await chatAs(bot1, '/ledger take Bot2 5 fee')
          await waitForChat(bot1, /(Took|Taken|Withdrew) 5\.00 Credits/i)
          await chatAs(bot1, '/ledger take Bot2 99999 greedy')
          await waitForChat(bot1, /Bot2 has less than 99,999\.00 Credits/)
        })

        await t.step('non-admin cannot give', async () => {
          await rcon('hub', 'deop Bot2')
          await chatAs(bot2, '/ledger give Bot2 5000 cheat')
          await waitForChat(bot2, /No permission/)
          await expectNoChat(bot2, /Gave 5000/, { quietMs: 1_000 })
        })
      })
    })
    await resetBalances()
  },
}
