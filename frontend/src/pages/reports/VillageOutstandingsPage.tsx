import { useState } from 'react'
import { useVillages } from '../../api/villages'
import { useFarmerOutstandings, type DateRangeFilter } from '../../api/reports'
import ReportShell from '../../components/ReportShell'
import { printReport, esc } from '../../lib/printReport'

type Filter = DateRangeFilter & { villageId?: string }

function fmt(n: number) {
  return '₹' + Math.abs(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtSigned(n: number) {
  const sign = n < 0 ? '-' : n > 0 ? '+' : ''
  return sign + '₹' + Math.abs(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function VillageOutstandingsPage() {
  const today = new Date().toISOString().slice(0, 10)
  const [draft, setDraft] = useState<Filter>({
    fromDate: '2026-04-01',
    toDate: today,
    villageId: '',
  })
  const [active, setActive] = useState<Filter>({
    fromDate: '2026-04-01',
    toDate: today,
    villageId: '',
  })

  const { data: villages = [] } = useVillages()
  const { data = [], isLoading } = useFarmerOutstandings(active, true)

  const run = () => setActive({ ...draft })

  const totalDebits    = data.reduce((s, r) => s + r.totalDebits, 0)
  const totalCredits   = data.reduce((s, r) => s + r.totalCredits, 0)
  const totalInterest  = data.reduce((s, r) => s + r.totalInterest, 0)
  const grandBalance   = data.reduce((s, r) => s + r.outstandingBalance, 0)

  const handlePrint = () => {
    const dateRange = [active?.fromDate, active?.toDate].filter(Boolean).join(' to ')
    const rows = data.map(r => {
      const bal = r.outstandingBalance
      const balCls = bal > 0 ? 'credit' : bal < 0 ? 'debit' : 'muted'
      const remarks = bal > 0 ? 'Firm owes' : bal < 0 ? 'Farmer owes' : '—'
      return `<tr>
        <td>${esc(r.farmerName)}</td>
        <td>${esc(r.fatherName ?? '—')}</td>
        <td>${esc(r.villageName ?? '—')}</td>
        <td class="right">${fmt(r.totalDebits)}</td>
        <td class="right">${fmt(r.totalCredits)}</td>
        <td class="right">${fmt(r.totalInterest)}</td>
        <td class="right ${balCls}">${fmtSigned(bal)}</td>
        <td>${remarks}</td>
      </tr>`
    }).join('')

    const totBalCls = grandBalance > 0 ? 'credit' : grandBalance < 0 ? 'debit' : 'muted'
    const table = `<table>
      <thead><tr>
        <th>Farmer</th><th>Father Name</th><th>Village</th>
        <th class="right">Debit</th><th class="right">Credit</th><th class="right">Interest</th>
        <th class="right">Outstanding Balance</th><th>Remarks</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td colspan="3" class="right">Total</td>
        <td class="right">${fmt(totalDebits)}</td>
        <td class="right">${fmt(totalCredits)}</td>
        <td class="right">${fmt(totalInterest)}</td>
        <td class="right ${totBalCls}">${fmtSigned(grandBalance)}</td>
        <td></td>
      </tr></tfoot>
    </table>`
    printReport('Village Outstandings', `Period: ${dateRange || 'All dates'}`, table, `Village Outstandings${dateRange ? ' - ' + dateRange : ''}`)
  }

  const filters = (
    <>
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Village</span>
        <select value={draft.villageId ?? ''} onChange={(e) => setDraft((d) => ({ ...d, villageId: e.target.value }))}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">All villages</option>
          {villages.map((v) => (
            <option key={v.id} value={v.id}>{v.name}</option>
          ))}
        </select>
      </label>

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
    </>
  )

  return (
    <ReportShell title="Village Outstanding" filters={filters} onRun={run}
      isLoading={isLoading} ran={true}
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
                {['Farmer', 'Father Name', 'Village', 'Debit', 'Credit', 'Interest', 'Outstanding Balance', 'Remarks'].map((h) => (
                  <th key={h} className={`px-4 py-3 text-xs font-semibold uppercase tracking-wide text-slate-500 ${
                    ['Debit', 'Credit', 'Interest', 'Outstanding Balance'].includes(h) ? 'text-right' : 'text-left'
                  }`}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => {
                const bal = row.outstandingBalance
                const remarks = bal > 0 ? 'Firm owes' : bal < 0 ? 'Farmer owes' : '—'
                return (
                  <tr key={row.farmerId} className="border-b border-slate-100 hover:bg-slate-50">
                    <td className="px-4 py-2.5 font-medium text-slate-800">{row.farmerName}</td>
                    <td className="px-4 py-2.5 text-slate-500">{row.fatherName || '—'}</td>
                    <td className="px-4 py-2.5 text-slate-600">{row.villageName || '—'}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.totalDebits)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.totalCredits)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.totalInterest)}</td>
                    <td className={`px-4 py-2.5 text-right font-semibold ${
                      bal > 0 ? 'text-green-600' : bal < 0 ? 'text-red-600' : 'text-slate-400'
                    }`}>
                      {fmtSigned(bal)}
                    </td>
                    <td className={`px-4 py-2.5 text-sm ${
                      bal > 0 ? 'text-green-700' : bal < 0 ? 'text-red-700' : 'text-slate-400'
                    }`}>
                      {remarks}
                    </td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot className="bg-slate-50 border-t border-slate-200">
              <tr>
                <td colSpan={3} className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Total
                </td>
                <td className="px-4 py-3 text-right font-bold text-slate-700">{fmt(totalDebits)}</td>
                <td className="px-4 py-3 text-right font-bold text-slate-700">{fmt(totalCredits)}</td>
                <td className="px-4 py-3 text-right font-bold text-slate-700">{fmt(totalInterest)}</td>
                <td className={`px-4 py-3 text-right font-bold text-base ${
                  grandBalance > 0 ? 'text-green-700' : grandBalance < 0 ? 'text-red-700' : 'text-slate-500'
                }`}>
                  {fmtSigned(grandBalance)}
                </td>
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
