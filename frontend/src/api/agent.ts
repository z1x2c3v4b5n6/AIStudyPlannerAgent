import http from './http'
import { getToken, getTokenName } from '../utils/token'
import type { ApiResponse } from '../types/api'

export type JsonValue = string | number | boolean | null | JsonValue[] | { [key: string]: JsonValue }
export interface AgentAction {
  id: string
  type: string
  target: string
  digest: string
  expiresAt: string
  status: 'PENDING' | 'EXECUTING' | 'EXECUTED' | 'CANCELLED' | 'FAILED' | 'EXPIRED'
  preview: JsonValue
  result: JsonValue
  errorMessage?: string
}
export interface AgentTool {
  name: string
  success: boolean
  data: JsonValue
}
export interface AgentEntry {
  role: 'user' | 'assistant'
  text: string
  tools: AgentTool[]
  actionId: string | null
}
export interface AgentSession {
  id: string
  title: string
  expiresAt: string
  messages: AgentEntry[]
  actions: AgentAction[]
}
export interface AgentSummary { id: string; title: string; updatedAt: string }

const path = '/agent/sessions'
export const agentApi = {
  list: (page = 1) => http.get<ApiResponse<AgentSummary[]>>(path, { params: { page } }),
  remove: (id: string) => http.delete<ApiResponse<null>>(`${path}/${id}`),
  create: () => http.post<ApiResponse<AgentSession>>(path),
  get: (id: string) => http.get<ApiResponse<AgentSession>>(`${path}/${id}`),
  send: (id: string, text: string) => http.post<ApiResponse<AgentSession>>(
    `${path}/${id}/messages`, { text }, { timeout: 38000 }),
  decide: (id: string, action: AgentAction, confirm: boolean) => http.post<ApiResponse<AgentSession>>(
    `${path}/${id}/actions/${action.id}/decision`, { digest: action.digest, confirm }, { timeout: 15000 }),
}

export interface WorkflowNode {
  id: string
  kind: string
  operation: string
  dependsOn: string[]
  status: 'READY' | 'RUNNING' | 'WAITING' | 'DONE' | 'SKIPPED' | 'FAILED'
  result: JsonValue
  error: string | null
  attempts: number
  startedAt: string | null
  endedAt: string | null
}
export interface WorkflowRun {
  id: string
  input: string
  scenario: string
  revision: number
  status: 'READY' | 'RUNNING' | 'PAUSED' | 'WAITING_INPUT' | 'WAITING_APPROVAL' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'TIMED_OUT'
  expiresAt: string
  nodes: WorkflowNode[]
}

const runs = (session: string) => `${path}/${session}/workflows`
const run = (id: string) => `/agent/workflows/${id}`
export const workflowApi = {
  list: (session: string, page = 1) => http.get<ApiResponse<WorkflowRun[]>>(runs(session), { params: { page } }),
  start: (session: string, text: string) => http.post<ApiResponse<WorkflowRun>>(runs(session), { text }),
  get: (id: string) => http.get<ApiResponse<WorkflowRun>>(run(id)),
  advance: (id: string) => http.post<ApiResponse<WorkflowRun>>(`${run(id)}/advance`, undefined, { timeout: 100000 }),
  autoAdvance: (id: string) => http.post<ApiResponse<WorkflowRun>>(`${run(id)}/auto-advance`, undefined, { timeout: 20000 }),
  decide: (id: string, digest: string, confirm: boolean, strategy?: string) =>
    http.post<ApiResponse<WorkflowRun>>(`${run(id)}/decision`, { digest, confirm, strategy }),
  clarify: (id: string, dailyMinutes: number, subjectId: number | null) =>
    http.post<ApiResponse<WorkflowRun>>(`${run(id)}/clarification`, { dailyMinutes, subjectId }),
  control: (id: string, command: 'PAUSE' | 'RESUME' | 'CANCEL') =>
    http.post<ApiResponse<WorkflowRun>>(`${run(id)}/control`, { command }),
}

export async function readWorkflowEvents(id: string, after: number, signal: AbortSignal,
  receive: (snapshot: WorkflowRun) => void): Promise<void> {
  const token = getToken()
  if (!token) throw new Error('Missing session')
  const response = await fetch(http.defaults.baseURL + run(id) + '/events?after=' + after, {
    headers: { [getTokenName()]: token, Accept: 'text/event-stream' }, signal,
  })
  if (!response.ok || !response.body) throw new Error('Workflow stream unavailable')
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader()
  let buffer = ''
  try {
    while (!signal.aborted) {
      const { done, value } = await reader.read()
      if (done) return
      buffer += value
      let separator = buffer.indexOf('\n\n')
      while (separator >= 0) {
        const entry = buffer.slice(0, separator).split('\n').find(line => line.startsWith('data:'))
        if (entry) receive(JSON.parse(entry.slice(5)) as WorkflowRun)
        buffer = buffer.slice(separator + 2)
        separator = buffer.indexOf('\n\n')
      }
    }
  } finally {
    await reader.cancel().catch(() => undefined)
    reader.releaseLock()
  }
}
