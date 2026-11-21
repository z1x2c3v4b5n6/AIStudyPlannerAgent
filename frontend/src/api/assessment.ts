import http from './http'
import type { ApiResponse } from '../types/api'

export type Mode = 'TOPIC' | 'LEARNING_PATH' | 'WRONG_ANSWERS' | 'COMPREHENSIVE'
export type Difficulty = 'BASIC' | 'INTERMEDIATE' | 'ADVANCED'
export type Kind = 'SINGLE_CHOICE' | 'SHORT_ANSWER'
export type State = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'REJECTED' | 'ARCHIVED'
export type Topic = { id: string; title: string; domain: string; prerequisites: string[] }
export type Session = {
  id: string; subjectId?: number; pathId?: number; mode: Mode
  status: 'IN_PROGRESS' | 'SUBMITTED' | 'CANCELLED' | 'EXPIRED'
  startedAt: string; expiresAt: string; submittedAt?: string; scoringVersion: number
}
export type Source = {
  versionId: string; ordinal: number; line: number; title: string; url?: string
  softwareVersion: string; excerpt: string; current: boolean
}
export type Criterion = { concept: string; accepted: string[] }
export type BankEntry = {
  questionId: number; versionId: number; revision: number; unitId: string; kind: Kind; difficulty: Difficulty
  state: State; softwareVersion: string; stem: string; publicBank: boolean; needsSourceReview: boolean
}
export type Definition = {
  options: {text: string; errorType?: string}[] | null; correctIndex?: number; referenceAnswer: string
  explanation: string; rubric?: Criterion[]; commonErrors?: string[]; quote: string
}
export type Managed = { question: BankEntry; definition: Definition; source?: Source }
export type Item = {
  ordinal: number; questionId: number; versionId: number; unitId: string; kind: Kind; difficulty: Difficulty
  softwareVersion: string; stem?: string; options: string[]; answer?: string; available: boolean
}
export type Mark = {
  ordinal: number; grade: string; errorType?: string; correctIndex?: number; correctAnswer?: string; explanation?: string
  rubric?: Criterion[]; source?: Source; assistance?: {
    status: string; reason?: string; rubricFeedback?: {criteria: {index: number; status: string; quote: string}[]; factError: string; version: string}
  }
}
export type Attempt = { session: Session; items: Item[]; marks: Mark[]; total: number; scored: number; correct: number; needsReview: number }
type Counts = { total: number; correct: number }
export type Performance = { topic: string; unitId: string; softwareVersion: string; level: string; newQuestions: Counts; repeats: Counts
  consecutiveErrors: number; byDifficulty: Record<Difficulty, Counts> }
type Summary = { days: number; minimumDistinct: number; completedSessions: number; incorrect: number; needsReview: number; truncated: boolean; topics: Performance[] }
type ReviewProgress = { accepted: number; completed: number; effects: { itemId: number; taskId: number; unitId: string; version: string
  difficulty: Difficulty; before: Counts; after: Counts; sufficient: boolean }[] }
type Wrong = { sessionId: string; ordinal: number; unitId: string; topic: string; softwareVersion: string; errorType?: string; submittedAt: string }
export type StartForm = { mode: Mode; subjectId?: number; pathId?: number; unitId?: string; softwareVersion?: string
  difficulty?: Difficulty; count: number; scope?: string[] }
export type GenerationForm = { unitId: string; softwareVersion: string; difficulty: Difficulty; kind: Kind; count: number
  publicBank: boolean; subjectId?: number; pathId?: number }
const base = '/assessments'
const session = (id: string) => `${base}/sessions/${id}`
export const assessmentApi = {
  topics: () => http.get<ApiResponse<Topic[]>>(base+'/topics'),
  bank: (unitId?: string) => http.get<ApiResponse<BankEntry[]>>(base+'/questions',{params:{unitId}}),
  manage: () => http.get<ApiResponse<BankEntry[]>>(base+'/manage/questions'),
  inspect: (id: number) => http.get<ApiResponse<Managed>>(base+'/manage/questions/'+id),
  decide: (id: number,state: State,rationale: string) =>
    http.post<ApiResponse<Managed>>(base+'/manage/questions/'+id+'/decision',{state,rationale}),
  generate: (body: GenerationForm) => http.post<ApiResponse<{status:string; questions:Managed[]}>>(base+'/generate',body,{timeout:45000}),
  start: (body: StartForm) => http.post<ApiResponse<{id:string; count:number; url:string}>>(base+'/sessions',body),
  history: (page: number) => http.get<ApiResponse<Session[]>>(base+'/sessions',{params:{page}}),
  get: (id: string) => http.get<ApiResponse<Attempt>>(session(id)),
  save: (id: string,ordinal: number,answer: string) =>
    http.put<ApiResponse<Attempt>>(`${session(id)}/items/${ordinal}`,{answer}),
  submit: (id: string) => http.post<ApiResponse<Attempt>>(session(id)+'/submit'),
  cancel: (id: string) => http.post<ApiResponse<Attempt>>(session(id)+'/cancel'),
  assist: (id: string,ordinal: number) =>
    http.post<ApiResponse<Attempt>>(`${session(id)}/items/${ordinal}/assistance`,undefined,{timeout:45000}),
  performance: (params: {days?:number; subjectId?:number; unitId?:string}={}) =>
    http.get<ApiResponse<Summary>>(base+'/performance',{params}),
  wrong: (params: {page:number; subjectId?:number; unitId?:string}) =>
    http.get<ApiResponse<Wrong[]>>(base+'/wrong-answers',{params}),
  reviewProgress: () => http.get<ApiResponse<ReviewProgress>>(base+'/review-progress'),
}
