import http from './http'
import type { ApiResponse } from '../types/api'
import type { DailyTrend, StatisticsQuery, StatisticsSummary, SubjectDistribution } from '../types/statistics'

export const statisticsApi = {
  summary: (params: StatisticsQuery) => http.get<ApiResponse<StatisticsSummary>>('/statistics/summary', { params }),
  dailyTrend: (params: StatisticsQuery) => http.get<ApiResponse<DailyTrend[]>>('/statistics/daily-trend', { params }),
  subjectDistribution: (params: StatisticsQuery) => http.get<ApiResponse<SubjectDistribution[]>>('/statistics/subject-distribution', { params }),
}
