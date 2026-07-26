import { useState } from 'react'
import { useDateSales, type DateRangeFilter } from '../../api/reports'
import ReportShell from '../../components/ReportShell'
import { printReport } from '../../lib/printReport'

function fmtCcy(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function DateSalesPage() {
  const today = new Date().toISOString().slice(0, 10)
  const [draft, setDraft] = useState<DateRangeFilter>({ fromDate: '2026-04-01', toDate: today, includeVoided: false })
  const [active, setActive] = useState<DateRangeFilter | null>({ fromDate: '2026-04-01', toDate: today, includeVoided: false })

  const { data = [], isLoading } = useDateSales(active ?? {}, !!active)

  const totCash    = data.reduce((s, r) => s + r.cashSalesTotal, 0)
  const totCredit  = data.reduce((s, r) => s + r.creditSalesTotal, 0)
  const totReturns = data.reduce((s, r) => s + r.returnsTotal, 0)
  const totDay     = data.reduce((s, r) => s + r.dayTotal, 0)

  const handlePrint = () => {
    const dateRange = [active?.fromDate, active?.toDate].filter(Boolean).join(' to ')
    const rows = data.map(r => `<tr>
      <td>${r.date}</td>
      <td class="right">${fmtCcy(r.cashSalesTotal)}</td>
      <td class="right">${fmtCcy(r.creditSalesTotal)}</td>
      <td class="right">${r.returnsTotal > 0 ? `-${fmtCcy(r.returnsTotal)}` : '—'}</td>
      <td class="right">${fmtCcy(r.dayTotal)}</td>
    </tr>`).join('')
    const table = `<table>
      <thead><tr>
        <th>Date</th><th class="right">Cash Sales (₹)</th>
        <th class="right">Credit Sales (₹)</th><th class="right">Returns (₹)</th>
        <th class="right">Day Total (₹)</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td class="right">Total</td>
        <td class="right">₹${fmtCcy(totCash)}</td>
        <td class="right">₹${fmtCcy(totCredit)}</td>
        <td class="right">${totReturns > 0 ? `-₹${fmtCcy(totReturns)}` : '—'}</td>
        <td class="right">₹${fmtCcy(totDay)}</td>
      </tr></tfoot>
    </table>`
    printReport('Sales Summary', `Period: ${dateRange || 'All dates'}`, table, `Sales Summary${dateRange ? ' - ' + dateRange : ''}`)
  }

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
    <ReportShell title="Sales Summary" filters={filters} onRun={() => setActive({ ...draft })}
      isLoading={isLoading} ran={!!active}
      actions={data.length > 0 ? (
        <button onClick={handlePrint}
          className="rounded-md border border-slate-300 px-5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 flex items-center gap-1.5">
          ⬇ Download PDF
        </button>
      ) : undefined}
    >
      {data.length === 0 ? (
        <p className="p-6 text-center text-sm text-slate-500">No data found.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                {['Date', 'Cash Sales (₹)', 'Credit Sales (₹)', 'Returns (₹)', 'Day Total (₹)'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.date} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2.5 text-slate-600">{row.date}</td>
                  <td className="px-4 py-2.5 text-right text-slate-700">{fmtCcy(row.cashSalesTotal)}</td>
                  <td className="px-4 py-2.5 text-right text-slate-700">{fmtCcy(row.creditSalesTotal)}</td>
                  <td className="px-4 py-2.5 text-right text-red-600">{row.returnsTotal > 0 ? `-${fmtCcy(row.returnsTotal)}` : '—'}</td>
                  <td className="px-4 py-2.5 text-right font-semibold text-slate-800">{fmtCcy(row.dayTotal)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-slate-50 font-semibold">
              <tr>
                <td className="px-4 py-3 text-right text-xs uppercase tracking-wide text-slate-500">Total</td>
                <td className="px-4 py-3 text-right text-slate-700">₹{fmtCcy(totCash)}</td>
                <td className="px-4 py-3 text-right text-slate-700">₹{fmtCcy(totCredit)}</td>
                <td className="px-4 py-3 text-right text-red-600">{totReturns > 0 ? `-₹${fmtCcy(totReturns)}` : '—'}</td>
                <td className="px-4 py-3 text-right text-slate-800">₹{fmtCcy(totDay)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
