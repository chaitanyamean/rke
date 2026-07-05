import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Transaction } from '../types'

export interface PaymentInput {
  farmerId: string
  billNumberTypeId: string
  billNumber: string
  transactionDate: string
  amount: number
  remarks?: string
}

export function useCreatePayment() {
  return useMutation({
    mutationFn: async (input: PaymentInput) =>
      (await api.post<Transaction>('/api/payments/payment', input)).data,
  })
}

export function useCreateReceipt() {
  return useMutation({
    mutationFn: async (input: PaymentInput) =>
      (await api.post<Transaction>('/api/payments/receipt', input)).data,
  })
}

export function useFarmerBalance(farmerId: string | null) {
  return useQuery({
    queryKey: ['farmer-balance', farmerId],
    queryFn: async () => (await api.get<number>(`/api/farmers/${farmerId}/balance`)).data,
    enabled: !!farmerId,
  })
}
