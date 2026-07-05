import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Transaction } from '../types'

/** Fetches the original sale by bill number for the "prefill items" step. */
export function useOriginalTransaction(billNumber: string | null) {
  return useQuery({
    queryKey: ['return-original', billNumber],
    queryFn: async () =>
      (await api.get<Transaction>(`/api/returns/by-bill?billNumber=${encodeURIComponent(billNumber!)}`)).data,
    enabled: !!billNumber,
    retry: false,
  })
}

export interface ReturnLineItemInput {
  itemId: string
  quantity: number
}

export interface ReturnInput {
  farmerId: string
  originalBillNumber: string
  returnDate: string
  items: ReturnLineItemInput[]
  remarks?: string
}

export function useCreateReturn() {
  return useMutation({
    mutationFn: async (input: ReturnInput) =>
      (await api.post<Transaction>('/api/returns', input)).data,
  })
}
