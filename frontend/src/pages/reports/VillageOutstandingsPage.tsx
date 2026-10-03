import { useState } from 'react'
import { useVillages } from '../../api/villages'
import { useFarmerOutstandings, type DateRangeFilter } from '../../api/reports'
import ReportShell from '../../components/ReportShell'
import { printReport, esc } from '../../lib/printReport'

type Filter = DateRangeFilter & { villageId?: string }

function fmt(n: number) {
  return '₹' + Math.abs(n).toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
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

  const totalDebits   = data.reduce((s, r) => s + r.totalDebits, 0)
  const totalCredits  = data.reduce((s, r) => s + r.totalCredits, 0)
  const totalInterest = data.reduce((s, r) => s + r.totalInterest, 0)

  // Farmer owes = rows where balance < 0 (absolute value)
  // Firm owes   = rows where balance > 0
  const totalFarmerOwes = data.reduce((s, r) => r.outstandingBalance < 0 ? s + Math.abs(r.outstandingBalance) : s, 0)
  const totalFirmOwes   = data.reduce((s, r) => r.outstandingBalance > 0 ? s + r.outstandingBalance : s, 0)

  const handlePrint = () => {
    const dateRange = [active?.fromDate, active?.toDate].filter(Boolean).join(' to ')
    const rows = data.map(r => {
      const bal = r.outstandingBalance
      const firmOwes = bal > 0 ? fmt(bal) : '—'
      return `<tr>
        <td>${esc(r.farmerName)}</td>
        <td>${esc(r.fatherName ?? '—')}</td>
        <td>${esc(r.villageName ?? '—')}</td>
        <td class="right">${fmt(r.totalDebits)}</td>
        <td class="right">${fmt(r.totalCredits)}</td>
        <td class="right">${fmt(r.totalInterest)}</td>
        <td class="right debit">${bal < 0 ? '-' + fmt(Math.abs(bal)) : '—'}</td>
        <td class="right credit">${firmOwes}</td>
      </tr>`
    }).join('')

    const table = `<table>
      <thead><tr>
        <th>Farmer</th><th>Father Name</th><th>Village</th>
        <th class="right">Debit</th><th class="right">Credit</th><th class="right">Interest</th>
        <th class="right">Farmer Owes</th><th class="right">Firm Owes</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td colspan="3" class="right">Total</td>
        <td class="right">${fmt(totalDebits)}</td>
        <td class="right">${fmt(totalCredits)}</td>
        <td class="right">${fmt(totalInterest)}</td>
        <td class="right debit">-${fmt(totalFarmerOwes)}</td>
        <td class="right credit">${fmt(totalFirmOwes)}</td>
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
                {[
                  { label: 'Farmer',       right: false },
                  { label: 'Father Name',  right: false },
                  { label: 'Village',      right: false },
                  { label: 'Debit',        right: true  },
                  { label: 'Credit',       right: true  },
                  { label: 'Interest',     right: true  },
                  { label: 'Farmer Owes',  right: true  },
                  { label: 'Firm Owes',    right: true  },
                ].map(({ label, right }) => (
                  <th key={label} className={`px-4 py-3 text-xs font-semibold uppercase tracking-wide text-slate-500 ${right ? 'text-right' : 'text-left'}`}>
                    {label}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => {
                const bal = row.outstandingBalance
                return (
                  <tr key={row.farmerId} className="border-b border-slate-100 hover:bg-slate-50">
                    <td className="px-4 py-2.5 font-medium text-slate-800">{row.farmerName}</td>
                    <td className="px-4 py-2.5 text-slate-500">{row.fatherName || '—'}</td>
                    <td className="px-4 py-2.5 text-slate-600">{row.villageName || '—'}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.totalDebits)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.totalCredits)}</td>
                    <td className="px-4 py-2.5 text-right text-slate-700">{fmt(row.totalInterest)}</td>
                    {/* Farmer Owes — red, shown only when balance is negative */}
                    <td className={`px-4 py-2.5 text-right font-semibold ${bal < 0 ? 'text-red-600' : 'text-slate-300'}`}>
                      {bal < 0 ? '-' + fmt(Math.abs(bal)) : '—'}
                    </td>
                    {/* Firm Owes — green, shown only when balance is positive */}
                    <td className={`px-4 py-2.5 text-right font-semibold ${bal > 0 ? 'text-green-600' : 'text-slate-300'}`}>
                      {bal > 0 ? fmt(bal) : '—'}
                    </td>
                  </tr>
                )
              })}
            </tbody>
            <tfoot className="bg-slate-50 border-t-2 border-slate-300">
              <tr>
                <td colSpan={3} className="px-4 py-3 text-right text-xs font-semibold uppercase tracking-wide text-slate-500">
                  Total
                </td>
                <td className="px-4 py-3 text-right font-bold text-slate-700">{fmt(totalDebits)}</td>
                <td className="px-4 py-3 text-right font-bold text-slate-700">{fmt(totalCredits)}</td>
                <td className="px-4 py-3 text-right font-bold text-slate-700">{fmt(totalInterest)}</td>
                <td className="px-4 py-3 text-right font-bold text-base text-red-700">-{fmt(totalFarmerOwes)}</td>
                <td className="px-4 py-3 text-right font-bold text-base text-green-700">{fmt(totalFirmOwes)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
