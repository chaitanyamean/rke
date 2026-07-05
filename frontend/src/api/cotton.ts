import { useMutation, useQuery } from '@tanstack/react-query'
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

export function useCottonLots() {
  return useQuery({
    queryKey: ['cotton-lots'],
    queryFn: async () => (await api.get<CottonLot[]>('/api/cotton-lots')).data,
  })
}
