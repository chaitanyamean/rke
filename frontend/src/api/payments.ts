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

/** Payload for correcting an existing payment/receipt — bill number is not editable. */
export interface PaymentUpdateInput {
  farmerId: string
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

/** Fetches a single payment or receipt by id — admin only (enforced server-side). */
export function usePayment(direction: 'payment' | 'receipt', id: string | null) {
  return useQuery({
    queryKey: ['payment', direction, id],
    queryFn: async () => (await api.get<Transaction>(`/api/payments/${direction}/${id}`)).data,
    enabled: !!id,
  })
}

export function useUpdatePayment() {
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: PaymentUpdateInput }) =>
      (await api.put<Transaction>(`/api/payments/payment/${id}`, input)).data,
  })
}

export function useUpdateReceipt() {
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: PaymentUpdateInput }) =>
      (await api.put<Transaction>(`/api/payments/receipt/${id}`, input)).data,
  })
}

export function useFarmerBalance(farmerId: string | null) {
  return useQuery({
    queryKey: ['farmer-balance', farmerId],
    queryFn: async () => (await api.get<number>(`/api/farmers/${farmerId}/balance`)).data,
    enabled: !!farmerId,
  })
}
