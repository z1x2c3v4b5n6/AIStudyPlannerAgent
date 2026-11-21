import { performance } from 'node:perf_hooks'

function number(env, name, { integer = false, maximum = Number.MAX_SAFE_INTEGER } = {}) {
  const value = Number(env[name])
  if (!Number.isFinite(value) || value <= 0 || value > maximum || (integer && !Number.isInteger(value)))
    throw new Error('Set a valid positive ' + name)
  return value
}

export function createProviderBudget(env = process.env) {
  const calls = number(env, 'C12_MAX_CALLS', { integer: true, maximum: 40 })
  const tokens = number(env, 'C12_MAX_TOKENS', { integer: true, maximum: 1_000_000 })
  const limit = number(env, 'C12_MAX_COST', { maximum: 100 })
  const inputPrice = number(env, 'C12_INPUT_PRICE_PER_MILLION')
  const outputPrice = number(env, 'C12_OUTPUT_PRICE_PER_MILLION')
  const expectedModel = env.DEEPSEEK_MODEL
  if (!expectedModel || !/^[a-zA-Z0-9_.-]+$/.test(expectedModel)) throw new Error('Set DEEPSEEK_MODEL explicitly')
  const totals = { attempts: 0, rejected: 0, reservedTokens: 0, reservedCost: 0, successes: 0, failures: 0,
    inputTokens: 0, outputTokens: 0, usageAvailable: 0, reportedCost: 0 }
  function reserve(payload) {
    if (payload.model !== expectedModel) throw new Error('MODEL_MISMATCH')
    if (payload.stream || payload.n > 1) throw new Error('UNSUPPORTED_BILLING_MODE')
    const output = payload.max_tokens
    if (!Number.isSafeInteger(output) || output < 1 || output > 8192) throw new Error('OUTPUT_CAP_REQUIRED')
    // Count the full UTF-8 request plus a per-message allowance for provider framing.
    const input = Buffer.byteLength(JSON.stringify(payload)) + (payload.messages?.length ?? 0) * 64 + 2048
    const charge = (input * inputPrice + output * outputPrice) / 1_000_000
    if (totals.attempts >= calls || totals.reservedTokens + input + output > tokens || totals.reservedCost + charge > limit)
      throw new Error('BUDGET_EXHAUSTED')
    totals.attempts++
    totals.reservedTokens += input + output
    totals.reservedCost += charge
  }
  async function forward(payload, send = fetch) {
    try { reserve(payload) } catch (failure) {
      totals.rejected++
      return { status: 400, data: { error: { type: failure.message } }, event: { category: failure.message } }
    }
    const started = performance.now()
    try {
      const response = await send('https://api.deepseek.com/chat/completions', {
        method: 'POST', redirect: 'error', headers: { authorization: 'Bearer ' + env.DEEPSEEK_API_KEY, 'content-type': 'application/json' },
        body: JSON.stringify(payload), signal: AbortSignal.timeout(85_000),
      })
      const data = await response.json()
      const trace = (data.choices ?? []).flatMap(choice => choice.message?.tool_calls ?? []).map(call => {
        let validJsonObject = false
        try {
          const parsed = JSON.parse(call.function.arguments)
          validJsonObject = parsed !== null && typeof parsed === 'object' && !Array.isArray(parsed)
        } catch { /* Log the shape, not model arguments or user content. */ }
        return { name: String(call.function?.name ?? '').replace(/[^\w.-]/g, '').slice(0, 80), validJsonObject }
      })
      const inTokens = data.usage?.prompt_tokens
      const outTokens = data.usage?.completion_tokens
      if (Number.isSafeInteger(inTokens) && Number.isSafeInteger(outTokens) && inTokens >= 0 && outTokens >= 0) {
        totals.usageAvailable++
        totals.inputTokens += inTokens
        totals.outputTokens += outTokens
        totals.reportedCost += (inTokens * inputPrice + outTokens * outputPrice) / 1_000_000
      }
      if (response.ok) totals.successes++
      else totals.failures++
      return { status: response.status, data, event: { category: response.ok ? 'OK' : 'PROVIDER_HTTP_' + response.status,
        model: data.model ?? payload.model, latencyMs: Math.round(performance.now() - started),
        inputTokens: inTokens ?? null, outputTokens: outTokens ?? null, tools: trace } }
    } catch (failure) {
      totals.failures++
      const category = failure.name === 'TimeoutError' || failure.name === 'AbortError' ? 'PROVIDER_TIMEOUT' : 'PROVIDER_TRANSPORT'
      return { status: 502, data: { error: { type: category } }, event: { category, latencyMs: Math.round(performance.now() - started) } }
    }
  }
  return { forward, summary: () => ({ ...totals, model: expectedModel, tariffCurrency: env.C12_PRICE_CURRENCY ?? 'configured tariff',
    note: 'Reserved budget is conservative and usage is provider-reported. Every forwarded retry spends another reservation.' }) }
}
