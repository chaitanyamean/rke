import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export interface FarmerLedgerRow {
  transactionId: string
  transactionDate: string
  billNumber: string
  transactionType: string
  direction: 'DEBIT' | 'CREDIT'
  // item-level (null for payment/receipt rows)
  categoryName: string | null
  itemName: string | null
  quantity: number | null
  price: number | null
  // transaction-level amounts (same across all item rows of same transaction)
  debitAmount: number
  creditAmount: number
  runningBalance: number
  interestAmount: number
  remarks: string | null
  // non-null only for cotton_procurement rows — the cotton_lots.id
  cottonLotId: string | null
}

export interface TransactionReportRow {
  transactionId: string
  transactionDate: string
  billNumber: string
  transactionType: string
  direction: 'DEBIT' | 'CREDIT'
  farmerName: string
  fatherName: string | null
  categoryName: string | null
  itemName: string | null
  quantity: number | null
  price: number | null
  debitAmount: number
  creditAmount: number
  remarks: string | null
}

export interface FarmerOutstandingRow {
  farmerId: string
  farmerName: string
  fatherName: string | null
  villageName: string | null
  outstandingBalance: number
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
  returnsTotal: number
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

export function useTransactionsReport(
  filter: DateRangeFilter & { farmerId?: string; billNumber?: string },
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['report', 'transactions', filter],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (filter.fromDate)   params.fromDate   = filter.fromDate
      if (filter.toDate)     params.toDate     = filter.toDate
      if (filter.farmerId)   params.farmerId   = filter.farmerId
      if (filter.billNumber) params.billNumber = filter.billNumber
      return (await api.get<TransactionReportRow[]>('/api/reports/transactions', { params })).data
    },
    enabled,
  })
}

export function useFarmerOutstandings(
  filter: DateRangeFilter & { villageId?: string },
  enabled: boolean,
) {
  return useQuery({
    queryKey: ['report', 'farmer-outstandings', filter],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (filter.fromDate) params.fromDate = filter.fromDate
      if (filter.toDate)   params.toDate   = filter.toDate
      if (filter.villageId) params.villageId = filter.villageId
      return (await api.get<FarmerOutstandingRow[]>('/api/reports/farmer-outstandings', { params })).data
    },
    enabled,
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
