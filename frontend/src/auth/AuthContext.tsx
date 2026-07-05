import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { api } from '../lib/api'
import type { Tenant, User } from '../types'

interface AuthContextValue {
  user: User | null
  tenant: Tenant | null
  loading: boolean
  isAdmin: boolean
  isSuperAdmin: boolean
  enabledFeatures: string[]
  hasFeature: (key: string) => boolean
  login: (username: string, password: string, tenantSlug?: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)
console.log("Auth",AuthContext)
const DEFAULT_BRAND_COLOR = '#1e293b' // slate-800

function applyBranding(tenant: Tenant | null) {
  const color = tenant?.primaryColor ?? DEFAULT_BRAND_COLOR
  document.documentElement.style.setProperty('--color-brand', color)
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [tenant, setTenant] = useState<Tenant | null>(null)
  const [enabledFeatures, setEnabledFeatures] = useState<string[]>([])
  const [loading, setLoading] = useState(true)
  const queryClient = useQueryClient()
  const mountedRef = useRef(false)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  const loadSession = useCallback(async () => {
    try {
      const userRes = await api.get<User>('/api/auth/me')
      if (!mountedRef.current) return
      setUser(userRes.data)

      // Load branding and features in parallel.
      const [tenantRes, featuresRes] = await Promise.allSettled([
        api.get<Tenant | null>('/api/tenants/current'),
        api.get<string[]>('/api/features/mine'),
      ])

      if (!mountedRef.current) return

      const tenantData =
        tenantRes.status === 'fulfilled' ? tenantRes.value.data : null
      const featuresData =
        featuresRes.status === 'fulfilled' ? featuresRes.value.data : []

      setTenant(tenantData)
      setEnabledFeatures(featuresData ?? [])
      applyBranding(tenantData)
    } catch {
      if (mountedRef.current) {
        setUser(null)
        setTenant(null)
        setEnabledFeatures([])
        applyBranding(null)
      }
    } finally {
      if (mountedRef.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadSession()
  }, [loadSession])

  // Re-apply CSS variable whenever tenant branding changes.
  useEffect(() => {
    applyBranding(tenant)
  }, [tenant])

  const login = useCallback(
    async (username: string, password: string, tenantSlug?: string) => {
      const res = await api.post<User>('/api/auth/login', {
        username,
        password,
        ...(tenantSlug ? { tenantSlug } : {}),
      })
      setUser(res.data)

      // Load branding and features after login.
      const [tenantRes, featuresRes] = await Promise.allSettled([
        api.get<Tenant | null>('/api/tenants/current'),
        api.get<string[]>('/api/features/mine'),
      ])
      const tenantData =
        tenantRes.status === 'fulfilled' ? tenantRes.value.data : null
      const featuresData =
        featuresRes.status === 'fulfilled' ? featuresRes.value.data : []
      setTenant(tenantData)
      setEnabledFeatures(featuresData ?? [])
      applyBranding(tenantData)
    },
    [],
  )

  const logout = useCallback(async () => {
    try {
      await api.post('/api/auth/logout')
    } finally {
      setUser(null)
      setTenant(null)
      setEnabledFeatures([])
      applyBranding(null)
      queryClient.clear()
    }
  }, [queryClient])

  const hasFeature = useCallback(
    (key: string) => enabledFeatures.includes(key),
    [enabledFeatures],
  )

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      tenant,
      loading,
      isAdmin: user?.role === 'admin' || user?.role === 'super_admin',
      isSuperAdmin: user?.role === 'super_admin',
      enabledFeatures,
      hasFeature,
      login,
      logout,
    }),
    [user, tenant, loading, enabledFeatures, hasFeature, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within an AuthProvider')
  return ctx
}
