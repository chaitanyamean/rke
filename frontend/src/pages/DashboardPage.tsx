import { Link } from 'react-router-dom'
import {
  useDashboardSummary,
  useRecentTransactions,
  type RecentTransaction,
} from '../api/dashboard'

const inr = new Intl.NumberFormat('en-IN', {
  style: 'currency',
  currency: 'INR',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
})

function formatMoney(value: number | undefined): string {
  return inr.format(value ?? 0)
}

function formatDate(iso: string | undefined): string {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleDateString('en-GB') // dd/mm/yyyy
}

// Presentation for each transaction type.
const TYPE_META: Record<string, { label: string; badge: string; mode: string }> = {
  cash_sale: { label: 'Cash Sale', badge: 'bg-green-100 text-green-700', mode: 'CASH' },
  credit_sale: { label: 'Credit Sale', badge: 'bg-amber-100 text-amber-700', mode: 'CREDIT' },
  cash_receipt: { label: 'Received', badge: 'bg-blue-100 text-blue-700', mode: 'CASH' },
  cash_payment: { label: 'Payment', badge: 'bg-purple-100 text-purple-700', mode: 'CASH' },
  return: { label: 'Return', badge: 'bg-rose-100 text-rose-700', mode: 'CASH' },
}

function typeMeta(type: string) {
  return TYPE_META[type] ?? { label: type, badge: 'bg-slate-100 text-slate-600', mode: '—' }
}

function KpiCard({
  label,
  value,
  accent,
  hint,
}: {
  label: string
  value: string
  accent?: boolean
  hint?: string
}) {
  return (
    <div className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
      <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-2 text-2xl font-bold ${accent ? 'text-red-600' : 'text-slate-800'}`}>
        {value}
      </p>
      {hint && <p className="mt-2 text-xs text-slate-400">{hint}</p>}
    </div>
  )
}

export default function DashboardPage() {
  const { data: summary, isLoading } = useDashboardSummary()
  const { data: recent = [], isLoading: recentLoading } = useRecentTransactions(8)

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h1 className="text-2xl font-bold text-slate-800">Dashboard</h1>
          <p className="text-sm text-slate-500">
            Overview of today's sales performance and cash flow.
          </p>
        </div>
        <span className="rounded-md bg-white px-3 py-1.5 text-sm font-medium text-slate-600 shadow-sm">
          📅 {formatDate(summary?.date)}
        </span>
      </div>

      {/* KPI cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <KpiCard label="Today's Cash Sales" value={formatMoney(summary?.todayCashSales)} />
        <KpiCard label="Today's Credit Sales" value={formatMoney(summary?.todayCreditSales)} />
        <KpiCard label="Today's Cash Received" value={formatMoney(summary?.todayCashReceived)} />
        <KpiCard
          label="Outstanding Amount"
          value={formatMoney(summary?.totalOutstanding)}
          accent
        />
      </div>

      {/* Main grid: recent transactions + summaries */}
      <div className="grid gap-6 lg:grid-cols-3">
        {/* Recent transactions */}
        <section className="lg:col-span-2">
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-lg font-semibold text-slate-800">Recent Transactions</h2>
            <Link to="/reports" className="text-sm font-medium text-blue-600 hover:underline">
              View All Transactions
            </Link>
          </div>
          <div className="overflow-x-auto rounded-lg border border-slate-200 bg-white shadow-sm">
            <table className="w-full text-left text-sm">
              <thead className="bg-slate-50 text-xs uppercase tracking-wide text-slate-500">
                <tr>
                  <th className="px-4 py-3 font-medium">Date</th>
                  <th className="px-4 py-3 font-medium">Type</th>
                  <th className="px-4 py-3 font-medium">Ref No.</th>
                  <th className="px-4 py-3 font-medium">Customer</th>
                  <th className="px-4 py-3 font-medium">Amount</th>
                  <th className="px-4 py-3 font-medium">Payment</th>
                </tr>
              </thead>
              <tbody>
                {recentLoading && (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-slate-400">Loading…</td>
                  </tr>
                )}
                {!recentLoading && recent.length === 0 && (
                  <tr>
                    <td colSpan={6} className="px-4 py-6 text-center text-slate-400">
                      No transactions yet
                    </td>
                  </tr>
                )}
                {recent.map((tx: RecentTransaction, i) => {
                  const meta = typeMeta(tx.type)
                  return (
                    <tr key={`${tx.billNumber}-${i}`} className="border-t border-slate-100">
                      <td className="px-4 py-3 text-slate-600">{formatDate(tx.date)}</td>
                      <td className="px-4 py-3">
                        <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${meta.badge}`}>
                          {meta.label}
                        </span>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-slate-600">{tx.billNumber}</td>
                      <td className="px-4 py-3 font-medium text-slate-800">{tx.farmerName}</td>
                      <td className="px-4 py-3 text-slate-700">{formatMoney(tx.amount)}</td>
                      <td className="px-4 py-3 text-xs font-medium text-slate-500">{meta.mode}</td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </section>

        {/* Right column: summaries */}
        <div className="space-y-6">
          {/* Sales summary */}
          <section className="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
            <h2 className="mb-4 flex items-center gap-2 font-semibold text-slate-800">
              📊 Sales Summary (Today)
            </h2>
            <dl className="space-y-2 text-sm">
              <div className="flex justify-between">
                <dt className="text-slate-500">Cash Sales</dt>
                <dd className="font-medium text-slate-800">{formatMoney(summary?.todayCashSales)}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-slate-500">Credit Sales</dt>
                <dd className="font-medium text-slate-800">{formatMoney(summary?.todayCreditSales)}</dd>
              </div>
              <div className="mt-2 flex justify-between border-t border-slate-100 pt-3">
                <dt className="font-semibold text-slate-700">Total Sales</dt>
                <dd className="font-bold text-blue-700">{formatMoney(summary?.todayTotalSales)}</dd>
              </div>
            </dl>
          </section>

          {/* Outstanding summary */}
          <section className="rounded-lg border border-rose-100 bg-rose-50/50 p-5 shadow-sm">
            <h2 className="mb-4 flex items-center gap-2 font-semibold text-slate-800">
              📉 Outstanding Summary
            </h2>
            <dl className="space-y-2 text-sm">
              <div className="flex items-center justify-between">
                <dt className="text-slate-500">Customers with Outstanding</dt>
                <dd className="rounded bg-slate-200 px-2 py-0.5 text-xs font-semibold text-slate-700">
                  {summary?.customersWithOutstanding ?? 0}
                </dd>
              </div>
              <div className="flex items-center justify-between">
                <dt className="text-slate-500">Total Customers</dt>
                <dd className="rounded bg-slate-200 px-2 py-0.5 text-xs font-semibold text-slate-700">
                  {summary?.totalCustomers ?? 0}
                </dd>
              </div>
              <div className="mt-2 flex justify-between border-t border-rose-100 pt-3">
                <dt className="font-semibold text-slate-700">Total Outstanding</dt>
                <dd className="font-bold text-red-600">{formatMoney(summary?.totalOutstanding)}</dd>
              </div>
            </dl>
            <Link
              to="/reports"
              className="mt-4 block rounded-md border border-red-300 py-2 text-center text-sm font-semibold uppercase tracking-wide text-red-600 hover:bg-red-50"
            >
              View Aging Report
            </Link>
          </section>
        </div>
      </div>

      {isLoading && <p className="text-sm text-slate-400">Loading summary…</p>}
    </div>
  )
}
