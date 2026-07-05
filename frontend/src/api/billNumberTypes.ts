import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { BillNumberType } from '../types'

const KEY = ['bill-number-types']

export interface BillNumberTypeInput {
  name: string
  itemCategoryId: string
  description?: string
}

export function useBillNumberTypes() {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => (await api.get<BillNumberType[]>('/api/bill-number-types')).data,
  })
}

export function useCreateBillNumberType() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: BillNumberTypeInput) =>
      (await api.post<BillNumberType>('/api/bill-number-types', input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateBillNumberType() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: BillNumberTypeInput }) =>
      (await api.put<BillNumberType>(`/api/bill-number-types/${id}`, input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}
