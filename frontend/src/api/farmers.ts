import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Farmer } from '../types'

const KEY = ['farmers']

export interface FarmerFilters {
  name?: string
  fatherName?: string
  villageId?: string
  mobile?: string
}

export interface FarmerInput {
  name: string
  fatherName?: string
  villageId: string
  address?: string
  mobileNumber?: string
  reference?: string
}

export function useFarmers(filters: FarmerFilters = {}, enabled = true) {
  const params: Record<string, string> = {}
  if (filters.name) params.name = filters.name
  if (filters.fatherName) params.fatherName = filters.fatherName
  if (filters.villageId) params.villageId = filters.villageId
  if (filters.mobile) params.mobile = filters.mobile
  return useQuery({
    queryKey: [...KEY, params],
    queryFn: async () => (await api.get<Farmer[]>('/api/farmers', { params })).data,
    enabled,
  })
}

export function useCreateFarmer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: FarmerInput) => (await api.post<Farmer>('/api/farmers', input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateFarmer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: FarmerInput }) =>
      (await api.put<Farmer>(`/api/farmers/${id}`, input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}
