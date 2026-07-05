import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import HealthStatus from '../components/HealthStatus'

function Card({ to, title, desc }: { to: string; title: string; desc: string }) {
  return (
    <Link
      to={to}
      className="block rounded-lg border border-slate-200 bg-white p-5 shadow-sm transition hover:border-slate-300 hover:shadow"
    >
      <h3 className="font-semibold text-slate-800">{title}</h3>
      <p className="mt-1 text-sm text-slate-500">{desc}</p>
    </Link>
  )
}

export default function DashboardPage() {
  const { user, isAdmin } = useAuth()

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">
          Welcome, {user?.fullName || user?.username}
        </h1>
        <p className="text-slate-500">Master data and farmer management.</p>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <Card to="/farmers" title="Farmers" desc="Register and search farmers" />
        <Card to="/items" title="Items" desc="Item catalog grouped by category" />
        {isAdmin && (
          <>
            <Card to="/villages" title="Villages" desc="Manage villages master data" />
            <Card to="/item-categories" title="Item Categories" desc="Manage categories" />
            <Card to="/bill-number-types" title="Bill Number Types" desc="Manage bill types" />
          </>
        )}
      </div>

      <HealthStatus />
    </div>
  )
}
