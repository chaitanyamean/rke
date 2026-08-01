import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { CottonLot } from '../types'

export interface CottonLotEntryInput {
  farmerId: string
  villageId: string
  quantity: number
  price: number
}

export interface CottonLotInput {
  vehicleRegistrationNumber?: string
  mutaHamaliName?: string
  commonPrice: number
  lotDate: string
  entries: CottonLotEntryInput[]
}

export function useSerialPreview() {
  return useQuery({
    queryKey: ['cotton-serial-preview'],
    queryFn: async () => (await api.get<string>('/api/cotton-lots/serial-preview')).data,
    staleTime: 0,
  })
}

export function useCreateCottonLot() {
  return useMutation({
    mutationFn: async (input: CottonLotInput) =>
      (await api.post<CottonLot>('/api/cotton-lots', input)).data,
  })
}

export function useCottonLot(id: string | null) {
  return useQuery({
    queryKey: ['cotton-lot', id],
    queryFn: async () => (await api.get<CottonLot>(`/api/cotton-lots/${id}`)).data,
    enabled: !!id,
  })
}

export function useUpdateCottonLot() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: CottonLotInput }) =>
      (await api.put<CottonLot>(`/api/cotton-lots/${id}`, input)).data,
    onSuccess: (_, { id }) => {
      queryClient.invalidateQueries({ queryKey: ['cotton-lot', id] })
      queryClient.invalidateQueries({ queryKey: ['cotton-lots'] })
    },
  })
}

export function useCottonLots(fromDate?: string, toDate?: string) {
  return useQuery({
    queryKey: ['cotton-lots', fromDate, toDate],
    queryFn: async () => {
      const params: Record<string, string> = {}
      if (fromDate) params.fromDate = fromDate
      if (toDate) params.toDate = toDate
      return (await api.get<CottonLot[]>('/api/cotton-lots', { params })).data
    },
  })
}
