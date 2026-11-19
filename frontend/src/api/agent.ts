import http from './http'
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
  list: () => http.get<ApiResponse<AgentSummary[]>>(path),
  create: () => http.post<ApiResponse<AgentSession>>(path),
  get: (id: string) => http.get<ApiResponse<AgentSession>>(`${path}/${id}`),
  send: (id: string, text: string) => http.post<ApiResponse<AgentSession>>(
    `${path}/${id}/messages`, { text }, { timeout: 38000 }),
  decide: (id: string, action: AgentAction, confirm: boolean) => http.post<ApiResponse<AgentSession>>(
    `${path}/${id}/actions/${action.id}/decision`, { digest: action.digest, confirm }, { timeout: 15000 }),
}
