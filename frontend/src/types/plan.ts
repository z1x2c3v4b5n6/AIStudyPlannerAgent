export type PlanStatus = 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'
export type PlanItemStatus = 'PENDING' | 'COMPLETED' | 'SKIPPED'
export type PlanGeneratorType = 'AI' | 'RULE'
export interface PlanDraftRequest { planDate:string; startTime:string; availableMinutes:number; requirement?:string|null; selectedSubjectIds:number[]; selectedTaskIds:number[] }
export interface PlanDraftItem { sequenceNo:number; taskId:number; taskTitle:string; subjectId:number; subjectName:string; subjectColor:string|null; startAt:string; endAt:string; plannedMinutes:number; reason:string }
export interface PlanDraft { draftId:string; planDate:string; startTime:string; availableMinutes:number; plannedMinutes:number; requirement:string|null; summary:string; items:PlanDraftItem[] }
export interface AiPlanDraftResult { generatorType:PlanGeneratorType; provider:string; model:string; fallbackUsed:boolean; fallbackReason:string|null; draft:PlanDraft }
export interface NaturalLanguagePlanParseRequest {
  text:string
  fallbackPlanDate?:string|null
  fallbackStartTime?:string|null
  fallbackAvailableMinutes?:number|null
}
export interface NaturalLanguagePlanParseResult {
  planDate:string|null
  startTime:string|null
  availableMinutes:number|null
  requirement:string
  preferences:string[]
  topicKeywords:string[]
  candidateSubjects:CandidateSubject[]
  ambiguousTopics:string[]
  needsSubjectSelection:boolean
  selectedSubjectIds:number[]
  unmatchedKeywords:string[]
  needsClarification:boolean
  clarificationMessage:string|null
  aiParsed:boolean
}
export type SubjectMatchLevel = 'EXACT' | 'UNIQUE' | 'RELATED'
export interface CandidateSubject {
  subjectId:number
  subjectName:string
  subjectColor:string|null
  pendingTaskCount:number
  matchReason:string
  matchLevel:SubjectMatchLevel
  recommended:boolean
}
export interface CandidateTasksRequest {
  selectedSubjectIds:number[]
  planDate:string
  requirement?:string|null
}
export interface QuickCreatePlanTaskRequest {
  topicName:string
  taskTitle:string
  planDate:string
  estimatedMinutes:number
  requirement?:string|null
}
export interface QuickCreatePlanTaskResult {
  subjectId:number
  subjectName:string
  taskId:number
  taskTitle:string
  createdSubject:boolean
  createdTask:boolean
}
export interface SelectablePlanTask {
  taskId:number
  taskTitle:string
  subjectId:number
  subjectName:string
  subjectColor:string|null
  goalTitle:string|null
  estimatedMinutes:number
  deadline:string|null
  priority:number
  status:'TODO'|'IN_PROGRESS'
  matchReason:string
  recommended:boolean
}
export interface PlanConfirmRequest { draftId:string; planDate:string; availableMinutes:number; plannedMinutes:number; requirement?:string|null; summary:string; items:Array<Pick<PlanDraftItem,'sequenceNo'|'taskId'|'startAt'|'endAt'|'plannedMinutes'|'reason'>> }
export interface PlanListItem { id:number; planDate:string; availableMinutes:number; plannedMinutes:number; requirement:string|null; summary:string; status:PlanStatus; totalItemCount:number; completedItemCount:number; skippedItemCount:number; pendingItemCount:number; completionPercentage:number; createdAt:string; updatedAt:string }
export interface PlanItem extends PlanDraftItem { id:number; status:PlanItemStatus }
export interface PlanDetail { id:number; sourceDraftId:string; planDate:string; availableMinutes:number; plannedMinutes:number; requirement:string|null; summary:string; status:PlanStatus; createdAt:string; updatedAt:string; items:PlanItem[] }
export interface PlanQuery { page:number; pageSize:number; startDate?:string; endDate?:string; status?:PlanStatus }
