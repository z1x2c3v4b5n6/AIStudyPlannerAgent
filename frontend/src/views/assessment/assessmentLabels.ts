import type { Difficulty, Mode, State } from '../../api/assessment'

export const modeText: Record<Mode,string>={TOPIC:'知识点练习',LEARNING_PATH:'学习路径练习',WRONG_ANSWERS:'错题复习',COMPREHENSIVE:'综合测评'}
export const difficultyText: Record<Difficulty,string>={BASIC:'基础',INTERMEDIATE:'中级',ADVANCED:'进阶'}
export const bankText: Record<State,string>={DRAFT:'草稿',PENDING_REVIEW:'待审核',APPROVED:'已通过审核',PUBLISHED:'已发布',REJECTED:'已拒绝',ARCHIVED:'已归档'}
export const statusText={IN_PROGRESS:'作答中',SUBMITTED:'已提交',CANCELLED:'已取消',EXPIRED:'已过期'}
export const levelText: Record<string,string>={INSUFFICIENT_DATA:'数据不足',NEEDS_PRACTICE:'需要加强',DEVELOPING:'基本理解',CONSISTENT:'表现稳定'}
export const gradeText: Record<string,string>={CORRECT:'正确',INCORRECT:'错误',NEEDS_REVIEW:'待复核',UNANSWERED:'未作答',COVERED:'可能覆盖',MISSING:'可能遗漏',UNCERTAIN:'无法确定'}
export const errorText: Record<string,string>={CONCEPT_CONFUSION:'概念混淆',MISSING_CONCEPT:'知识点遗漏',VERSION_ERROR:'版本错误',CONDITION_ERROR:'条件理解错误',PREREQUISITE_GAP:'前置知识不足',PARTIALLY_CORRECT:'部分正确',INSUFFICIENT_EVIDENCE:'评分证据不足'}
