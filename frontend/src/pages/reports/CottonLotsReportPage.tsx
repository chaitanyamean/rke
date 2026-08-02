import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useCottonLots } from '../../api/cotton'
import { useAuth } from '../../auth/AuthContext'
import ReportShell from '../../components/ReportShell'
import SearchSelect from '../../components/SearchSelect'
import { printReport, esc } from '../../lib/printReport'

function fmtQty(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}
function fmtCcy(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function CottonLotsReportPage() {
  const today = new Date().toISOString().slice(0, 10)

  const [draft, setDraft] = useState({ fromDate: '2026-04-01', toDate: today })
  const [active, setActive] = useState({ fromDate: '2026-04-01', toDate: today })
  const [selectedSerial, setSelectedSerial] = useState('')

  const { data: allLots = [], isLoading } = useCottonLots(active.fromDate, active.toDate)
  const { isAdmin } = useAuth()

  // Filter by selected serial if one is chosen
  const lots = useMemo(
    () => selectedSerial ? allLots.filter(l => l.vehicleSerialNumber === selectedSerial) : allLots,
    [allLots, selectedSerial]
  )

  // Serial number options for the SearchSelect
  const serialOptions = useMemo(
    () => allLots.map(l => ({ id: l.vehicleSerialNumber, label: l.vehicleSerialNumber })),
    [allLots]
  )

  const run = () => {
    setActive({ ...draft })
  }

  const totalQty    = lots.reduce((s, l) => s + l.totalQuantity, 0)
  const totalAmount = lots.reduce((s, l) => s + l.totalAmount, 0)

  const handlePrint = () => {
    const dateRange = [active.fromDate, active.toDate].filter(Boolean).join(' to ')
    const rows = lots.flatMap(l =>
      l.entries.length === 0
        ? [`<tr>
            <td>${esc(l.lotDate)}</td>
            <td style="font-family:monospace">${esc(l.vehicleSerialNumber)}</td>
            <td>${esc(l.vehicleRegistrationNumber)}</td>
            <td>${esc(l.mutaHamaliName)}</td>
            <td class="right">${fmtCcy(l.commonPrice)}</td>
            <td colspan="4" class="muted">No entries</td>
           </tr>`]
        : l.entries.map((e, i) => {
            const rs = l.entries.length > 1 ? ` rowspan="${l.entries.length}"` : ''
            return `<tr>
              ${i === 0 ? `<td${rs}>${esc(l.lotDate)}</td>` : ''}
              ${i === 0 ? `<td${rs} style="font-family:monospace">${esc(l.vehicleSerialNumber)}</td>` : ''}
              ${i === 0 ? `<td${rs}>${esc(l.vehicleRegistrationNumber)}</td>` : ''}
              ${i === 0 ? `<td${rs}>${esc(l.mutaHamaliName)}</td>` : ''}
              ${i === 0 ? `<td${rs} class="right">${fmtCcy(l.commonPrice)}</td>` : ''}
              <td>${esc(e.villageName)}</td>
              <td>${e.farmerName ? `<strong>${esc(e.farmerName)}</strong>${e.fatherName ? `<br/><span style="color:#94a3b8;font-size:9px">${esc(e.fatherName)}</span>` : ''}` : '—'}</td>
              <td class="right">${fmtQty(e.quantity)}</td>
              <td class="right">${fmtCcy(e.price)}</td>
              <td class="right">${fmtCcy(e.amount)}</td>
            </tr>`
          })
    ).join('')

    const table = `<table>
      <thead><tr>
        <th>Date</th><th>Serial</th><th>Vehicle Reg</th><th>Muta Hamali</th>
        <th class="right">Common Price (₹/kg)</th>
        <th>Village</th><th>Farmer</th>
        <th class="right">Qty(kg)</th><th class="right">Price (₹/kg)</th><th class="right">Amount (₹)</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td colspan="7" class="right">Total</td>
        <td class="right">${fmtQty(totalQty)}</td>
        <td></td>
        <td class="right">₹${fmtCcy(totalAmount)}</td>
      </tr></tfoot>
    </table>`

    printReport(
      'Cotton Lots',
      `Period: ${dateRange || 'All dates'} · ${lots.length} lot${lots.length !== 1 ? 's' : ''}`,
      table,
      `Cotton Lots${dateRange ? ' - ' + dateRange : ''}`,
    )
  }

  const filters = (
    <>
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Serial Number</span>
        <SearchSelect
          options={serialOptions}
          value={selectedSerial}
          onChange={setSelectedSerial}
          placeholder="Search serial…"
        />
      </label>
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">From</span>
        <input
          type="date"
          value={draft.fromDate}
          onChange={(e) => setDraft((d) => ({ ...d, fromDate: e.target.value }))}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </label>
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">To</span>
        <input
          type="date"
          value={draft.toDate}
          onChange={(e) => setDraft((d) => ({ ...d, toDate: e.target.value }))}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm"
        />
      </label>
    </>
  )

  // columns: Date, Serial, Vehicle Reg, Muta Hamali, Common Price, Village, Farmer, Qty, Price, Amount = 10
  // + optional Edit column for admin = 11
  const COLS = isAdmin ? 11 : 10

  return (
    <ReportShell
      title="Cotton Lots"
      filters={filters}
      onRun={run}
      isLoading={isLoading}
      ran={true}
      actions={lots.length > 0 ? (
        <button
          onClick={handlePrint}
          className="rounded-md border border-slate-300 px-5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 flex items-center gap-1.5"
        >
          ⬇ Download PDF
        </button>
      ) : undefined}
    >
      {lots.length === 0 ? (
        <p className="p-6 text-center text-sm text-slate-500">No cotton lots found.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[900px] text-sm border-collapse">
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                {['Date', 'Serial', 'Vehicle Reg', 'Muta Hamali', 'Common Price (₹/kg)', 'Village', 'Farmer', 'Qty(kgs)', 'Price (₹/kg)', 'Amount (₹)', ...(isAdmin ? [''] : [])].map((h, i) => (
                  <th key={i} className="px-3 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {lots.map(l => {
                if (l.entries.length === 0) {
                  return (
                    <tr key={l.id} className="border-b border-slate-100">
                      <td className="px-3 py-2.5 text-slate-600 whitespace-nowrap">{l.lotDate}</td>
                      <td className="px-3 py-2.5 font-mono text-xs text-slate-700">{l.vehicleSerialNumber}</td>
                      <td className="px-3 py-2.5 text-slate-600">{l.vehicleRegistrationNumber || '—'}</td>
                      <td className="px-3 py-2.5 text-slate-600">{l.mutaHamaliName || '—'}</td>
                      <td className="px-3 py-2.5 text-right text-slate-700">₹{fmtCcy(l.commonPrice)}</td>
                      <td colSpan={5} className="px-3 py-2.5 text-slate-400 italic">No entries</td>
                      {isAdmin && (
                        <td className="px-3 py-2.5 text-right">
                          <Link to={`/cotton/${l.id}/edit`} className="text-xs font-medium text-blue-600 hover:underline">Edit</Link>
                        </td>
                      )}
                    </tr>
                  )
                }
                return l.entries.map((e, i) => {
                  const isFirst = i === 0
                  const rs = l.entries.length
                  return (
                    <tr key={e.id} className="border-b border-slate-100 hover:bg-slate-50">
                      {isFirst && (
                        <>
                          <td rowSpan={rs} className="px-3 py-2.5 text-slate-600 whitespace-nowrap align-top border-r border-slate-100">{l.lotDate}</td>
                          <td rowSpan={rs} className="px-3 py-2.5 font-mono text-xs text-slate-700 align-top border-r border-slate-100">{l.vehicleSerialNumber}</td>
                          <td rowSpan={rs} className="px-3 py-2.5 text-slate-600 align-top border-r border-slate-100">{l.vehicleRegistrationNumber || '—'}</td>
                          <td rowSpan={rs} className="px-3 py-2.5 text-slate-600 align-top border-r border-slate-100">{l.mutaHamaliName || '—'}</td>
                          <td rowSpan={rs} className="px-3 py-2.5 text-right text-slate-700 align-top border-r border-slate-100">₹{fmtCcy(l.commonPrice)}</td>
                        </>
                      )}
                      <td className="px-3 py-2.5 text-slate-600">{e.villageName || '—'}</td>
                      <td className="px-3 py-2.5 text-slate-800 font-medium">
                        <div className="font-medium text-slate-800">{e.farmerName || '—'}</div>
                        {e.fatherName && <div className="text-xs text-slate-500">{e.fatherName}</div>}
                      </td>
                      <td className="px-3 py-2.5 text-right text-slate-700">{fmtQty(e.quantity)}</td>
                      <td className="px-3 py-2.5 text-right text-slate-700">₹{fmtCcy(e.price)}</td>
                      <td className="px-3 py-2.5 text-right font-semibold text-slate-800">₹{fmtCcy(e.amount)}</td>
                      {isFirst && isAdmin && (
                        <td rowSpan={rs} className="px-3 py-2.5 text-right align-top">
                          <Link to={`/cotton/${l.id}/edit`} className="text-xs font-medium text-blue-600 hover:underline">Edit</Link>
                        </td>
                      )}
                    </tr>
                  )
                })
              })}
            </tbody>
            <tfoot className="bg-slate-50 font-semibold border-t-2 border-slate-300">
              <tr>
                <td colSpan={COLS - 3} className="px-3 py-3 text-right text-xs uppercase tracking-wide text-slate-500">Total</td>
                <td className="px-3 py-3 text-right text-slate-700">{fmtQty(totalQty)}</td>
                <td className="px-3 py-3" />
                <td className="px-3 py-3 text-right text-slate-800">₹{fmtCcy(totalAmount)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
