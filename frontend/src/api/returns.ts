import { useMutation, useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Transaction, TransactionType } from '../types'

/** A line item of the original sale, enriched with remaining-returnable quantity. */
export interface OriginalSaleItem {
  itemId: string
  quantity: number
  price: number
  amount: number
  /** Already returned by prior ACTIVE returns against this bill. */
  alreadyReturnedQuantity: number
  /** The real cap for a new return — quantity minus alreadyReturnedQuantity. */
  returnableQuantity: number
}

export interface OriginalSale {
  id: string
  transactionNo: string
  farmerId: string
  billNumber: string
  transactionType: TransactionType
  transactionDate: string
  grandTotal: number
  items: OriginalSaleItem[]
}

/** Fetches the original sale by bill number for the "prefill items" step. */
export function useOriginalTransaction(billNumber: string | null) {
  return useQuery({
    queryKey: ['return-original', billNumber],
    queryFn: async () =>
      (await api.get<OriginalSale>(`/api/returns/by-bill?billNumber=${encodeURIComponent(billNumber!)}`)).data,
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

/** Payload for correcting an existing return — original bill/farmer are not editable. */
export interface ReturnUpdateInput {
  returnDate: string
  items: ReturnLineItemInput[]
  remarks?: string
}

/** Fetches a single return by id — admin only (enforced server-side). */
export function useReturn(id: string | null) {
  return useQuery({
    queryKey: ['return', id],
    queryFn: async () => (await api.get<Transaction>(`/api/returns/${id}`)).data,
    enabled: !!id,
  })
}

export function useUpdateReturn() {
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: ReturnUpdateInput }) =>
      (await api.put<Transaction>(`/api/returns/${id}`, input)).data,
  })
}
