import { Link } from 'react-router-dom'
import { useTenants, useStartImpersonation, useExitImpersonation } from '../../api/tenants'
import { useAuth } from '../../auth/AuthContext'
import { getErrorMessage } from '../../lib/api'
import { useState } from 'react'

export default function TenantListPage() {
  const { data: tenants = [], isLoading } = useTenants()
  const { tenant: currentTenant } = useAuth()
  const startImpersonation = useStartImpersonation()
  const exitImpersonation = useExitImpersonation()
  const [actionError, setActionError] = useState<string | null>(null)

  const handleImpersonate = async (id: string) => {
    setActionError(null)
    try {
      await startImpersonation.mutateAsync(id)
    } catch (err) {
      setActionError(getErrorMessage(err))
    }
  }

  const handleExitImpersonation = async () => {
    setActionError(null)
    try {
      await exitImpersonation.mutateAsync()
    } catch (err) {
      setActionError(getErrorMessage(err))
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Tenants</h1>
        <Link
          to="/admin/tenants/new"
          className="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white hover:opacity-90"
        >
          New tenant
        </Link>
      </div>

      {currentTenant && (
        <div className="flex items-center gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-800">
          <span>
            Impersonating: <strong>{currentTenant.name}</strong>
          </span>
          <button
            onClick={handleExitImpersonation}
            disabled={exitImpersonation.isPending}
            className="ml-auto rounded border border-amber-300 px-3 py-1 hover:bg-amber-100 disabled:opacity-60"
          >
            Exit impersonation
          </button>
        </div>
      )}

      {actionError && (
        <p className="text-sm text-red-600">{actionError}</p>
      )}

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-2 font-medium">Name</th>
              <th className="px-4 py-2 font-medium">Slug</th>
              <th className="px-4 py-2 font-medium">Status</th>
              <th className="px-4 py-2 font-medium">Branding</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-slate-400">
                  Loading…
                </td>
              </tr>
            )}
            {!isLoading && tenants.length === 0 && (
              <tr>
                <td colSpan={5} className="px-4 py-6 text-center text-slate-400">
                  No tenants
                </td>
              </tr>
            )}
            {tenants.map((t) => (
              <tr key={t.id} className="border-t border-slate-100">
                <td className="px-4 py-2 font-medium text-slate-800">
                  {t.logoUrl && (
                    <img
                      src={t.logoUrl}
                      alt=""
                      className="mr-2 inline-block h-5 w-5 rounded object-contain"
                    />
                  )}
                  {t.name}
                </td>
                <td className="px-4 py-2 font-mono text-xs text-slate-500">{t.slug}</td>
                <td className="px-4 py-2">
                  <span
                    className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                      t.active
                        ? 'bg-green-100 text-green-700'
                        : 'bg-slate-100 text-slate-500'
                    }`}
                  >
                    {t.active ? 'Active' : 'Inactive'}
                  </span>
                </td>
                <td className="px-4 py-2">
                  {t.primaryColor ? (
                    <span className="flex items-center gap-1.5 text-xs text-slate-600">
                      <span
                        className="inline-block h-4 w-4 rounded-full border border-slate-300"
                        style={{ backgroundColor: t.primaryColor }}
                      />
                      {t.primaryColor}
                    </span>
                  ) : (
                    <span className="text-xs text-slate-400">—</span>
                  )}
                </td>
                <td className="px-4 py-2">
                  <div className="flex justify-end gap-3 text-sm">
                    <Link
                      to={`/admin/tenants/${t.id}/features`}
                      className="text-slate-500 hover:underline"
                    >
                      Features
                    </Link>
                    <Link
                      to={`/admin/tenants/${t.id}/edit`}
                      className="text-slate-600 hover:underline"
                    >
                      Edit
                    </Link>
                    <button
                      onClick={() => handleImpersonate(t.id)}
                      disabled={startImpersonation.isPending}
                      className="text-amber-600 hover:underline disabled:opacity-60"
                    >
                      Impersonate
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
