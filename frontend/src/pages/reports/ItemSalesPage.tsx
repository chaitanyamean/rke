import { useState } from 'react'
import { useItemCategories } from '../../api/itemCategories'
import { useItemSales, type DateRangeFilter } from '../../api/reports'
import ReportShell from '../../components/ReportShell'
import { printReport, esc } from '../../lib/printReport'

function fmtQty(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 3, maximumFractionDigits: 3 })
}
function fmtCcy(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function ItemSalesPage() {
  const today = new Date().toISOString().slice(0, 10)
  const [draft, setDraft] = useState<DateRangeFilter & { categoryId?: string }>({
    fromDate: '2026-04-01', toDate: today, includeVoided: false, categoryId: '',
  })
  const [active, setActive] = useState<typeof draft | null>({
    fromDate: '2026-04-01', toDate: today, includeVoided: false, categoryId: '',
  })

  const { data: categories = [] } = useItemCategories()
  const { data = [], isLoading } = useItemSales(active ?? {}, !!active)

  const run = () => setActive({ ...draft, categoryId: draft.categoryId || undefined })

  const totQty = data.reduce((s, r) => s + r.totalQuantity, 0)
  const totAmt = data.reduce((s, r) => s + r.totalAmount, 0)

  const handlePrint = () => {
    const dateRange = [active?.fromDate, active?.toDate].filter(Boolean).join(' to ')
    const rows = data.map(r => `<tr>
      <td>${esc(r.categoryName)}</td>
      <td>${esc(r.itemName)}</td>
      <td class="right">${fmtQty(r.totalQuantity)}</td>
      <td class="right">${fmtCcy(r.totalAmount)}</td>
    </tr>`).join('')
    const table = `<table>
      <thead><tr>
        <th>Category</th><th>Item</th>
        <th class="right">Net Qty</th><th class="right">Net Amount (₹)</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td colspan="2" class="right">Total</td>
        <td class="right">${fmtQty(totQty)}</td>
        <td class="right">₹${fmtCcy(totAmt)}</td>
      </tr></tfoot>
    </table>`
    printReport('Item Sales', `Period: ${dateRange || 'All dates'}`, table, `Item Sales${dateRange ? ' - ' + dateRange : ''}`)
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

      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Category</span>
        <select value={draft.categoryId ?? ''} onChange={(e) => setDraft((d) => ({ ...d, categoryId: e.target.value }))}
          className="rounded-md border border-slate-300 px-3 py-2 text-sm">
          <option value="">All categories</option>
          {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
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
    <ReportShell title="Item Sales" filters={filters} onRun={run} isLoading={isLoading} ran={!!active}
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
                {['Category', 'Item', 'Net Qty', 'Net Amount (₹)'].map((h) => (
                  <th key={h} className="px-4 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.map((row) => (
                <tr key={row.itemId} className="border-b border-slate-100 hover:bg-slate-50">
                  <td className="px-4 py-2.5 text-slate-500">{row.categoryName}</td>
                  <td className="px-4 py-2.5 font-medium text-slate-800">{row.itemName}</td>
                  <td className="px-4 py-2.5 text-right text-slate-700">{fmtQty(row.totalQuantity)}</td>
                  <td className="px-4 py-2.5 text-right font-semibold text-slate-800">
                    {fmtCcy(row.totalAmount)}
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="bg-slate-50 font-semibold">
              <tr>
                <td colSpan={2} className="px-4 py-3 text-right text-xs uppercase tracking-wide text-slate-500">Total</td>
                <td className="px-4 py-3 text-right text-slate-700">{fmtQty(totQty)}</td>
                <td className="px-4 py-3 text-right text-slate-800">₹{fmtCcy(totAmt)}</td>
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
