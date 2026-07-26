import { useState } from 'react'
import { useVillages } from '../../api/villages'
import { useFarmerOutstandings, type DateRangeFilter } from '../../api/reports'
import ReportShell from '../../components/ReportShell'
import { formatBalance } from '../../lib/balance'
import { printReport, esc } from '../../lib/printReport'

type Filter = DateRangeFilter & { villageId?: string }

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

  const grandTotal = data.reduce((s, r) => s + r.outstandingBalance, 0)
  const { label: totalLabel, direction: totalDir } = formatBalance(grandTotal)

  const handlePrint = () => {
    const dateRange = [active?.fromDate, active?.toDate].filter(Boolean).join(' to ')
    const rows = data.map(r => {
      const { label, direction } = formatBalance(r.outstandingBalance)
      const cls = direction === 'owes' ? 'debit' : direction === 'credit' ? 'credit' : 'muted'
      return `<tr>
        <td>${esc(r.farmerName)}</td>
        <td>${esc(r.fatherName)}</td>
        <td>${esc(r.villageName)}</td>
        <td class="right ${cls}">${label}</td>
      </tr>`
    }).join('')
    const { label: totLabel, direction: totDir } = formatBalance(grandTotal)
    const totCls = totDir === 'owes' ? 'debit' : totDir === 'credit' ? 'credit' : 'muted'
    const table = `<table>
      <thead><tr>
        <th>Farmer</th><th>Father Name</th><th>Village</th><th class="right">Outstanding Balance</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td colspan="3" class="right">Total Outstanding</td>
        <td class="right ${totCls}">${totLabel}</td>
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
    <ReportShell title="Village Outstandings" filters={filters} onRun={run}
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
                {['Farmer', 'Father Name', 'Village', 'Outstanding Balance'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => {
                const { label, direction } = formatBalance(row.outstandingBalance)
                return (
                  <tr key={row.farmerId} className="border-b border-slate-100 hover:bg-slate-50">
                    <td className="px-4 py-2.5 font-medium text-slate-800">{row.farmerName}</td>
                    <td className="px-4 py-2.5 text-slate-500">{row.fatherName || '—'}</td>
                    <td className="px-4 py-2.5 text-slate-600">{row.villageName || '—'}</td>
                    <td className={`px-4 py-2.5 text-right font-semibold ${
                      direction === 'owes' ? 'text-red-600' :
                      direction === 'credit' ? 'text-green-600' : 'text-slate-400'
                    }`}>
                      {label}
                    </td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot className="bg-slate-50">
              <tr>
                <td colSpan={3} className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Total Outstanding
                </td>
                <td className={`px-4 py-3 text-right font-bold text-base ${
                  totalDir === 'owes' ? 'text-red-700' :
                  totalDir === 'credit' ? 'text-green-700' : 'text-slate-500'
                }`}>
                  {totalLabel}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
