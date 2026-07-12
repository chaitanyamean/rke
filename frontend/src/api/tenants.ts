import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Tenant, TenantFeature } from '../types'

const KEY = ['tenants']
const FEATURES_KEY = ['tenant-features']

export interface TenantInput {
  name: string
  slug: string
  primaryColor?: string
  active: boolean
}

/** Payload for creating a brand-new tenant plus its first admin login in one step. */
export interface TenantCreateInput extends TenantInput {
  adminFullName: string
  adminUsername: string
  adminPassword: string
}

/** Response from tenant creation — never carries the admin password or its hash. */
export interface TenantCreateResult {
  tenant: Tenant
  adminCreated: boolean
  adminUsername: string
}

// ── Tenant CRUD (super_admin) ────────────────────────────────────────────────

export function useTenants() {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => (await api.get<Tenant[]>('/api/admin/tenants')).data,
  })
}

export function useTenant(id: string) {
  return useQuery({
    queryKey: [...KEY, id],
    queryFn: async () => (await api.get<Tenant>(`/api/admin/tenants/${id}`)).data,
    enabled: Boolean(id),
  })
}

/** Creates a tenant together with its first admin login, in one atomic step. */
export function useCreateTenant() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (input: TenantCreateInput) =>
      (await api.post<TenantCreateResult>('/api/admin/tenants', input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateTenant() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, input }: { id: string; input: TenantInput }) =>
      (await api.put<Tenant>(`/api/admin/tenants/${id}`, input)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUploadLogo() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({ id, file }: { id: string; file: File }) => {
      const form = new FormData()
      form.append('file', file)
      return (
        await api.post<Tenant>(`/api/admin/tenants/${id}/logo`, form, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
      ).data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

// ── Impersonation ────────────────────────────────────────────────────────────

export function useStartImpersonation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (tenantId: string) =>
      (await api.post<Tenant>(`/api/admin/tenants/${tenantId}/impersonate`)).data,
    onSuccess: () => {
      // Invalidate everything — tenant context changed.
      qc.clear()
    },
  })
}

export function useExitImpersonation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => api.delete('/api/admin/tenants/impersonate'),
    onSuccess: () => qc.clear(),
  })
}

// ── Feature toggles (super_admin) ────────────────────────────────────────────

export function useTenantFeatures(tenantId: string) {
  return useQuery({
    queryKey: [...FEATURES_KEY, tenantId],
    queryFn: async () =>
      (await api.get<TenantFeature[]>(`/api/admin/tenants/${tenantId}/features`)).data,
    enabled: Boolean(tenantId),
  })
}

export function useSetTenantFeature() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({
      tenantId,
      featureKey,
      enabled,
    }: {
      tenantId: string
      featureKey: string
      enabled: boolean
    }) =>
      (
        await api.put<TenantFeature>(
          `/api/admin/tenants/${tenantId}/features/${featureKey}`,
          { enabled },
        )
      ).data,
    onSuccess: (_data, vars) =>
      qc.invalidateQueries({ queryKey: [...FEATURES_KEY, vars.tenantId] }),
  })
}
