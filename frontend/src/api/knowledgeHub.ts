import http from './http'
import type { ApiResponse } from '../types/api'

const base = '/knowledge-hub'
export type Domain = 'JAVA_BACKEND' | 'AI_APPLICATION'
export type Scope = 'PUBLIC' | 'PRIVATE' | 'BOTH'
export interface Version {
  id: string
  documentId: string
  status: string
  title: string
  softwareVersion?: string
  hash: string
  createdAt: string
}
export interface Source {
  id: string
  name: string
  url: string
  domain: Domain
  softwareVersion?: string
  checkedAt?: string
  lastError?: string
}
export interface Chunk { ordinal: number; heading: string; line: number; excerpt: string }
export interface Evidence extends Chunk {
  scope: string
  versionId?: string
  title: string
  source?: string
  url?: string
  softwareVersion?: string
  collectedAt: string
  matchedTerms: string[]
}
export interface SearchResult {
  structured: { unit: { id: string; topic: string; keyPoints: string[] }; matchedTerms: string[] }[]
  published: Evidence[]
  online: Evidence[]
  onlineStatus: string
  trigger?: string
}
export interface Detail { version: Version; text: string; chunks: Chunk[] }
export interface Difference { activeVersion?: string; added: Chunk[]; removed: Chunk[]; addedCount: number; removedCount: number }
export interface Signal { versionId: string; environment: string; queryType: string; users: number }
export interface Audit { operation: string; reason: string; time: string; versionId: string; previous?: string; hash: string }
interface DocumentPayload { title: string; text: string; domain: Domain; unitId?: string; softwareVersion?: string }
const manage = base + '/manage'
const version = (id: string) => manage + '/versions/' + id

export const knowledgeHubApi = {
  capabilities: () => http.get<ApiResponse<{reviewer: boolean; onlineEnabled: boolean}>>(base + '/capabilities'),
  search: (input: { query: string; scope: Scope; online: boolean; domain?: Domain; softwareVersion?: string }) =>
    http.post<ApiResponse<SearchResult>>(base + '/search', input, { timeout: 18000 }),
  personal: () => http.get<ApiResponse<Version[]>>(base + '/personal'),
  import: (input: DocumentPayload) => http.post<ApiResponse<Version>>(base + '/personal', input),
  remove: (id: string) => http.delete(base + '/personal/' + id),
  view: (id: string) => http.get<ApiResponse<Detail>>(base + '/versions/' + id),
  feedback: (id: string, input: { category: string; queryType: string; ordinal: number; environment?: string }) =>
    http.post(base + '/versions/' + id + '/feedback', input),
  sources: () => http.get<ApiResponse<Source[]>>(manage + '/sources'),
  source: (input: { name: string; url: string; domain: Domain; softwareVersion?: string }) =>
    http.post<ApiResponse<Source>>(manage + '/sources', input),
  stage: (id: string, input: DocumentPayload) => http.post<ApiResponse<Version>>(manage + '/sources/' + id + '/candidate', input),
  refresh: (id: string, unitId?: string) => http.post<ApiResponse<Version>>(manage + '/sources/' + id + '/refresh', {unitId}),
  candidates: () => http.get<ApiResponse<Version[]>>(manage + '/versions'),
  inspect: (id: string) => http.get<ApiResponse<Detail>>(version(id)),
  diff: (id: string) => http.get<ApiResponse<Difference>>(version(id) + '/difference'),
  history: (id: string) => http.get<ApiResponse<Audit[]>>(manage + '/documents/' + id + '/history'),
  signals: () => http.get<ApiResponse<Signal[]>>(manage + '/signals'),
  extract: (id: string) => http.post<ApiResponse<{status: string; suggestions: {kind: string; ordinal: number; excerpt: string}[]}>>(
    version(id) + '/extract', undefined, { timeout: 95000 }),
  decision: (id: string, decision: string, reason: string) =>
    http.post<ApiResponse<Version>>(version(id) + '/decision', {decision, reason}),
}
