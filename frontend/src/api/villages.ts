import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Village } from '../types'

const KEY = ['villages']

export interface VillageInput {
  name: string
  landmark?: string
}

export function useVillages(search?: string) {
  return useQuery({
    queryKey: [...KEY, search ?? ''],
    queryFn: async () =>
      (await api.get<Village[]>('/api/villages', { params: search ? { search } : {} })).data,
  })
}

export function useCreateVillage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: VillageInput) =>
      (await api.post<Village>('/api/villages', input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateVillage() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: VillageInput }) =>
      (await api.put<Village>(`/api/villages/${id}`, input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}
