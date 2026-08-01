import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useTransactionsReport, type DateRangeFilter, type TransactionReportRow } from '../../api/reports'
import { useAuth } from '../../auth/AuthContext'
import ReportShell from '../../components/ReportShell'
import { printReport, esc } from '../../lib/printReport'

function editPathFor(transactionType: string, transactionId: string): string | null {
  switch (transactionType) {
    case 'cash_sale':    return `/sales/cash/${transactionId}/edit`
    case 'credit_sale':  return `/sales/credit/${transactionId}/edit`
    case 'cash_payment': return `/payments/payment/${transactionId}/edit`
    case 'cash_receipt': return `/payments/receipt/${transactionId}/edit`
    case 'return':       return `/returns/${transactionId}/edit`
    case 'cotton_procurement': return null  // edit via cotton lot page — no direct entry edit
    default:             return null
  }
}

function fmtType(t: string) {
  if (t === 'cotton_procurement') return 'Cotton Procurement'
  return t.replace(/_/g, ' ').toLowerCase().replace(/\b\w/g, (c) => c.toUpperCase())
}

function fmtCcy(n: number) {
  return n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function fmtQty(n: number | null) {
  if (n === null || n === 0) return '—'
  return n.toLocaleString('en-IN', { minimumFractionDigits: 3, maximumFractionDigits: 3 })
}

function financialYearStart(): string {
  return '2026-04-01'
}

/** Group flat rows by transactionId, preserving order. */
function groupByTransaction(rows: TransactionReportRow[]): TransactionReportRow[][] {
  const groups: TransactionReportRow[][] = []
  const seen = new Map<string, number>()
  for (const row of rows) {
    const idx = seen.get(row.transactionId)
    if (idx === undefined) {
      seen.set(row.transactionId, groups.length)
      groups.push([row])
    } else {
      groups[idx].push(row)
    }
  }
  return groups
}

type Filter = DateRangeFilter & { farmerId?: string; billNumber?: string }

export default function TransactionsReportPage() {
  const { isAdmin } = useAuth()
  const today = new Date().toISOString().slice(0, 10)

  const defaultFilter: Filter = { fromDate: financialYearStart(), toDate: today }
  const [draft, setDraft] = useState<Filter>(defaultFilter)
  const [active, setActive] = useState<Filter>(defaultFilter)

  const { data = [], isLoading } = useTransactionsReport(active, true)
  const groups = groupByTransaction(data)

  const totalDebit  = groups.map(g => g[0]).reduce((s, r) => s + r.debitAmount, 0)
  const totalCredit = groups.map(g => g[0]).reduce((s, r) => s + r.creditAmount, 0)

  // All fields are optional — Run Report always fires with whatever is filled in
  const run = () => setActive({ ...draft })

  const handlePrint = () => {
    const dateRange = [active.fromDate, active.toDate].filter(Boolean).join(' to ')
    const rows = groups.flatMap((group) =>
      group.map((row, itemIdx) => {
        const first = group[0]
        const isFirst = itemIdx === 0
        const rs = group.length
        const isDebit = first.direction === 'DEBIT'
        const spanAttr = rs > 1 ? ` rowspan="${rs}"` : ''
        return `<tr>
          ${isFirst ? `<td${spanAttr}>${esc(first.transactionDate)}</td>` : ''}
          ${isFirst ? `<td${spanAttr} style="font-family:monospace;font-size:9px">${esc(first.billNumber)}</td>` : ''}
          ${isFirst ? `<td${spanAttr}>${esc(first.farmerName)}${first.fatherName ? `<br/><span style="color:#94a3b8;font-size:9px">${esc(first.fatherName)}</span>` : ''}</td>` : ''}
          ${isFirst ? `<td${spanAttr}><span class="badge ${isDebit ? 'badge-d' : 'badge-c'}">${esc(fmtType(first.transactionType))}</span></td>` : ''}
          <td>${esc(row.categoryName)}</td>
          <td>${esc(row.itemName) || `<span class="muted">${first.transactionType === 'cash_payment' ? 'Payment' : first.transactionType === 'cash_receipt' ? 'Payment Received' : first.transactionType === 'cotton_procurement' ? 'Cotton Procurement' : '—'}</span>`}</td>
          <td class="right">${fmtQty(row.quantity)}</td>
          <td class="right">${row.price ? fmtCcy(row.price) : '—'}</td>
          ${isFirst ? `<td${spanAttr} class="right">${first.debitAmount > 0 ? `<span class="debit">₹${fmtCcy(first.debitAmount)}</span>` : '<span class="muted">—</span>'}</td>` : ''}
          ${isFirst ? `<td${spanAttr} class="right">${first.creditAmount > 0 ? `<span class="credit">+₹${fmtCcy(first.creditAmount)}</span>` : '<span class="muted">—</span>'}</td>` : ''}
          ${isFirst ? `<td${spanAttr}>${esc(first.remarks)}</td>` : ''}
        </tr>`
      })
    ).join('')
    const table = `<table>
      <thead><tr>
        <th>Date</th><th>Bill Number</th><th>Farmer</th><th>Type</th>
        <th>Item Category</th><th>Items</th>
        <th class="right">Qty</th><th class="right">Price (₹)</th>
        <th class="right">Debit (₹)</th><th class="right">Credit (₹)</th><th>Remarks</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td colspan="8" class="right" style="font-size:9px;text-transform:uppercase;letter-spacing:0.04em;color:#475569">
          ${groups.length} transaction${groups.length !== 1 ? 's' : ''}
        </td>
        <td class="right debit">₹${fmtCcy(totalDebit)}</td>
        <td class="right credit">+₹${fmtCcy(totalCredit)}</td>
        <td></td>
      </tr></tfoot>
    </table>`
    printReport('Transactions', `Period: ${dateRange || 'All dates'}`, table, `Transactions${dateRange ? ' - ' + dateRange : ''}`)
  }

  const filters = (
    <>
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Bill Number</span>
        <input
          type="text"
          value={draft.billNumber ?? ''}
          onChange={(e) => setDraft((d) => ({ ...d, billNumber: e.target.value }))}
          placeholder="Partial match…"
          className="rounded-md border border-slate-300 px-3 py-2 text-sm w-36"
        />
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
    <ReportShell title="Transactions" filters={filters} onRun={run}
      isLoading={isLoading} ran={true}
      actions={data.length > 0 ? (
        <button onClick={handlePrint}
          className="rounded-md border border-slate-300 px-5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 flex items-center gap-1.5">
          ⬇ Download PDF
        </button>
      ) : undefined}
    >
      {data.length === 0 ? (
        <p className="p-6 text-center text-sm text-slate-500">No transactions found.</p>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1000px] text-sm border-collapse">
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                {[
                  'Date', 'Bill Number', 'Farmer', 'Type',
                  'Item Category', 'Items', 'Qty', 'Price (₹)',
                  'Debit (₹)', 'Credit (₹)', 'Remarks',
                ].concat(isAdmin ? [''] : []).map((h, i) => (
                  <th key={i} className="px-3 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 whitespace-nowrap">{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {groups.map((group) => {
                const first = group[0]
                const rowspan = group.length
                const isDebit = first.direction === 'DEBIT'
                const editPath = isAdmin ? editPathFor(first.transactionType, first.transactionId) : null

                return group.map((row, itemIdx) => {
                  const isFirstRow = itemIdx === 0
                  return (
                    <tr key={`${row.transactionId}-${itemIdx}`}
                      className="border-b border-slate-100 hover:bg-slate-50">

                      {isFirstRow && (
                        <>
                          <td rowSpan={rowspan} className="px-3 py-2.5 text-slate-600 align-top whitespace-nowrap border-r border-slate-100">
                            {first.transactionDate}
                          </td>
                          <td rowSpan={rowspan} className="px-3 py-2.5 font-mono text-xs text-slate-700 align-top border-r border-slate-100">
                            {first.billNumber}
                          </td>
                          <td rowSpan={rowspan} className="px-3 py-2.5 align-top border-r border-slate-100">
                            <div className="font-medium text-slate-800">{first.farmerName}</div>
                            {first.fatherName && <div className="text-xs text-slate-500">{first.fatherName}</div>}
                          </td>
                          <td rowSpan={rowspan} className="px-3 py-2.5 align-top border-r border-slate-100">
                            <span className={`rounded px-1.5 py-0.5 text-xs font-medium ${
                              isDebit ? 'bg-red-50 text-red-700' : 'bg-green-50 text-green-700'
                            }`}>
                              {fmtType(first.transactionType)}
                            </span>
                          </td>
                        </>
                      )}

                      <td className="px-3 py-2.5 text-slate-700">{row.categoryName ?? '—'}</td>
                      <td className="px-3 py-2.5 text-slate-800">
                        {row.itemName ?? (
                          <span className="italic text-slate-500">
                            {first.transactionType === 'cash_payment' ? 'Payment' :
                             first.transactionType === 'cash_receipt' ? 'Payment Received' :
                             first.transactionType === 'cotton_procurement' ? 'Cotton Procurement' : '—'}
                          </span>
                        )}
                      </td>
                      <td className="px-3 py-2.5 text-right text-slate-700">{fmtQty(row.quantity)}</td>
                      <td className="px-3 py-2.5 text-right text-slate-700">
                        {row.price ? fmtCcy(row.price) : '—'}
                      </td>

                      {isFirstRow && (
                        <>
                          <td rowSpan={rowspan} className="px-3 py-2.5 text-right font-semibold align-top border-l border-slate-100">
                            {first.debitAmount > 0
                              ? <span className="text-red-600">₹{fmtCcy(first.debitAmount)}</span>
                              : <span className="text-slate-500">—</span>}
                          </td>
                          <td rowSpan={rowspan} className="px-3 py-2.5 text-right font-semibold align-top">
                            {first.creditAmount > 0
                              ? <span className="text-green-700">+₹{fmtCcy(first.creditAmount)}</span>
                              : <span className="text-slate-500">—</span>}
                          </td>
                          <td rowSpan={rowspan} className="px-3 py-2.5 text-xs text-slate-600 align-top">
                            {first.remarks ?? ''}
                          </td>
                          {isAdmin && (
                            <td rowSpan={rowspan} className="px-3 py-2.5 text-right align-top">
                              {editPath && (
                                <Link to={editPath} className="text-xs font-medium text-blue-600 hover:underline">
                                  Edit
                                </Link>
                              )}
                            </td>
                          )}
                        </>
                      )}
                    </tr>
                  )
                })
              })}
            </tbody>
            <tfoot className="bg-slate-50 border-t-2 border-slate-300">
              <tr className="font-semibold text-sm">
                <td colSpan={8} className="px-3 py-3 text-right text-xs uppercase tracking-wide text-slate-500">
                  {groups.length} transaction{groups.length !== 1 ? 's' : ''}
                </td>
                <td className="px-3 py-3 text-right text-red-700">
                  ₹{fmtCcy(totalDebit)}
                </td>
                <td className="px-3 py-3 text-right text-green-700">
                  +₹{fmtCcy(totalCredit)}
                </td>
                <td colSpan={isAdmin ? 2 : 1} />
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
