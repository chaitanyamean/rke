import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

function navClass({ isActive }: { isActive: boolean }): string {
  return [
    'px-3 py-2 rounded-md text-sm font-medium transition-colors',
    isActive
      ? 'bg-white/20 text-white'
      : 'text-white/75 hover:bg-white/10 hover:text-white',
  ].join(' ')
}

export default function Layout() {
  const { user, tenant, isAdmin, isSuperAdmin, hasFeature, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  const tenantName = tenant?.name ?? ''
  const logoUrl = tenant?.logoUrl ?? null

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Header — background driven by CSS variable set at runtime from tenant branding */}
      <header
        className="border-b border-black/10"
        style={{ backgroundColor: 'var(--color-brand, #1e293b)' }}
      >
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-2 px-4 py-3">
          {/* Logo / tenant name */}
          <div className="mr-4 flex items-center gap-2">
            {logoUrl ? (
              <img
                src={logoUrl}
                alt={tenantName}
                className="h-7 w-auto rounded object-contain"
              />
            ) : null}
            <span className="text-lg font-bold text-white">{tenantName}</span>
          </div>

          {/* Navigation */}
          <nav className="flex flex-wrap items-center gap-1">
            <NavLink to="/" end className={navClass}>
              Dashboard
            </NavLink>
            <NavLink to="/farmers" className={navClass}>
              Farmers
            </NavLink>
            <NavLink to="/items" className={navClass}>
              Items
            </NavLink>
            <NavLink to="/sales/cash" className={navClass}>
              Cash Sale
            </NavLink>
            <NavLink to="/sales/credit" className={navClass}>
              Credit Sale
            </NavLink>
            <NavLink to="/payments/payment" className={navClass}>
              Payment
            </NavLink>
            <NavLink to="/payments/receipt" className={navClass}>
              Receipt
            </NavLink>
            <NavLink to="/returns" className={navClass}>
              Returns
            </NavLink>
            <NavLink to="/reports" className={navClass}>
              Reports
            </NavLink>

            {/* Feature-gated: Cotton Procurement */}
            {hasFeature('cotton_procurement') && (
              <NavLink to="/cotton" className={navClass}>
                Cotton
              </NavLink>
            )}

            {/* Admin-only: master data */}
            {isAdmin && (
              <>
                <NavLink to="/villages" className={navClass}>
                  Villages
                </NavLink>
                <NavLink to="/item-categories" className={navClass}>
                  Categories
                </NavLink>
                <NavLink to="/bill-number-types" className={navClass}>
                  Bill Types
                </NavLink>
              </>
            )}

            {/* Super admin only: tenant management */}
            {isSuperAdmin && (
              <NavLink to="/admin/tenants" className={navClass}>
                ⚙ Tenants
              </NavLink>
            )}
          </nav>

          {/* User info + logout */}
          <div className="ml-auto flex items-center gap-3 text-sm">
            <span className="text-white/80">
              {user?.fullName || user?.username}
              <span className="ml-1 rounded bg-white/20 px-1.5 py-0.5 text-xs uppercase text-white">
                {user?.role}
              </span>
            </span>
            <button
              onClick={handleLogout}
              className="rounded-md border border-white/30 px-3 py-1.5 text-white/80 hover:bg-white/10 hover:text-white"
            >
              Logout
            </button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
