import { useState } from 'react'
import { useVillages } from '../../api/villages'
import { useVillageOutstandings } from '../../api/reports'
import ReportShell from '../../components/ReportShell'

function fmtCcy(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function VillageOutstandingsPage() {
  const [villageId, setVillageId] = useState('')
  const [activeVillageId, setActiveVillageId] = useState<string | null>(null)
  const [ran, setRan] = useState(true) // auto-run on load with all villages

  const { data: villages = [] } = useVillages()
  const { data = [], isLoading } = useVillageOutstandings(activeVillageId, ran)

  const run = () => {
    setActiveVillageId(villageId || null)
    setRan(true)
  }

  const grandTotal = data.reduce((s, r) => s + r.outstandingBalance, 0)

  const filters = (
    <>
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Village</span>
        <select value={villageId} onChange={(e) => setVillageId(e.target.value)}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">All villages</option>
          {villages.map((v) => (
            <option key={v.id} value={v.id}>{v.name}</option>
          ))}
        </select>
      </label>
    </>
  )

  return (
    <ReportShell title="Village Outstandings" filters={filters} onRun={run}
      isLoading={isLoading} ran={ran}>
      {data.length === 0 ? (
        <p className="p-6 text-center text-sm text-slate-500">No data found.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                {['Village', 'Outstanding Balance (₹)'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.villageId} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2.5 font-medium text-slate-800">{row.villageName}</td>
                  <td className={`px-4 py-2.5 text-right font-semibold ${row.outstandingBalance > 0 ? 'text-red-600' : row.outstandingBalance < 0 ? 'text-green-600' : 'text-slate-500'}`}>
                    {fmtCcy(row.outstandingBalance)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-slate-50">
              <tr>
                <td className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-slate-500">Total</td>
                <td className={`px-4 py-3 text-right font-bold ${grandTotal > 0 ? 'text-red-700' : grandTotal < 0 ? 'text-green-700' : 'text-slate-500'}`}>
                  ₹{fmtCcy(grandTotal)}
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
