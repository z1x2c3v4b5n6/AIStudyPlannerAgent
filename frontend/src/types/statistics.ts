export interface StatisticsQuery {
  startDate?: string
  endDate?: string
}

export interface StatisticsSummary {
  startDate: string
  endDate: string
  totalMinutes: number
  recordCount: number
  activeDays: number
  averageDailyMinutes: number
  averageMinutesPerActiveDay: number
}

export interface DailyTrend {
  date: string
  totalMinutes: number
  recordCount: number
}

export interface SubjectDistribution {
  subjectId: number
  subjectName: string
  subjectColor: string | null
  totalMinutes: number
  recordCount: number
  percentage: number
}
