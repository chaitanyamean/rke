import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { StaffRole } from '../types'

const KEY = ['staff-users']

export interface StaffUser {
  id: string
  tenantId: string | null
  username: string
  fullName: string | null
  role: StaffRole
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface StaffUserCreateInput {
  fullName: string
  username: string
  password: string
}

export interface StaffUserUpdateInput {
  fullName: string
  active: boolean
  /** Optional — leave blank to keep the current password unchanged. */
  newPassword?: string
}

/** Lists staff users for the current tenant (admin/super_admin only — enforced server-side). */
export function useStaffUsers() {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => (await api.get<StaffUser[]>('/api/staff-users')).data,
  })
}

export function useCreateStaffUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: StaffUserCreateInput) =>
      (await api.post<StaffUser>('/api/staff-users', input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateStaffUser() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: StaffUserUpdateInput }) =>
      (await api.put<StaffUser>(`/api/staff-users/${id}`, input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}
