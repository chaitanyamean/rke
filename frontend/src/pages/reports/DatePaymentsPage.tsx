import { useState } from 'react'
import { useDatePayments, type DateRangeFilter } from '../../api/reports'
import ReportShell from '../../components/ReportShell'
import { printReport } from '../../lib/printReport'

function fmtCcy(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function DatePaymentsPage() {
  const today = new Date().toISOString().slice(0, 10)
  const [draft, setDraft] = useState<DateRangeFilter>({ fromDate: '2026-04-01', toDate: today, includeVoided: false })
  const [active, setActive] = useState<DateRangeFilter | null>({ fromDate: '2026-04-01', toDate: today, includeVoided: false })

  const { data = [], isLoading } = useDatePayments(active ?? {}, !!active)

  const totPay     = data.reduce((s, r) => s + r.paymentsTotal, 0)
  const totReceipt = data.reduce((s, r) => s + r.receiptsTotal, 0)
  const totDay     = data.reduce((s, r) => s + r.dayTotal, 0)

  const handlePrint = () => {
    const dateRange = [active?.fromDate, active?.toDate].filter(Boolean).join(' to ')
    const rows = data.map(r => `<tr>
      <td>${r.date}</td>
      <td class="right debit">${r.paymentsTotal > 0 ? `-₹${fmtCcy(r.paymentsTotal)}` : '—'}</td>
      <td class="right credit">${r.receiptsTotal > 0 ? `+₹${fmtCcy(r.receiptsTotal)}` : '—'}</td>
      <td class="right">${r.dayTotal >= 0 ? '' : '-'}₹${fmtCcy(Math.abs(r.dayTotal))}</td>
    </tr>`).join('')
    const table = `<table>
      <thead><tr>
        <th>Date</th><th class="right">Payments (₹)</th>
        <th class="right">Receipts (₹)</th><th class="right">Day Total (₹)</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td>Total</td>
        <td class="right debit">${totPay > 0 ? `-₹${fmtCcy(totPay)}` : '—'}</td>
        <td class="right credit">${totReceipt > 0 ? `+₹${fmtCcy(totReceipt)}` : '—'}</td>
        <td class="right">${totDay >= 0 ? '' : '-'}₹${fmtCcy(Math.abs(totDay))}</td>
      </tr></tfoot>
    </table>`
    printReport('Payments & Receipts', `Period: ${dateRange || 'All dates'}`, table)
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
    <ReportShell title="Payments & Receipts" filters={filters}
      onRun={() => setActive({ ...draft })} isLoading={isLoading} ran={!!active}
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
                {[['Date', 'text-left'], ['Payments (₹)', 'text-right'], ['Receipts (₹)', 'text-right'], ['Day Total (₹)', 'text-right']].map(([h, align]) => (
                  <th key={h} className={`px-4 py-3 ${align} text-xs font-semibold uppercase tracking-wide text-slate-500`}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.date} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2.5 text-slate-600">{row.date}</td>
                  <td className="px-4 py-2.5 text-right text-red-600">
                    {row.paymentsTotal > 0 ? `-₹${fmtCcy(row.paymentsTotal)}` : '—'}
                  </td>
                  <td className="px-4 py-2.5 text-right text-green-700">
                    {row.receiptsTotal > 0 ? `+₹${fmtCcy(row.receiptsTotal)}` : '—'}
                  </td>
                  <td className={`px-4 py-2.5 text-right font-semibold ${row.dayTotal >= 0 ? 'text-slate-800' : 'text-red-700'}`}>
                    {row.dayTotal >= 0 ? '' : '-'}₹{fmtCcy(Math.abs(row.dayTotal))}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-slate-50 font-semibold">
              <tr>
                <td className="px-4 py-3 text-left text-xs uppercase tracking-wide text-slate-500">Total</td>
                <td className="px-4 py-3 text-right text-red-600">
                  {totPay > 0 ? `-₹${fmtCcy(totPay)}` : '—'}
                </td>
                <td className="px-4 py-3 text-right text-green-700">
                  {totReceipt > 0 ? `+₹${fmtCcy(totReceipt)}` : '—'}
                </td>
                <td className={`px-4 py-3 text-right ${totDay >= 0 ? 'text-slate-800' : 'text-red-700'}`}>
                  {totDay >= 0 ? '' : '-'}₹{fmtCcy(Math.abs(totDay))}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
