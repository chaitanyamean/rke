import { useParams, Link } from 'react-router-dom'
import { useTenant, useTenantFeatures, useSetTenantFeature } from '../../api/tenants'
import { getErrorMessage } from '../../lib/api'
import { useState } from 'react'

/**
 * Known feature keys. Add new entries here as features are introduced —
 * no schema change required (the DB stores arbitrary strings).
 */
const KNOWN_FEATURES: { key: string; label: string; description: string }[] = [
  {
    key: 'cotton_procurement',
    label: 'Cotton Procurement',
    description: 'Enables the Cotton Lot management module (Phase 6).',
  },
]

export default function TenantFeaturesPage() {
  const { id = '' } = useParams<{ id: string }>()
  const { data: tenant } = useTenant(id)
  const { data: features = [], isLoading } = useTenantFeatures(id)
  const setFeature = useSetTenantFeature()
  const [errors, setErrors] = useState<Record<string, string>>({})

  const isEnabled = (key: string) =>
    features.find((f) => f.featureKey === key)?.enabled ?? false

  const handleToggle = async (featureKey: string, enabled: boolean) => {
    setErrors((e) => ({ ...e, [featureKey]: '' }))
    try {
      await setFeature.mutateAsync({ tenantId: id, featureKey, enabled })
    } catch (err) {
      setErrors((e) => ({ ...e, [featureKey]: getErrorMessage(err) }))
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/admin/tenants" className="text-sm text-slate-500 hover:underline">
          ← Tenants
        </Link>
        <span className="text-slate-300">/</span>
        <h1 className="text-xl font-bold text-slate-800">
          {tenant?.name ?? 'Tenant'} — Feature Flags
        </h1>
      </div>

      <div className="rounded-lg border border-slate-200 bg-white shadow-sm">
        {isLoading ? (
          <p className="p-6 text-center text-slate-400">Loading…</p>
        ) : (
          <ul className="divide-y divide-slate-100">
            {KNOWN_FEATURES.map(({ key, label, description }) => (
              <li key={key} className="flex items-start gap-4 p-4">
                <div className="flex-1">
                  <p className="font-medium text-slate-800">{label}</p>
                  <p className="mt-0.5 text-sm text-slate-500">{description}</p>
                  <code className="mt-1 inline-block text-xs text-slate-400">
                    {key}
                  </code>
                  {errors[key] && (
                    <p className="mt-1 text-xs text-red-600">{errors[key]}</p>
                  )}
                </div>
                <button
                  onClick={() => handleToggle(key, !isEnabled(key))}
                  disabled={setFeature.isPending}
                  className={`mt-0.5 flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 transition-colors duration-200 focus:outline-none disabled:opacity-60 ${
                    isEnabled(key)
                      ? 'border-brand bg-brand'
                      : 'border-slate-300 bg-slate-200'
                  }`}
                  role="switch"
                  aria-checked={isEnabled(key)}
                >
                  <span
                    className={`h-4 w-4 translate-y-[-1px] transform rounded-full bg-white shadow transition-transform duration-200 ${
                      isEnabled(key) ? 'translate-x-5' : 'translate-x-0'
                    }`}
                  />
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
