import mineflayer from 'mineflayer'

const host = process.env.PROXY_HOST || '127.0.0.1'
const port = Number(process.env.PROXY_PORT || 25565)
const count = Number(process.env.BOT_COUNT || 3)

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function spawnBot(name) {
  const bot = mineflayer.createBot({
    host,
    port,
    username: name,
    auth: 'offline',
    version: false,
  })

  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error(`${name} login timeout`)), 60_000)
    bot.once('spawn', () => {
      clearTimeout(timer)
      resolve()
    })
    bot.once('error', (error) => {
      clearTimeout(timer)
      reject(error)
    })
    bot.once('kicked', (reason) => {
      clearTimeout(timer)
      reject(new Error(`${name} kicked: ${reason}`))
    })
  })

  console.log(`${name} spawned at`, bot.entity.position)
  return bot
}

const bots = []
try {
  for (let i = 1; i <= count; i++) {
    bots.push(await spawnBot(`Bot${i}`))
    await sleep(1_000)
  }
  console.log(`smoke ok: ${bots.length} bots on ${host}:${port}`)
  process.exitCode = 0
} catch (error) {
  console.error('smoke failed:', error)
  process.exitCode = 1
} finally {
  for (const bot of bots) {
    bot.quit('done')
  }
  await sleep(500)
  process.exit()
}
