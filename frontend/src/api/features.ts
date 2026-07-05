import { useQuery } from '@tanstack/react-query'
import { api } from '../lib/api'

export const FEATURES_MINE_KEY = ['features', 'mine']

/**
 * Returns the list of enabled feature keys for the current session's tenant.
 * Returns [] for super_admin without active impersonation.
 */
export function useMyFeatures() {
  return useQuery({
    queryKey: FEATURES_MINE_KEY,
    queryFn: async () => (await api.get<string[]>('/api/features/mine')).data,
    staleTime: 60_000,
  })
}
