import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export interface DashboardSummary {
  date: string
  todayCashSales: number
  todayCreditSales: number
  todayTotalSales: number
  todayCashReceived: number
  todayPayments: number
  totalOutstanding: number
  customersWithOutstanding: number
  totalCustomers: number
}

export interface RecentTransaction {
  date: string
  type: string
  billNumber: string
  farmerName: string
  amount: number
}

export function useDashboardSummary() {
  return useQuery({
    queryKey: ['dashboard', 'summary'],
    queryFn: async () => (await api.get<DashboardSummary>('/api/reports/dashboard')).data,
  })
}

export function useRecentTransactions(limit = 8) {
  return useQuery({
    queryKey: ['dashboard', 'recent-transactions', limit],
    queryFn: async () =>
      (await api.get<RecentTransaction[]>('/api/reports/recent-transactions', { params: { limit } }))
        .data,
  })
}
