export type PlanStatus = 'CONFIRMED' | 'COMPLETED' | 'CANCELLED'
export type PlanItemStatus = 'PENDING' | 'COMPLETED' | 'SKIPPED'
export interface PlanDraftRequest { planDate:string; startTime:string; availableMinutes:number; requirement?:string|null }
export interface PlanDraftItem { sequenceNo:number; taskId:number; taskTitle:string; subjectId:number; subjectName:string; subjectColor:string|null; startAt:string; endAt:string; plannedMinutes:number; reason:string }
export interface PlanDraft { draftId:string; planDate:string; startTime:string; availableMinutes:number; plannedMinutes:number; requirement:string|null; summary:string; items:PlanDraftItem[] }
export interface PlanConfirmRequest { draftId:string; planDate:string; availableMinutes:number; plannedMinutes:number; requirement?:string|null; summary:string; items:Array<Pick<PlanDraftItem,'sequenceNo'|'taskId'|'startAt'|'endAt'|'plannedMinutes'|'reason'>> }
export interface PlanListItem { id:number; planDate:string; availableMinutes:number; plannedMinutes:number; requirement:string|null; summary:string; status:PlanStatus; totalItemCount:number; completedItemCount:number; skippedItemCount:number; pendingItemCount:number; completionPercentage:number; createdAt:string; updatedAt:string }
export interface PlanItem extends PlanDraftItem { id:number; status:PlanItemStatus }
export interface PlanDetail { id:number; sourceDraftId:string; planDate:string; availableMinutes:number; plannedMinutes:number; requirement:string|null; summary:string; status:PlanStatus; createdAt:string; updatedAt:string; items:PlanItem[] }
export interface PlanQuery { page:number; pageSize:number; startDate?:string; endDate?:string; status?:PlanStatus }
