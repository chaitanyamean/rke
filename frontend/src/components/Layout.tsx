import { useState, type ReactNode } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

const SIDEBAR_WIDTH = 'w-64'

function linkClass({ isActive }: { isActive: boolean }): string {
  return [
    'block rounded-md px-3 py-2 text-sm font-medium transition-colors',
    isActive
      ? 'bg-white/20 text-white'
      : 'text-white/75 hover:bg-white/10 hover:text-white',
  ].join(' ')
}

export default function Layout() {
  const { user, tenant, isAdmin, isSuperAdmin, hasFeature, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  const close = () => setOpen(false)

  const handleLogout = async () => {
    close()
    await logout()
    navigate('/login', { replace: true })
  }

  const tenantName = tenant?.name ?? ''
  const logoUrl = tenant?.logoUrl ?? null

  // Closes the mobile drawer whenever a link is tapped.
  const Item = ({ to, label, end }: { to: string; label: ReactNode; end?: boolean }) => (
    <NavLink to={to} end={end} className={linkClass} onClick={close}>
      {label}
    </NavLink>
  )

  return (
    <div className="min-h-screen bg-slate-50">
      {/* Mobile top bar with hamburger (hidden on md+) */}
      <div
        className="sticky top-0 z-20 flex items-center gap-3 border-b border-black/10 px-4 py-3 md:hidden"
        style={{ backgroundColor: 'var(--color-brand, #1e293b)' }}
      >
        <button
          type="button"
          onClick={() => setOpen(true)}
          aria-label="Open navigation menu"
          className="rounded-md p-1 text-white hover:bg-white/10"
        >
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
            <line x1="3" y1="6" x2="21" y2="6" />
            <line x1="3" y1="12" x2="21" y2="12" />
            <line x1="3" y1="18" x2="21" y2="18" />
          </svg>
        </button>
        <span className="text-lg font-bold text-white">{tenantName}</span>
      </div>

      {/* Backdrop (mobile only, when drawer open) */}
      {open && (
        <div
          className="fixed inset-0 z-30 bg-black/50 md:hidden"
          onClick={close}
          aria-hidden="true"
        />
      )}

      {/* Sidebar: fixed drawer on mobile, always-visible column on md+ */}
      <aside
        className={[
          'fixed inset-y-0 left-0 z-40 flex flex-col border-r border-black/10',
          SIDEBAR_WIDTH,
          'transform transition-transform duration-200 ease-in-out md:translate-x-0',
          open ? 'translate-x-0' : '-translate-x-full',
        ].join(' ')}
        style={{ backgroundColor: 'var(--color-brand, #1e293b)' }}
      >
        {/* Brand + close (close only on mobile) */}
        <div className="flex items-center gap-2 border-b border-white/10 px-4 py-4">
          {logoUrl && (
            <img src={logoUrl} alt={tenantName} className="h-7 w-auto rounded object-contain" />
          )}
          <span className="text-lg font-bold text-white">{tenantName}</span>
          <button
            type="button"
            onClick={close}
            aria-label="Close navigation menu"
            className="ml-auto rounded-md p-1 text-white hover:bg-white/10 md:hidden"
          >
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round">
              <line x1="6" y1="6" x2="18" y2="18" />
              <line x1="6" y1="18" x2="18" y2="6" />
            </svg>
          </button>
        </div>

        {/* Scrollable nav */}
        <nav className="flex-1 space-y-1 overflow-y-auto px-3 py-4">
          <Item to="/" label="Dashboard" end />
          <Item to="/farmers" label="Farmers" />
          <Item to="/items" label="Items" />
          <Item to="/sales/cash" label="Cash Sale" />
          <Item to="/sales/credit" label="Credit Sale" />
          <Item to="/payments/payment" label="Payment" />
          <Item to="/payments/receipt" label="Receipt" />
          <Item to="/returns" label="Returns" />
          {hasFeature('cotton_procurement') && <Item to="/cotton" label="Cotton" />}
          <Item to="/reports" label="Reports" />

          {isAdmin && (
            <>
              <p className="px-3 pb-1 pt-4 text-xs font-semibold uppercase tracking-wide text-white/40">
                Master Data
              </p>
              <Item to="/villages" label="Villages" />
              <Item to="/item-categories" label="Categories" />
              <Item to="/bill-number-types" label="Bill Types" />

              <p className="px-3 pb-1 pt-4 text-xs font-semibold uppercase tracking-wide text-white/40">
                Team
              </p>
              <Item to="/staff-users" label="Staff Users" />
            </>
          )}

          {isSuperAdmin && (
            <>
              <p className="px-3 pb-1 pt-4 text-xs font-semibold uppercase tracking-wide text-white/40">
                Platform
              </p>
              <Item to="/admin/tenants" label="⚙ Tenants" />
            </>
          )}
        </nav>

        {/* User info + logout */}
        <div className="border-t border-white/10 px-4 py-3">
          <div className="mb-2 truncate text-sm text-white/80">
            {user?.fullName || user?.username}
            <span className="ml-1 rounded bg-white/20 px-1.5 py-0.5 text-xs uppercase text-white">
              {user?.role}
            </span>
          </div>
          <button
            onClick={handleLogout}
            className="w-full rounded-md border border-white/30 px-3 py-1.5 text-sm text-white/80 hover:bg-white/10 hover:text-white"
          >
            Logout
          </button>
        </div>
      </aside>

      {/* Main content — offset by the sidebar width on md+ */}
      <div className="md:pl-64">
        <main className="mx-auto px-4 py-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
