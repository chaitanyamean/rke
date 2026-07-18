import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { Farmer } from '../../types'
import { useFarmerLedger, type DateRangeFilter } from '../../api/reports'
import { useAuth } from '../../auth/AuthContext'
import FarmerSelector from '../../components/FarmerSelector'
import ReportShell from '../../components/ReportShell'
import { formatBalance } from '../../lib/balance'

/** Maps a ledger row's transaction_type token to its admin-only edit route. */
function editPathFor(transactionType: string, transactionId: string): string | null {
  switch (transactionType) {
    case 'cash_sale':
      return `/sales/cash/${transactionId}/edit`
    case 'credit_sale':
      return `/sales/credit/${transactionId}/edit`
    case 'cash_payment':
      return `/payments/payment/${transactionId}/edit`
    case 'cash_receipt':
      return `/payments/receipt/${transactionId}/edit`
    case 'return':
      return `/returns/${transactionId}/edit`
    default:
      return null
  }
}

function fmtType(t: string) {
  return t.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}

function fmtCcy(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function FarmerLedgerPage() {
  const { isAdmin } = useAuth()
  const [farmer, setFarmer] = useState<Farmer | null>(null)
  const [draft, setDraft] = useState<DateRangeFilter>({
    fromDate: `${new Date().getFullYear()}-04-01`,
    toDate: new Date().toISOString().slice(0, 10),
    includeVoided: false,
  })
  const [active, setActive] = useState<(DateRangeFilter & { farmerId: string }) | null>(null)

  const { data = [], isLoading } = useFarmerLedger(
    active?.farmerId ?? null,
    active ?? {},
    !!active,
  )

  const run = () => {
    if (!farmer) return
    setActive({ ...draft, farmerId: farmer.id })
  }

  const filters = (
    <>
      <div className="w-full">
        <span className="mb-1 block text-sm font-medium text-slate-700">Farmer</span>
        <FarmerSelector onChange={setFarmer} />
      </div>

      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">From</span>
        <input type="date" value={draft.fromDate ?? ''} onChange={(e) => setDraft((d) => ({ ...d, fromDate: e.target.value }))}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm" />
      </label>

      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">To</span>
        <input type="date" value={draft.toDate ?? ''} onChange={(e) => setDraft((d) => ({ ...d, toDate: e.target.value }))}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm" />
      </label>

      {/* <label className="flex items-center gap-2 text-sm text-slate-700">
        <input type="checkbox" checked={!!draft.includeVoided}
          onChange={(e) => setDraft((d) => ({ ...d, includeVoided: e.target.checked }))}
          className="h-4 w-4 accent-slate-700" />
        Include voided
      </label> */}
    </>
  )

  return (
    <ReportShell title="Farmer Ledger" filters={filters} onRun={run}
      isLoading={isLoading} ran={!!active}>
      {data.length === 0 ? (
        <p className="p-6 text-center text-sm text-slate-500">No transactions found.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[700px] text-sm">
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                {['Date', 'Bill Number', 'Type', 'Direction', 'Amount (₹)', 'Balance (₹)', 'Interest (₹)']
                  .concat(isAdmin ? [''] : [])
                  .map((h, i) => (
                    <th key={i} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">{h}</th>
                  ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row, i) => {
                const isDebit = row.direction === 'DEBIT'
                const isCredit = row.direction === 'CREDIT'
                const editPath = isAdmin ? editPathFor(row.transactionType, row.transactionId) : null
                return (
                  <tr key={i} className="border-b border-slate-100 hover:bg-slate-50">
                    <td className="px-4 py-2.5 text-slate-600">{row.transactionDate}</td>
                    <td className="px-4 py-2.5 font-mono text-xs text-slate-700">{row.billNumber}</td>
                    <td className="px-4 py-2.5">
                      <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                        isDebit ? 'bg-red-50 text-red-700' :
                        isCredit ? 'bg-green-50 text-green-700' :
                        'bg-slate-100 text-slate-600'
                      }`}>
                        {fmtType(row.transactionType)}
                      </span>
                    </td>
                    <td className="px-4 py-2.5">
                      <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                        row.direction === 'DEBIT'
                          ? 'bg-red-50 text-red-700'
                          : 'bg-green-50 text-green-700'
                      }`}>
                        {row.direction}
                      </span>
                    </td>
                    <td className={`px-4 py-2.5 text-right font-medium ${isDebit ? 'text-red-600' : isCredit ? 'text-green-600' : 'text-slate-700'}`}>
                      {row.direction === 'DEBIT' ? '−' : '+'}₹{fmtCcy(Math.abs(row.signedAmount))}
                    </td>
                    <td className={`px-4 py-2.5 text-right font-semibold ${row.runningBalance > 0 ? 'text-red-700' : row.runningBalance < 0 ? 'text-green-700' : 'text-slate-500'}`}>
                      {fmtCcy(row.runningBalance)}
                    </td>
                    <td className="px-4 py-2.5 text-right text-slate-400">{fmtCcy(row.interestAmount)}</td>
                    {isAdmin && (
                      <td className="px-4 py-2.5 text-right">
                        {editPath && (
                          <Link to={editPath} className="text-xs font-medium text-blue-600 hover:underline">
                            Edit
                          </Link>
                        )}
                      </td>
                    )}
                  </tr>
                )
              })}
            </tbody>
            <tfoot className="bg-slate-50 font-semibold">
              <tr>
                <td colSpan={5} className="px-4 py-3 text-right text-xs uppercase tracking-wide text-slate-500">Closing Balance</td>
                {(() => {
                  const { label, direction: dir } = formatBalance(data[data.length - 1].runningBalance)
                  return (
                    <td className={`px-4 py-3 text-right ${dir === 'owes' ? 'text-red-700' : dir === 'credit' ? 'text-green-700' : 'text-slate-500'}`}>
                      {label}
                    </td>
                  )
                })()}
                <td />
                {isAdmin && <td />}
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
