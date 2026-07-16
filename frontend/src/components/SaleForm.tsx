import { useEffect, useMemo, useRef, useState } from 'react'
import type { Farmer, Item } from '../types'
import type { SaleInput } from '../api/transactions'
import { fetchBillNumberPreview, useCreateCashSale, useCreateCreditSale } from '../api/transactions'
import { useBillNumberTypes } from '../api/billNumberTypes'
import { useItemCategories } from '../api/itemCategories'
import { useItems } from '../api/items'
import FarmerSelector from './FarmerSelector'
import SearchSelect from './SearchSelect'

type SaleType = 'cash' | 'credit'

interface LineItem {
  itemId: string
  quantity: string
  price: string
}

function emptyLine(): LineItem {
  return { itemId: '', quantity: '1', price: '' }
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

interface Props {
  saleType: SaleType
}

type Phase = 'form' | 'review' | 'done'

export default function SaleForm({ saleType }: Props) {
  const [phase, setPhase] = useState<Phase>('form')
  const [savedTxId, setSavedTxId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveDisabled, setSaveDisabled] = useState(false)

  const [farmer, setFarmer] = useState<Farmer | null>(null)
  const [categoryId, setCategoryId] = useState('')
  const [billTypeId, setBillTypeId] = useState('')
  const [billNumber, setBillNumber] = useState('')
  const [billNumberAuto, setBillNumberAuto] = useState(true)
  const [txDate, setTxDate] = useState(today)
  const [lines, setLines] = useState<LineItem[]>([emptyLine()])
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)

  const { data: categories = [] } = useItemCategories()
  const { data: allBillTypes = [] } = useBillNumberTypes()
  const { data: allItems = [] } = useItems()

  const billTypes = useMemo(
    () => allBillTypes.filter((bt) => bt.itemCategoryId === categoryId),
    [allBillTypes, categoryId],
  )
  const categoryItems = useMemo(
    () => allItems.filter((i) => i.itemCategoryId === categoryId),
    [allItems, categoryId],
  )

  const createCash = useCreateCashSale()
  const createCredit = useCreateCreditSale()

  const selectedBillType = useMemo(
    () => allBillTypes.find((b) => b.id === billTypeId) ?? null,
    [allBillTypes, billTypeId],
  )

  // Bill number actually stored/shown: "{BillNumberType.name}-{bill number}"
  // e.g. type "CS" + number "0001" -> "CS-0001".
  const composedBillNumber = useMemo(() => {
    const trimmed = billNumber.trim()
    if (!trimmed) return ''
    return selectedBillType ? `${selectedBillType.name}-${trimmed}` : trimmed
  }, [selectedBillType, billNumber])

  // Auto-fetch bill number preview when bill type changes.
  const previewAbort = useRef<AbortController | null>(null)
  useEffect(() => {
    if (!billTypeId || !billNumberAuto) return
    const bt = allBillTypes.find((b) => b.id === billTypeId)
    if (!bt) return
    previewAbort.current?.abort()
    previewAbort.current = new AbortController()
    fetchBillNumberPreview(bt.itemCategoryId).then(setBillNumber).catch(() => {})
  }, [billTypeId, billNumberAuto, allBillTypes])

  // Auto-set price when item is selected.
  const handleItemChange = (idx: number, itemId: string) => {
    const item = allItems.find((i) => i.id === itemId)
    const price = item ? (saleType === 'cash' ? item.cashPrice : item.creditPrice) : 0
    setLines((prev) =>
      prev.map((l, i) => (i === idx ? { ...l, itemId, price: String(price) } : l)),
    )
  }

  const handleQtyChange = (idx: number, qty: string) => {
    setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, quantity: qty } : l)))
  }

  const handlePriceChange = (idx: number, price: string) => {
    setLines((prev) => prev.map((l, i) => (i === idx ? { ...l, price } : l)))
  }

  const grandTotal = useMemo(() => {
    return lines.reduce((sum, l) => {
      const qty = parseFloat(l.quantity) || 0
      const price = parseFloat(l.price) || 0
      return sum + qty * price
    }, 0)
  }, [lines])

  const isFormComplete =
    farmer &&
    categoryId &&
    billTypeId &&
    composedBillNumber &&
    txDate &&
    txDate <= today() &&
    lines.length > 0 &&
    lines.every((l) => l.itemId && parseFloat(l.quantity) > 0 && parseFloat(l.price) >= 0)

  const handleEnd = () => {
    if (txDate > today()) {
      setError('Transaction date cannot be in the future.')
      return
    }
    if (!isFormComplete) {
      setError('Please complete all fields before reviewing.')
      return
    }
    setError(null)
    setSaveDisabled(false)
    setPhase('review')
  }

  const handleSave = async () => {
    if (!farmer || !isFormComplete) return
    setSaveDisabled(true)
    setSaving(true)
    setError(null)
    try {
      const payload: SaleInput = {
        farmerId: farmer.id,
        billNumberTypeId: billTypeId,
        billNumber: composedBillNumber,
        transactionDate: txDate,
        items: lines.map((l) => ({
          itemId: l.itemId,
          quantity: parseFloat(l.quantity),
          price: parseFloat(l.price),
        })),
        remarks: remarks.trim() || undefined,
      }
      const mut = saleType === 'cash' ? createCash : createCredit
      const tx = await mut.mutateAsync(payload)
      setSavedTxId(tx.transactionNo)
      setSaving(false)
      setPhase('done')
    } catch (e: unknown) {
      setSaving(false)
      setSaveDisabled(false)
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Save failed. Please try again.'
      setError(msg)
    }
  }

  const handleReset = () => {
    setPhase('form')
    setFarmer(null)
    setCategoryId('')
    setBillTypeId('')
    setBillNumber('')
    setBillNumberAuto(true)
    setTxDate(today())
    setLines([emptyLine()])
    setRemarks('')
    setSavedTxId(null)
    setSaveDisabled(false)
    setError(null)
  }

  const title = saleType === 'cash' ? 'Cash Sale' : 'Credit Sale'

  // ── Done screen ───────────────────────────────────────────────────────────
  if (phase === 'done') {
    return (
      <div className="mx-auto max-w-lg rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
        <div className="mb-2 text-4xl">✓</div>
        <h2 className="mb-1 text-xl font-bold text-green-800">{title} Saved</h2>
        <p className="mb-1 text-sm text-green-700">
          Bill Number: <span className="font-semibold">{composedBillNumber}</span>
        </p>
        <p className="mb-6 text-xs text-green-600 break-all">Transaction No: {savedTxId}</p>
        <button
          onClick={handleReset}
          className="rounded-md bg-green-700 px-6 py-2 text-white hover:bg-green-800"
        >
          OK — New Entry
        </button>
      </div>
    )
  }

  // ── Review screen ─────────────────────────────────────────────────────────
  if (phase === 'review') {
    const resolvedItems = lines.map((l) => ({
      name: allItems.find((i) => i.id === l.itemId)?.name ?? l.itemId,
      qty: parseFloat(l.quantity),
      price: parseFloat(l.price),
      amount: parseFloat(l.quantity) * parseFloat(l.price),
    }))

    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <h1 className="text-2xl font-bold text-slate-800">{title} — Review</h1>

        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm space-y-3 text-sm">
          <Row label="Farmer" value={farmer?.name ?? ''} />
          <Row label="Father Name" value={farmer?.fatherName || '—'} />
          <Row label="Bill Id" value={composedBillNumber} />
          <Row label="Date" value={txDate} />
          <Row label="Remarks" value={remarks || '—'} />

          <hr className="my-2 border-slate-100" />

          <table className="w-full text-sm">
            <thead>
              <tr className="text-left text-slate-500">
                <th className="pb-1 font-medium">Item</th>
                <th className="pb-1 font-medium text-right">Qty</th>
                <th className="pb-1 font-medium text-right">Price</th>
                <th className="pb-1 font-medium text-right">Amount</th>
              </tr>
            </thead>
            <tbody>
              {resolvedItems.map((r, i) => (
                <tr key={i} className="border-t border-slate-100">
                  <td className="py-1">{r.name}</td>
                  <td className="py-1 text-right">{r.qty}</td>
                  <td className="py-1 text-right">₹{r.price.toFixed(2)}</td>
                  <td className="py-1 text-right">₹{r.amount.toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr className="border-t-2 border-slate-300 font-bold">
                <td colSpan={3} className="pt-2 text-right text-slate-700">
                  Grand Total
                </td>
                <td className="pt-2 text-right">₹{grandTotal.toFixed(2)}</td>
              </tr>
            </tfoot>
          </table>
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <div className="flex gap-3">
          <button
            onClick={() => setPhase('form')}
            className="rounded-md border border-slate-300 px-5 py-2 text-slate-700 hover:bg-slate-50"
          >
            ← Back
          </button>
          <button
            onClick={handleSave}
            disabled={saveDisabled}
            className="rounded-md bg-brand px-6 py-2 font-semibold text-white hover:opacity-90 disabled:opacity-50"
          >
            {saving ? 'Saving…' : 'Save'}
          </button>
        </div>
      </div>
    )
  }

  // ── Entry form ────────────────────────────────────────────────────────────
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">{title}</h1>

      {/* Farmer */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Farmer
        </h2>
        <FarmerSelector value={farmer} onChange={setFarmer} />
      </section>

      {/* Category + Bill Number Type */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
          Category &amp; Bill Number
        </h2>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Item Category <span className="text-red-500">*</span>
            </span>
            <select
              value={categoryId}
              onChange={(e) => {
                setCategoryId(e.target.value)
                setBillTypeId('')
                setBillNumber('')
              }}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            >
              <option value="">Select category…</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Bill Number Type <span className="text-red-500">*</span>
            </span>
            <select
              value={billTypeId}
              onChange={(e) => setBillTypeId(e.target.value)}
              disabled={!categoryId}
              className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:bg-slate-100"
            >
              <option value="">Select type…</option>
              {billTypes.map((bt) => (
                <option key={bt.id} value={bt.id}>
                  {bt.name}
                </option>
              ))}
            </select>
          </label>

          <label className="block sm:col-span-2">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Bill Id <span className="text-red-500">*</span>
            </span>
            <div className="flex gap-2">
              <input
                type="text"
                inputMode="numeric"
                value={billNumber}
                onChange={(e) => {
                  const digitsOnly = e.target.value.replace(/[^0-9]/g, '')
                  setBillNumber(digitsOnly)
                  setBillNumberAuto(false)
                }}
                disabled={!billTypeId}
                placeholder={billTypeId ? 'Bill Number' : 'Select bill id first'}
                className="flex-1 rounded-md border border-slate-300 px-3 py-2 disabled:bg-slate-100"
              />
              {/* <button
                type="button"
                onClick={() => {
                  setBillNumberAuto(true)
                  if (billTypeId) {
                    const bt = allBillTypes.find((b) => b.id === billTypeId)
                    if (bt) fetchBillNumberPreview(bt.itemCategoryId).then(setBillNumber).catch(() => {})
                  }
                }}
                className="rounded-md border border-slate-300 px-3 py-2 text-sm text-slate-600 hover:bg-slate-50"
              >
                Auto
              </button> */}
            </div>
            {composedBillNumber && (
              <p className="mt-1 text-xs text-slate-500">
                Will be saved as: <span className="font-mono font-medium">{composedBillNumber}</span>
              </p>
            )}
          </label>
        </div>

        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">
            Transaction Date <span className="text-red-500">*</span>
          </span>
          <input
            type="date"
            value={txDate}
            max={today()}
            onChange={(e) => setTxDate(e.target.value)}
            className="rounded-md border border-slate-300 px-3 py-2"
          />
        </label>
      </section>

      {/* Line Items */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Items
        </h2>

        <div className="space-y-3">
          {lines.map((line, idx) => (
            <LineRow
              key={idx}
              line={line}
              items={categoryItems}
              onItemChange={(id) => handleItemChange(idx, id)}
              onQtyChange={(q) => handleQtyChange(idx, q)}
              onPriceChange={(p) => handlePriceChange(idx, p)}
              onRemove={lines.length > 1 ? () => setLines((l) => l.filter((_, i) => i !== idx)) : undefined}
            />
          ))}
        </div>

        <button
          type="button"
          onClick={() => setLines((l) => [...l, emptyLine()])}
          disabled={!categoryId}
          className="mt-3 rounded-md border border-slate-300 px-4 py-1.5 text-sm text-slate-600 hover:bg-slate-50 disabled:opacity-50"
        >
          + Add Item
        </button>

        <div className="mt-4 border-t border-slate-100 pt-3 text-right text-base font-bold text-slate-800">
          Grand Total: ₹{grandTotal.toFixed(2)}
        </div>
      </section>

      {/* Remarks */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Remarks</span>
          <textarea
            value={remarks}
            onChange={(e) => setRemarks(e.target.value)}
            rows={2}
            className="w-full rounded-md border border-slate-300 px-3 py-2 text-sm"
            placeholder="Optional notes…"
          />
        </label>
      </section>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex justify-end">
        <button
          onClick={handleEnd}
          disabled={!isFormComplete}
          className="rounded-md bg-brand px-8 py-2.5 font-semibold text-white hover:opacity-90 disabled:opacity-40"
        >
          END →
        </button>
      </div>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-2">
      <span className="w-28 shrink-0 text-slate-500">{label}</span>
      <span className="font-medium text-slate-800">{value}</span>
    </div>
  )
}

interface LineRowProps {
  line: LineItem
  items: Item[]
  onItemChange: (id: string) => void
  onQtyChange: (q: string) => void
  onPriceChange: (p: string) => void
  onRemove?: () => void
}

function LineRow({ line, items, onItemChange, onQtyChange, onPriceChange: _onPriceChange, onRemove }: LineRowProps) {
  const amount = (parseFloat(line.quantity) || 0) * (parseFloat(line.price) || 0)
  const itemOptions = useMemo(() => items.map((i) => ({ id: i.id, label: i.name })), [items])

  return (
    <div className="flex flex-wrap items-end gap-2">
      <div className="flex-1 min-w-[160px]">
        <span className="mb-1 block text-xs font-medium text-slate-500">
          Item <span className="text-red-500">*</span>
        </span>
        <SearchSelect
          options={itemOptions}
          value={line.itemId}
          onChange={onItemChange}
          placeholder="Search item…"
          disabledPlaceholder="Select category first"
          disabled={items.length === 0}
        />
      </div>

      <div className="w-20">
        <span className="mb-1 block text-xs font-medium text-slate-500">
          Qty <span className="text-red-500">*</span>
        </span>
        <input
          type="number"
          min="0.001"
          step="any"
          value={line.quantity}
          onChange={(e) => onQtyChange(e.target.value)}
          className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
        />
      </div>

      {/* <div className="w-24">
        <span className="mb-1 block text-xs font-medium text-slate-500">Price (₹)</span>
        <input
          type="number"
          min="0"
          step="0.01"
          value={line.price}
          onChange={(e) => onPriceChange(e.target.value)}
          className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
        />
      </div> */}

      <div className="w-24 text-right">
        <span className="mb-1 block text-xs font-medium text-slate-500">Amount</span>
        <span className="block py-1.5 text-sm font-medium text-slate-700">
          ₹{amount.toFixed(2)}
        </span>
      </div>

      {onRemove && (
        <button
          type="button"
          onClick={onRemove}
          className="pb-1.5 text-slate-400 hover:text-red-500"
          title="Remove"
        >
          ✕
        </button>
      )}
    </div>
  )
}
