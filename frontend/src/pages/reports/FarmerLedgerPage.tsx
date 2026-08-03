import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { Farmer } from '../../types'
import { useFarmerLedger, type DateRangeFilter, type FarmerLedgerRow } from '../../api/reports'
import { useAuth } from '../../auth/AuthContext'
import { useVillages } from '../../api/villages'
import FarmerSelector from '../../components/FarmerSelector'
import ReportShell from '../../components/ReportShell'
import { formatBalance } from '../../lib/balance'
import { printReport, esc } from '../../lib/printReport'

function editPathFor(transactionType: string, transactionId: string, cottonLotId?: string | null): string | null {
  switch (transactionType) {
    case 'cash_sale':    return `/sales/cash/${transactionId}/edit`
    case 'credit_sale':  return `/sales/credit/${transactionId}/edit`
    case 'cash_payment': return `/payments/payment/${transactionId}/edit`
    case 'cash_receipt': return `/payments/receipt/${transactionId}/edit`
    case 'return':       return `/returns/${transactionId}/edit`
    case 'cotton_procurement': return cottonLotId ? `/cotton/${cottonLotId}/edit` : null
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
  return n.toLocaleString('en-IN', { minimumFractionDigits: 0, maximumFractionDigits: 0 })
}

/** Group flat rows by transactionId, preserving order. */
function groupByTransaction(rows: FarmerLedgerRow[]): FarmerLedgerRow[][] {
  const groups: FarmerLedgerRow[][] = []
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

export default function FarmerLedgerPage() {
  const { isAdmin } = useAuth()
  const { data: villages = [] } = useVillages()
  const [farmer, setFarmer] = useState<Farmer | null>(null)
  const [draft, setDraft] = useState<DateRangeFilter>({
    fromDate: '2026-04-01',
    toDate: new Date().toISOString().slice(0, 10),
    includeVoided: false,
  })
  const [active, setActive] = useState<(DateRangeFilter & { farmerId: string }) | null>(null)

  const { data = [], isLoading } = useFarmerLedger(
    active?.farmerId ?? null,
    active ?? {},
    !!active,
  )

  const groups = groupByTransaction(data)

  // Compute interest per transaction group.
  // Formula: if PrevBalance < 0 → |PrevBalance| * days * 24 / 365 / 100
  // (24% annual rate, balance is negative when farmer owes money)
  const INTEREST_RATE = 24 // % per annum
  const firstRows = groups.map((g) => g[0])

  const interestByTransactionId = new Map<string, number>()
  for (let i = 1; i < firstRows.length; i++) {
    const prev = firstRows[i - 1]
    const curr = firstRows[i]
    if (prev.runningBalance < 0) {
      const [py, pm, pd] = prev.transactionDate.split('-').map(Number)
      const [cy, cm, cdd] = curr.transactionDate.split('-').map(Number)
      const prevDate = new Date(py, pm - 1, pd)
      const currDate = new Date(cy, cm - 1, cdd)
      const days = Math.max(0, (currDate.getTime() - prevDate.getTime()) / (1000 * 60 * 60 * 24))
      const interest = Math.abs(prev.runningBalance) * days * INTEREST_RATE / 365 / 100
      interestByTransactionId.set(curr.transactionId, interest)
    } else {
      interestByTransactionId.set(curr.transactionId, 0)
    }
  }
  // First transaction has no previous — interest is 0
  if (firstRows.length > 0) {
    interestByTransactionId.set(firstRows[0].transactionId, 0)
  }

  // Trailing interest: if the last transaction's closing balance is negative,
  // compute interest from that date to today.
  const closingBalance = firstRows.length > 0 ? firstRows[firstRows.length - 1].runningBalance : 0
  const trailingInterest = (() => {
    if (firstRows.length === 0 || closingBalance >= 0) return 0
    const lastRow = firstRows[firstRows.length - 1]
    // Parse as local date to avoid UTC timezone offset issues
    const [ly, lm, ld] = lastRow.transactionDate.split('-').map(Number)
    const lastDate = new Date(ly, lm - 1, ld)
    const now = new Date()
    const today = new Date(now.getFullYear(), now.getMonth(), now.getDate())
    const days = Math.max(0, (today.getTime() - lastDate.getTime()) / (1000 * 60 * 60 * 24))
    return Math.abs(closingBalance) * days * INTEREST_RATE / 365 / 100
  })()

  // Footer totals — one row per transaction to avoid double-counting
  const totalDebit     = firstRows.reduce((s, r) => s + r.debitAmount, 0)
  const totalCredit    = firstRows.reduce((s, r) => s + r.creditAmount, 0)
  const totalInterest  = firstRows.reduce((s, r) => s + (interestByTransactionId.get(r.transactionId) ?? 0), 0)
                         + trailingInterest

  const run = () => {
    if (!farmer) return
    setActive({ ...draft, farmerId: farmer.id })
  }

  // ── PDF print via a new window ──────────────────────────────────────────
  const printLedger = () => {
    const villageName = farmer ? (villages.find(v => v.id === farmer.villageId)?.name ?? '') : ''
    const farmerLabel = farmer
      ? [farmer.name, farmer.fatherName, villageName].filter(Boolean).join(' / ')
      : ''
    const dateRange = [active?.fromDate, active?.toDate].filter(Boolean).join(' to ')

    const rows = groups.flatMap((group) =>
      group.map((row, itemIdx) => {
        const first = group[0]
        const isFirst = itemIdx === 0
        const rs = group.length  // rowspan value
        const isDebit = first.direction === 'DEBIT'
        const balClass = first.runningBalance < 0 ? 'bal-neg' : first.runningBalance > 0 ? 'bal-pos' : 'muted'
        const spanAttr = rs > 1 ? ` rowspan="${rs}"` : ''
        return `<tr>
          ${isFirst ? `<td${spanAttr}>${esc(first.transactionDate)}</td>` : ''}
          ${isFirst ? `<td${spanAttr} style="font-family:monospace;font-size:9px">${esc(first.billNumber)}</td>` : ''}
          ${isFirst ? `<td${spanAttr}><span class="badge ${isDebit ? 'badge-d' : 'badge-c'}">${esc(fmtType(first.transactionType))}</span></td>` : ''}
          <td>${esc(row.categoryName)}</td>
          <td>${esc(row.itemName) || `<span class="muted">${first.transactionType === 'cash_payment' ? 'Payment' : first.transactionType === 'cash_receipt' ? 'Payment Received' : first.transactionType === 'cotton_procurement' ? 'Cotton Procurement' : '—'}</span>`}</td>
          <td class="right">${fmtQty(row.quantity)}</td>
          <td class="right">${row.price ? fmtCcy(row.price) : '—'}</td>
          ${isFirst ? `<td${spanAttr} class="right">${first.debitAmount > 0 ? `<span class="debit">₹${fmtCcy(first.debitAmount)}</span>` : '<span class="muted">—</span>'}</td>` : ''}
          ${isFirst ? `<td${spanAttr} class="right">${first.creditAmount > 0 ? `<span class="credit">+₹${fmtCcy(first.creditAmount)}</span>` : '<span class="muted">—</span>'}</td>` : ''}
          ${isFirst ? `<td${spanAttr} class="right">${fmtCcy(interestByTransactionId.get(first.transactionId) ?? 0)}</td>` : ''}
          ${isFirst ? `<td${spanAttr} class="right ${balClass}">${fmtCcy(first.runningBalance)}</td>` : ''}
          ${isFirst ? `<td${spanAttr}>${esc(first.remarks)}</td>` : ''}
        </tr>`
      })
    ).join('')

    const { label: closingLabel, direction: closingDir } = formatBalance(closingBalance - totalInterest)
    const closingCls = closingDir === 'owes' ? 'bal-neg' : closingDir === 'credit' ? 'bal-pos' : 'muted'

    const table = `<table>
      <thead><tr>
        <th>Date</th><th>Bill Number</th><th>Type</th>
        <th>Item Category</th><th>Items</th><th class="right">Qty</th><th class="right">Price (₹)</th>
        <th class="right">Debit (₹)</th><th class="right">Credit (₹)</th>
        <th class="right">Interest (₹)</th><th class="right">Balance (₹)</th><th>Remarks</th>
      </tr></thead>
      <tbody>${rows}</tbody>
      <tfoot><tr>
        <td colspan="7" class="right" style="font-size:9px;text-transform:uppercase;letter-spacing:0.04em;color:#475569">Totals</td>
        <td class="right debit">₹${fmtCcy(totalDebit)}</td>
        <td class="right credit">+₹${fmtCcy(totalCredit)}</td>
        <td class="right">${fmtCcy(totalInterest)}</td>
        <td colspan="2" class="right ${closingCls}" style="font-size:13px">${closingLabel}</td>
      </tr></tfoot>
    </table>`

    printReport(
      'Farmer Ledger',
      `Farmer: <strong>${esc(farmerLabel)}</strong> &nbsp;|&nbsp; Period: ${dateRange || 'All dates'}`,
      table,
      `Farmer Ledger - ${farmerLabel}${dateRange ? ' - ' + dateRange : ''}`,
    )
  }

  // ── actions slot (only when data is present) ────────────────────────────
  const pdfButton = data.length > 0 ? (
    <button
      onClick={printLedger}
      className="rounded-md border border-slate-300 px-5 py-2 text-sm font-semibold text-slate-700 hover:bg-slate-50 flex items-center gap-1.5"
    >
      ⬇ Download PDF
    </button>
  ) : null

  // ── filters ─────────────────────────────────────────────────────────────
  const filters = (
    <>
      <div className="w-full">
        <span className="mb-1 block text-sm font-medium text-slate-700">Farmer</span>
        <FarmerSelector onChange={setFarmer} />
      </div>
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

  const colCount = 12 + (isAdmin ? 1 : 0)

  return (
    <ReportShell title="Farmer Ledger" filters={filters} onRun={run}
      isLoading={isLoading} ran={!!active} actions={pdfButton}>
      {data.length === 0 ? (
        <p className="p-6 text-center text-sm text-slate-500">No transactions found.</p>
      ) : (
        // printRef kept for potential future use
        <div className="overflow-x-auto">
          <table className="w-full min-w-[1100px] text-sm border-collapse">
            <thead className="border-b border-slate-200 bg-slate-50">
              <tr>
                {[
                  'Date', 'Bill Number', 'Type',
                  'Item Category', 'Items', 'Qty', 'Price (₹)',
                  'Debit (₹)', 'Credit (₹)', 'Interest (₹)', 'Balance (₹)', 'Remarks',
                ].concat(isAdmin ? [''] : []).map((h, i) => (
                  <th key={i} className={`px-3 py-3 text-left text-xs font-semibold uppercase tracking-wide text-slate-500 whitespace-nowrap${i === 12 && isAdmin ? ' no-print' : ''}`}>{h}</th>                ))}
              </tr>
            </thead>
            <tbody>
              {groups.map((group) => {
                const first = group[0]
                const rowspan = group.length
                const isDebit  = first.direction === 'DEBIT'
                const editPath = isAdmin ? editPathFor(first.transactionType, first.transactionId, first.cottonLotId) : null

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
                          <td rowSpan={rowspan} className="px-3 py-2.5 text-right font-bold text-red-600 align-top">
                            {(() => {
                              const interest = interestByTransactionId.get(first.transactionId) ?? 0
                              return interest > 0 ? fmtCcy(interest) : '—'
                            })()}
                          </td>
                          <td rowSpan={rowspan} className={`px-3 py-2.5 text-right font-bold align-top ${
                            first.runningBalance < 0 ? 'text-red-700' :
                            first.runningBalance > 0 ? 'text-green-700' : 'text-slate-500'
                          }`}>
                            {fmtCcy(first.runningBalance)}
                          </td>
                          <td rowSpan={rowspan} className="px-3 py-2.5 text-xs text-slate-600 align-top max-w-[160px]">
                            {first.remarks ?? ''}
                          </td>
                          {isAdmin && (
                            <td rowSpan={rowspan} className="px-3 py-2.5 text-right align-top no-print">
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
                <td colSpan={7} className="px-3 py-3 text-right text-xs uppercase tracking-wide text-slate-500">
                  Totals
                </td>
                <td className="px-3 py-3 text-right text-red-700">₹{fmtCcy(totalDebit)}</td>
                <td className="px-3 py-3 text-right text-green-700">+₹{fmtCcy(totalCredit)}</td>
                <td className="px-3 py-3 text-right font-bold text-red-600">{fmtCcy(totalInterest)}</td>
                {(() => {
                  const adjustedBalance = closingBalance - totalInterest
                  const { label, direction: dir } = formatBalance(adjustedBalance)
                  return (
                    <td colSpan={colCount - 10} className={`px-3 py-3 text-right text-base font-bold ${
                      dir === 'owes' ? 'text-red-700' :
                      dir === 'credit' ? 'text-green-700' : 'text-slate-500'
                    }`}>
                      {label}
                    </td>
                  )
                })()}
              </tr>
            </tfoot>
          </table>
        </div>
      )}
    </ReportShell>
  )
}
