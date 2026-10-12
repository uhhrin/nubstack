import { execFile } from 'node:child_process'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const botsDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

// shortcut: delegate to smoke.mjs as a child process instead of duplicating the join loop, merge if smoke grows assertions
export default {
  name: 'smoke',
  requires: ['velocity', 'hub'],
  async run(t) {
    await t.step('three bots join through the proxy', () =>
      new Promise((resolve, reject) => {
        execFile('node', ['smoke.mjs'], { cwd: botsDir, timeout: 120_000 }, (error, stdout, stderr) => {
          const out = `${stdout}${stderr}`.trim()
          if (error || !/smoke ok/.test(out)) {
            reject(new Error(`smoke.mjs failed:\n${out}`))
            return
          }
          t.log(out.split('\n').pop())
          resolve()
        })
      }),
    )
  },
}
