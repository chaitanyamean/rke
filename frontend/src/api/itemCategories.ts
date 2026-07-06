import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { ItemCategory } from '../types'

const KEY = ['item-categories']

export interface ItemCategoryInput {
  name: string
  description?: string
}

export function useItemCategories(search?: string) {
  return useQuery({
    queryKey: [...KEY, search ?? ''],
    queryFn: async () =>
      (await api.get<ItemCategory[]>('/api/item-categories', { params: search ? { search } : {} }))
        .data,
    refetchOnWindowFocus: true,
  })
}

export function useCreateItemCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: ItemCategoryInput) =>
      (await api.post<ItemCategory>('/api/item-categories', input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateItemCategory() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: ItemCategoryInput }) =>
      (await api.put<ItemCategory>(`/api/item-categories/${id}`, input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}
