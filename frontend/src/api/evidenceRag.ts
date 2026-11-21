import http from './http'
import type { ApiResponse } from '../types/api'
import type { Domain, Scope } from './knowledgeHub'

const base = '/knowledge-hub'
type RagBlock = {
  id: string; scope: string; title: string; softwareVersion?: string; versionId?: string
  chunkId?: string; url?: string; line?: number; source?: string
}
export type RagAnswer = {
  answer?: string; citations: { evidence: RagBlock; quote: string }[]; retrievalMode: string
  usedVersions: string[]; evidenceCount: number; fallbackReason?: string; modelInformation?: string
  latencyInformation: { retrievalMs: number; modelMs: number; totalMs: number }
  conversationId?: string; onlineStatus: string
}
export type TeachingStyle = 'SIMPLE'|'PRINCIPLE'|'EXAMPLE'|'COMPARE'
export type TeachingResult = { mode: string; reason?: string; citations: RagAnswer['citations']
  lessons: { unitId: string; evidenceId: string; fact: string; principle?: string; conditions: string
    example?: string; exampleStatus?: string; softwareVersion: string }[] }
export type RagTurn = { id: string; question: string; version?: string; time: string }
export type ReviewCase = {
  id: string; versionId: string; environment: string; queryType: string; users: number
  state: string; outcome?: string; candidateId?: string
}
type Metrics = { count: number; wrongVersion: number; unrelated: number; traceable: number; hit: boolean }
type Report = { previous: Metrics; current: Metrics; candidate?: Metrics; regression: boolean }
export type ReviewRun = { id: string; query: string; report: Report; time: string }
const cases = base + '/manage/cases'

export const evidenceRag = {
  answer: (body: {query: string; domain?: Domain; softwareVersion?: string; scope: Scope
    allowOnline: boolean; allowPrivateModel: boolean; conversationId?: string}) =>
    http.post<ApiResponse<RagAnswer>>(base + '/rag/answer', body, { timeout: 115000 }),
  explain: (input: {query:string; softwareVersion?:string; style:TeachingStyle}) =>
    http.post<ApiResponse<TeachingResult>>(base + '/rag/explain',input,{timeout:45000}),
  history: () => http.get<ApiResponse<RagTurn[]>>(base + '/rag/history'),
  feedback: (version: string, correction: boolean, body: {ordinal: number; category: string; queryType: string; environment: string}) =>
    http.request({url:base + '/versions/' + version + '/feedback',method:correction?'PUT':'POST',data:body}),
  cases: () => http.get<ApiResponse<ReviewCase[]>>(cases),
  recheck: (id: string) => http.post<ApiResponse<ReviewCase>>(cases + '/' + id + '/recheck'),
  close: (id: string, outcome: string) => http.post<ApiResponse<ReviewCase>>(cases + '/' + id + '/close',{outcome}),
  runs: (id: string) => http.get<ApiResponse<ReviewRun[]>>(cases + '/' + id + '/runs'),
  evaluate: (id: string, input: {query: string; softwareVersion: string; expectedTerm: string}) =>
    http.post<ApiResponse<Report>>(cases + '/' + id + '/evaluate',input),
}
