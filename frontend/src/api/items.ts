import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Item } from '../types'

const KEY = ['items']

export interface ItemInput {
  itemCategoryId: string
  name: string
  creditPrice: number
  cashPrice: number
  unit: string
}

export function useItems() {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => (await api.get<Item[]>('/api/items')).data,
  })
}

export function useCreateItem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: ItemInput) => (await api.post<Item>('/api/items', input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateItem() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: ItemInput }) =>
      (await api.put<Item>(`/api/items/${id}`, input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}
