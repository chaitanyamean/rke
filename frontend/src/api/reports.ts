import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export interface FarmerLedgerRow {
  transactionDate: string
  billNumber: string
  transactionType: string
  grandTotal: number
  runningBalance: number
  interestAmount: number
}

export interface VillageOutstandingRow {
  villageId: string
  villageName: string
  outstandingBalance: number
}

export interface ItemSalesRow {
  itemId: string
  itemName: string
  categoryId: string
  categoryName: string
  totalQuantity: number
  totalAmount: number
}

export interface DateSalesRow {
  date: string
  cashSalesTotal: number
  creditSalesTotal: number
  dayTotal: number
}

export interface DatePaymentsRow {
  date: string
  paymentsTotal: number
  receiptsTotal: number
  dayTotal: number
}

export interface DateRangeFilter {
  fromDate?: string
  toDate?: string
  includeVoided?: boolean
}

// ─── hooks ───────────────────────────────────────────────────────────────────

export function useFarmerLedger(
  farmerId: string | null,
  filter: DateRangeFilter,
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['report', 'farmer-ledger', farmerId, filter],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (filter.fromDate) params.fromDate = filter.fromDate
      if (filter.toDate) params.toDate = filter.toDate
      if (filter.includeVoided) params.includeVoided = 'true'
      return (
        await api.get<FarmerLedgerRow[]>(`/api/reports/farmer-ledger/${farmerId}`, { params })
      ).data
    },
    enabled: enabled && !!farmerId,
  })
}

export function useVillageOutstandings(villageId: string | null, enabled: boolean) {
  return useQuery({
    queryKey: ['report', 'village-outstandings', villageId],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (villageId) params.villageId = villageId
      return (await api.get<VillageOutstandingRow[]>('/api/reports/village-outstandings', { params }))
        .data
    },
    enabled,
  })
}

export function useItemSales(
  filter: DateRangeFilter & { categoryId?: string },
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['report', 'item-sales', filter],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (filter.fromDate) params.fromDate = filter.fromDate
      if (filter.toDate) params.toDate = filter.toDate
      if (filter.categoryId) params.categoryId = filter.categoryId
      if (filter.includeVoided) params.includeVoided = 'true'
      return (await api.get<ItemSalesRow[]>('/api/reports/item-sales', { params })).data
    },
    enabled,
  })
}

export function useDateSales(filter: DateRangeFilter, enabled: boolean) {
  return useQuery({
    queryKey: ['report', 'date-sales', filter],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (filter.fromDate) params.fromDate = filter.fromDate
      if (filter.toDate) params.toDate = filter.toDate
      if (filter.includeVoided) params.includeVoided = 'true'
      return (await api.get<DateSalesRow[]>('/api/reports/date-sales', { params })).data
    },
    enabled,
  })
}

export function useDatePayments(filter: DateRangeFilter, enabled: boolean) {
  return useQuery({
    queryKey: ['report', 'date-payments', filter],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (filter.fromDate) params.fromDate = filter.fromDate
      if (filter.toDate) params.toDate = filter.toDate
      if (filter.includeVoided) params.includeVoided = 'true'
      return (await api.get<DatePaymentsRow[]>('/api/reports/date-payments', { params })).data
    },
    enabled,
  })
}
