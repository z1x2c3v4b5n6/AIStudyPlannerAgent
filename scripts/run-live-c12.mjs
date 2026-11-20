import { spawn } from 'node:child_process'
import { createProviderBudget } from '../tests/c12-provider.mjs'
if (!process.env.DEEPSEEK_API_KEY || process.env.C12_LIVE_DEEPSEEK !== 'true') {
  console.log(JSON.stringify({ check: 'deepseek', status: 'NOT_RUN',
    reason: !process.env.DEEPSEEK_API_KEY ? 'DEEPSEEK_API_KEY is missing' : 'Set C12_LIVE_DEEPSEEK=true to opt in',
    command: 'node scripts/run-live-c12.mjs' }))
} else {
  if (process.env.CI) throw new Error('Paid acceptance is disabled in CI')
  createProviderBudget()
  const child = spawn(process.execPath, ['tests/c8-e2e.mjs'], {
    stdio: 'inherit',
    env: { ...process.env, C8_LIVE_DEEPSEEK: 'true', C11_LIVE_DEEPSEEK: 'true' },
  })
  child.on('error', () => { process.exitCode = 1 })
  child.on('exit', code => { process.exitCode = code ?? 1 })
}
