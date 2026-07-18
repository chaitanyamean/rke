import { Link } from 'react-router-dom'

const REPORTS = [
  { to: '/reports/ledger',              label: 'Farmer Ledger',         desc: 'Chronological transaction history with running balance for a specific farmer.' },
  { to: '/reports/village-outstandings', label: 'Village Outstandings', desc: 'Outstanding credit balance aggregated by village.' },
  { to: '/reports/item-sales',           label: 'Item Sales',           desc: 'Net quantity and amount sold per item, filterable by category and date.' },
  { to: '/reports/date-sales',           label: 'Sales Summary',        desc: 'Day-by-day sales totals with cash vs credit breakdown and returns.' },
  { to: '/reports/date-payments',        label: 'Daily Payments',       desc: 'Day-by-day payment and receipt totals.' },
]

export default function ReportsLandingPage() {
  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-bold text-slate-800">Reports</h1>
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {REPORTS.map((r) => (
          <Link
            key={r.to}
            to={r.to}
            className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm hover:border-brand hover:shadow-md transition-all"
          >
            <p className="font-semibold text-slate-800">{r.label}</p>
            <p className="mt-1 text-sm text-slate-500">{r.desc}</p>
          </Link>
        ))}
      </div>
    </div>
  )
}
