import { useState } from 'react'
import { useDatePayments, type DateRangeFilter } from '../../api/reports'
import ReportShell from '../../components/ReportShell'

function fmtCcy(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function DatePaymentsPage() {
  const today = new Date().toISOString().slice(0, 10)
  const [draft, setDraft] = useState<DateRangeFilter>({ fromDate: today, toDate: today, includeVoided: false })
  const [active, setActive] = useState<DateRangeFilter | null>({ fromDate: today, toDate: today, includeVoided: false })

  const { data = [], isLoading } = useDatePayments(active ?? {}, !!active)

  const totPay     = data.reduce((s, r) => s + r.paymentsTotal, 0)
  const totReceipt = data.reduce((s, r) => s + r.receiptsTotal, 0)
  const totDay     = data.reduce((s, r) => s + r.dayTotal, 0)

  const filters = (
    <>
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
    <ReportShell title="Daily Payments & Receipts" filters={filters}
      onRun={() => setActive({ ...draft })} isLoading={isLoading} ran={!!active}>
      {data.length === 0 ? (
        <p className="p-6 text-center text-sm text-slate-500">No data found.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                {['Date', 'Payments (₹)', 'Receipts (₹)', 'Day Total (₹)'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.date} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2.5 text-slate-600">{row.date}</td>
                  <td className="px-4 py-2.5 text-right text-green-700">{fmtCcy(row.paymentsTotal)}</td>
                  <td className="px-4 py-2.5 text-right text-blue-700">{fmtCcy(row.receiptsTotal)}</td>
                  <td className="px-4 py-2.5 text-right font-semibold text-slate-800">{fmtCcy(row.dayTotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-slate-50 font-semibold">
              <tr>
                <td className="px-4 py-3 text-right text-xs uppercase tracking-wide text-slate-500">Total</td>
                <td className="px-4 py-3 text-right text-green-700">₹{fmtCcy(totPay)}</td>
                <td className="px-4 py-3 text-right text-blue-700">₹{fmtCcy(totReceipt)}</td>
                <td className="px-4 py-3 text-right text-slate-800">₹{fmtCcy(totDay)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
