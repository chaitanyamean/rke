import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Transaction } from '../types'

export interface SaleLineItemInput {
  itemId: string
  quantity: number
  price?: number
}

export interface SaleInput {
  farmerId: string
  billNumberTypeId: string
  billNumber?: string
  transactionDate: string
  items: SaleLineItemInput[]
  remarks?: string
}

export function useCreateCashSale() {
  return useMutation({
    mutationFn: async (input: SaleInput) =>
      (await api.post<Transaction>('/api/sales/cash', input)).data,
  })
}

export function useCreateCreditSale() {
  return useMutation({
    mutationFn: async (input: SaleInput) =>
      (await api.post<Transaction>('/api/sales/credit', input)).data,
  })
}

export function useTransactionsByFarmer(farmerId: string | null) {
  return useQuery({
    queryKey: ['transactions', 'farmer', farmerId],
    queryFn: async () =>
      (await api.get<Transaction[]>(`/api/sales/farmer/${farmerId}`)).data,
    enabled: !!farmerId,
  })
}

export async function fetchBillNumberPreview(categoryId: string): Promise<string> {
  return (await api.get<string>(`/api/bill-numbers/preview?categoryId=${categoryId}`)).data
}
