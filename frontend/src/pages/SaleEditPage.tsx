import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import type { Farmer } from '../types'
import {
  useSale,
  useUpdateCashSale,
  useUpdateCreditSale,
  type SaleLineItemInput,
} from '../api/transactions'
import { useItems } from '../api/items'
import { useFarmers } from '../api/farmers'
import FarmerSelector from '../components/FarmerSelector'
import SearchSelect from '../components/SearchSelect'
import { getErrorMessage } from '../lib/api'

interface Props {
  saleType: 'cash' | 'credit'
}

interface LineDraft {
  itemId: string
  quantity: string
  price: string
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * Admin-only correction screen for an existing cash/credit sale. Bill number
 * and bill number type are fixed (see backend SaleUpdateRequest) — only
 * farmer, date, line items, and remarks can be corrected here.
 */
export default function SaleEditPage({ saleType }: Props) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: sale, isLoading, error: fetchError } = useSale(saleType, id ?? null)
  const { data: allItems = [] } = useItems()
  const { data: farmers = [] } = useFarmers({}, true)
  const updateCash = useUpdateCashSale()
  const updateCredit = useUpdateCreditSale()
  const update = saleType === 'cash' ? updateCash : updateCredit

  const [farmer, setFarmer] = useState<Farmer | null>(null)
  const [txDate, setTxDate] = useState(today)
  const [lines, setLines] = useState<LineDraft[]>([])
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  // Prefill once the sale and farmer list have loaded.
  useEffect(() => {
    if (!sale) return
    setTxDate(sale.transactionDate)
    setLines(
      sale.items.map((i) => ({
        itemId: i.itemId,
        quantity: String(i.quantity),
        price: String(i.price),
      })),
    )
    setRemarks(sale.remarks ?? '')
  }, [sale])

  useEffect(() => {
    if (!sale || farmer) return
    const match = farmers.find((f) => f.id === sale.farmerId)
    if (match) setFarmer(match)
  }, [sale, farmers, farmer])

  const itemOptions = useMemo(() => allItems.map((i) => ({ id: i.id, label: i.name })), [allItems])

  const grandTotal = useMemo(
    () =>
      lines.reduce((sum, l) => sum + (parseFloat(l.quantity) || 0) * (parseFloat(l.price) || 0), 0),
    [lines],
  )

  const isComplete =
    farmer &&
    txDate &&
    txDate <= today() &&
    lines.length > 0 &&
    lines.every((l) => l.itemId && parseFloat(l.quantity) > 0 && parseFloat(l.price) >= 0)

  const handleSave = async () => {
    if (!farmer || !isComplete) return
    setError(null)
    try {
      const items: SaleLineItemInput[] = lines.map((l) => ({
        itemId: l.itemId,
        quantity: parseFloat(l.quantity),
        price: parseFloat(l.price),
      }))
      await update.mutateAsync({
        id: id!,
        input: {
          farmerId: farmer.id,
          transactionDate: txDate,
          items,
          remarks: remarks.trim() || undefined,
        },
      })
      setSaved(true)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  if (isLoading) return <p className="text-slate-400">Loading…</p>
  if (fetchError) {
    return <p className="text-sm text-red-600">{getErrorMessage(fetchError, 'Sale not found.')}</p>
  }
  if (!sale) return null

  if (saved) {
    return (
      <div className="mx-auto max-w-lg rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
        <div className="mb-2 text-4xl">✓</div>
        <h2 className="mb-1 text-xl font-bold text-green-800">Sale Updated</h2>
        <p className="mb-6 text-sm text-green-700">
          Bill Number: <span className="font-semibold">{sale.billNumber}</span>
        </p>
        <button
          onClick={() => navigate(-1)}
          className="rounded-md bg-green-700 px-6 py-2 text-white hover:bg-green-800"
        >
          Back
        </button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">
        Edit {saleType === 'cash' ? 'Cash' : 'Credit'} Sale
        <span className="ml-2 font-mono text-base text-slate-400">{sale.billNumber}</span>
      </h1>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Farmer</h2>
        <FarmerSelector value={farmer} onChange={setFarmer} />
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Transaction Date</span>
          <input
            type="date"
            value={txDate}
            max={today()}
            onChange={(e) => setTxDate(e.target.value)}
            className="rounded-md border border-slate-300 px-3 py-2"
          />
        </label>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Items</h2>
        <div className="space-y-3">
          {lines.map((line, idx) => {
            const amount = (parseFloat(line.quantity) || 0) * (parseFloat(line.price) || 0)
            return (
              <div key={idx} className="flex flex-wrap items-end gap-2">
                <div className="flex-1 min-w-[160px]">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Item</span>
                  <SearchSelect
                    options={itemOptions}
                    value={line.itemId}
                    onChange={(itemId) =>
                      setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, itemId } : l)))
                    }
                    placeholder="Search item…"
                  />
                </div>
                <div className="w-24">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Qty</span>
                  <input
                    type="number"
                    min="0.001"
                    step="any"
                    value={line.quantity}
                    onChange={(e) =>
                      setLines((prev) =>
                        prev.map((l, i) => (i === idx ? { ...l, quantity: e.target.value } : l)),
                      )
                    }
                    className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
                  />
                </div>
                <div className="w-24">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Price (₹)</span>
                  <input
                    type="number"
                    min="0"
                    step="0.01"
                    value={line.price}
                    onChange={(e) =>
                      setLines((prev) =>
                        prev.map((l, i) => (i === idx ? { ...l, price: e.target.value } : l)),
                      )
                    }
                    className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
                  />
                </div>
                <div className="w-24 text-right">
                  <span className="mb-1 block text-xs font-medium text-slate-500">Amount</span>
                  <span className="block py-1.5 text-sm font-medium text-slate-700">
                    ₹{amount.toFixed(2)}
                  </span>
                </div>
                {lines.length > 1 && (
                  <button
                    type="button"
                    onClick={() => setLines((prev) => prev.filter((_, i) => i !== idx))}
                    className="pb-1.5 text-slate-400 hover:text-red-500"
                    title="Remove"
                  >
                    ✕
                  </button>
                )}
              </div>
            )
          })}
        </div>
        <button
          type="button"
          onClick={() => setLines((l) => [...l, { itemId: '', quantity: '1', price: '' }])}
          className="mt-3 rounded-md border border-slate-300 px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-50"
        >
          + Add Item
        </button>
        <div className="mt-4 border-t border-slate-100 pt-3 text-right text-base font-bold text-slate-800">
          Grand Total: ₹{grandTotal.toFixed(2)}
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Remarks</span>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
          />
        </label>
      </section>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex justify-end gap-3">
        <button
          type="button"
          onClick={() => navigate(-1)}
          className="rounded-md border border-slate-300 px-5 py-2.5 text-slate-700 hover:bg-slate-50"
        >
          Cancel
        </button>
        <button
          onClick={handleSave}
          disabled={!isComplete || update.isPending}
          className="rounded-md bg-brand px-8 py-2.5 font-semibold text-white hover:opacity-90 disabled:opacity-40"
        >
          {update.isPending ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
    </div>
  )
}
