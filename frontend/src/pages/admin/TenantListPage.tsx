import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  useTenants,
  useStartImpersonation,
  useExitImpersonation,
  useTenantAdmins,
  useResetAdminPassword,
  type TenantAdminUser,
} from '../../api/tenants'
import { useAuth } from '../../auth/AuthContext'
import { getErrorMessage } from '../../lib/api'

const MIN_PASSWORD_LENGTH = 8

// ── Inline admin password reset panel for one tenant ─────────────────────────

function AdminPasswordPanel({ tenantId, onClose }: { tenantId: string; onClose: () => void }) {
  const { data: admins = [], isLoading } = useTenantAdmins(tenantId)
  const resetPassword = useResetAdminPassword()

  // Per-admin form state keyed by admin id
  const [passwords, setPasswords]     = useState<Record<string, string>>({})
  const [showPass, setShowPass]       = useState<Record<string, boolean>>({})
  const [errors, setErrors]           = useState<Record<string, string>>({})
  const [success, setSuccess]         = useState<Record<string, boolean>>({})

  const setField = (adminId: string, val: string) => {
    setPasswords(p => ({ ...p, [adminId]: val }))
    setSuccess(s => ({ ...s, [adminId]: false }))
    setErrors(e => ({ ...e, [adminId]: '' }))
  }

  const handleReset = async (admin: TenantAdminUser) => {
    const pwd = passwords[admin.id] ?? ''
    if (!pwd) {
      setErrors(e => ({ ...e, [admin.id]: 'Password is required.' }))
      return
    }
    if (pwd.length < MIN_PASSWORD_LENGTH) {
      setErrors(e => ({ ...e, [admin.id]: `Min ${MIN_PASSWORD_LENGTH} characters.` }))
      return
    }
    try {
      await resetPassword.mutateAsync({ tenantId, adminId: admin.id, newPassword: pwd })
      setPasswords(p => ({ ...p, [admin.id]: '' }))
      setSuccess(s => ({ ...s, [admin.id]: true }))
    } catch (err) {
      setErrors(e => ({ ...e, [admin.id]: getErrorMessage(err) }))
    }
  }

  return (
    <tr>
      <td colSpan={5} className="bg-slate-50 px-4 pb-4 pt-2">
        <div className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-semibold text-slate-700">Reset Admin Password</h3>
            <button onClick={onClose} className="text-xs text-slate-400 hover:text-slate-600">✕ Close</button>
          </div>

          {isLoading && <p className="text-sm text-slate-400">Loading admins…</p>}
          {!isLoading && admins.length === 0 && (
            <p className="text-sm text-slate-400">No admin users found for this tenant.</p>
          )}

          <div className="space-y-4">
            {admins.map((admin) => (
              <div key={admin.id} className="flex flex-wrap items-end gap-3 border-b border-slate-100 pb-4 last:border-0 last:pb-0">
                <div className="min-w-[160px]">
                  <p className="text-xs text-slate-500">Admin</p>
                  <p className="font-medium text-slate-800">{admin.fullName || admin.username}</p>
                  <p className="font-mono text-xs text-slate-500">{admin.username}</p>
                </div>

                <div className="flex flex-1 items-end gap-2">
                  <label className="flex-1 block">
                    <span className="mb-1 block text-xs font-medium text-slate-600">
                      New Password <span className="text-slate-400">(min {MIN_PASSWORD_LENGTH} chars)</span>
                    </span>
                    <input
                      type={showPass[admin.id] ? 'text' : 'password'}
                      value={passwords[admin.id] ?? ''}
                      onChange={(e) => setField(admin.id, e.target.value)}
                      autoComplete="new-password"
                      className="w-full rounded-md border border-slate-300 px-3 py-1.5 text-sm"
                      placeholder="New password"
                    />
                  </label>
                  <button
                    type="button"
                    onClick={() => setShowPass(s => ({ ...s, [admin.id]: !s[admin.id] }))}
                    className="rounded-md border border-slate-300 px-2.5 py-1.5 text-xs text-slate-600 hover:bg-slate-100"
                  >
                    {showPass[admin.id] ? 'Hide' : 'Show'}
                  </button>
                  <button
                    onClick={() => handleReset(admin)}
                    disabled={resetPassword.isPending}
                    className="rounded-md bg-slate-900 px-4 py-1.5 text-sm font-medium text-white hover:bg-slate-700 disabled:opacity-60"
                  >
                    Reset
                  </button>
                </div>

                <div className="w-full">
                  {errors[admin.id] && <p className="text-xs text-red-600">{errors[admin.id]}</p>}
                  {success[admin.id] && <p className="text-xs text-green-600">Password updated successfully.</p>}
                </div>
              </div>
            ))}
          </div>
        </div>
      </td>
    </tr>
  )
}

// ── Main page ─────────────────────────────────────────────────────────────────

export default function TenantListPage() {
  const { data: tenants = [], isLoading } = useTenants()
  const { tenant: currentTenant } = useAuth()
  const startImpersonation = useStartImpersonation()
  const exitImpersonation = useExitImpersonation()
  const [actionError, setActionError] = useState<string | null>(null)
  // Which tenant's reset panel is open (null = none)
  const [resetOpenId, setResetOpenId] = useState<string | null>(null)

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

  const toggleReset = (id: string) =>
    setResetOpenId(prev => (prev === id ? null : id))

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

      {actionError && <p className="text-sm text-red-600">{actionError}</p>}

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
              <>
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
                        onClick={() => toggleReset(t.id)}
                        className={`hover:underline ${resetOpenId === t.id ? 'text-slate-400' : 'text-blue-600'}`}
                      >
                        {resetOpenId === t.id ? 'Close' : 'Reset Password'}
                      </button>
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
                {resetOpenId === t.id && (
                  <AdminPasswordPanel
                    key={`reset-${t.id}`}
                    tenantId={t.id}
                    onClose={() => setResetOpenId(null)}
                  />
                )}
              </>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
