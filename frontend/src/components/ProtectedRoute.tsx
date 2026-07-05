import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/** Requires an authenticated user; otherwise redirects to the login page. */
export function ProtectedRoute() {
  const { user, loading } = useAuth()

  if (loading) {
    return <div className="p-8 text-slate-500">Loading…</div>
  }
  if (!user) {
    return <Navigate to="/login" replace />
  }
  return <Outlet />
}

/** Requires admin or super_admin; otherwise shows a 403 message. */
export function AdminRoute() {
  const { isAdmin, loading } = useAuth()

  if (loading) {
    return <div className="p-8 text-slate-500">Loading…</div>
  }
  if (!isAdmin) {
    return (
      <div className="mx-auto max-w-lg p-8 text-center">
        <h2 className="text-xl font-semibold text-red-700">Not authorized</h2>
        <p className="mt-2 text-slate-500">
          This section is restricted to administrators.
        </p>
      </div>
    )
  }
  return <Outlet />
}

/** Requires super_admin only; shows 403 for any other role. */
export function SuperAdminRoute() {
  const { isSuperAdmin, loading } = useAuth()

  if (loading) {
    return <div className="p-8 text-slate-500">Loading…</div>
  }
  if (!isSuperAdmin) {
    return (
      <div className="mx-auto max-w-lg p-8 text-center">
        <h2 className="text-xl font-semibold text-red-700">Super admin only</h2>
        <p className="mt-2 text-slate-500">
          This section is restricted to super administrators.
        </p>
      </div>
    )
  }
  return <Outlet />
}
