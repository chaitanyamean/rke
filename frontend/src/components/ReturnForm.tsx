import { useEffect, useState } from 'react'
import type { Farmer } from '../types'
import { useOriginalTransaction, useCreateReturn } from '../api/returns'
import { useItems } from '../api/items'
// import { useItemCategories } from '../api/itemCategories'
import FarmerSelector from './FarmerSelector'

type Phase = 'form' | 'done'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

export default function ReturnForm() {
  const [phase, setPhase] = useState<Phase>('form')
  const [savedTxId, setSavedTxId] = useState<string | null>(null)
  const [savedReturnBill, setSavedReturnBill] = useState<string | null>(null)

  // Step 1: Farmer
  const [farmer, setFarmer] = useState<Farmer | null>(null)

  // Step 2: Item Category (for UX context only — not used to filter the fetch)
  //const [categoryId, setCategoryId] = useState('')

  // Step 3: Bill number lookup
  const [billInput, setBillInput] = useState('')
  const [queryBill, setQueryBill] = useState<string | null>(null)
  const [farmerMismatch, setFarmerMismatch] = useState(false)

  // Step 4+: Return date, item selection
  const [returnDate, setReturnDate] = useState(today)
  const [checkedIds, setCheckedIds] = useState<Set<string>>(new Set())
  const [returnQtys, setReturnQtys] = useState<Map<string, string>>(new Map())

  const [remarks, setRemarks] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // const { data: categories = [] } = useItemCategories()
  const { data: allItems = [] } = useItems()
  const {
    data: originalTx,
    isLoading: fetching,
    error: fetchErr,
    isError: isFetchErr,
  } = useOriginalTransaction(queryBill)

  const createReturn = useCreateReturn()

  // Build item name lookup map
  const itemNameMap = new Map(allItems.map((i) => [i.id, i.name]))

  // When original transaction arrives, validate farmer + seed qty state
  useEffect(() => {
    if (!originalTx) return

    if (farmer && originalTx.farmerId !== farmer.id) {
      setFarmerMismatch(true)
      setQueryBill(null)
      return
    }

    setFarmerMismatch(false)
    setCheckedIds(new Set())
    setReturnQtys(
      new Map(originalTx.items.map((item) => [item.itemId, String(item.returnableQuantity)])),
    )
  }, [originalTx, farmer])

  const handleFetch = () => {
    const b = billInput.trim()
    if (!b) return
    setFarmerMismatch(false)
    setCheckedIds(new Set())
    setQueryBill(b)
  }

  const toggleItem = (itemId: string) => {
    setCheckedIds((prev) => {
      const next = new Set(prev)
      if (next.has(itemId)) next.delete(itemId)
      else next.add(itemId)
      return next
    })
  }

  const handleQty = (itemId: string, value: string) => {
    setReturnQtys((prev) => new Map(prev).set(itemId, value))
  }

  // Validation
  const validLines = originalTx
    ? Array.from(checkedIds).map((id) => {
        const orig = originalTx.items.find((i) => i.itemId === id)
        const qty = parseFloat(returnQtys.get(id) ?? '0')
        const cap = orig?.returnableQuantity ?? 0
        return { id, qty, origQty: cap, valid: qty > 0 && qty <= cap }
      })
    : []
  const allLinesValid = validLines.length > 0 && validLines.every((l) => l.valid)

  const handleSave = async () => {
    if (!farmer || !originalTx || !allLinesValid) return
    if (returnDate > today()) {
      setError('Return date cannot be in the future.')
      return
    }
    setSaving(true)
    setError(null)
    try {
      const tx = await createReturn.mutateAsync({
        farmerId: farmer.id,
        originalBillNumber: queryBill!,
        returnDate,
        items: validLines.map((l) => ({ itemId: l.id, quantity: l.qty })),
        remarks: remarks.trim() || undefined,
      })
      setSavedTxId(tx.transactionNo)
      setSavedReturnBill(tx.billNumber)
      setPhase('done')
    } catch (e: unknown) {
      const msg =
        (e as { response?: { data?: { message?: string } } })?.response?.data?.message ??
        'Save failed. Please try again.'
      setError(msg)
    } finally {
      setSaving(false)
    }
  }

  const handleReset = () => {
    setPhase('form')
    setFarmer(null)
    // setCategoryId('')
    setBillInput('')
    setQueryBill(null)
    setReturnDate(today())
    setCheckedIds(new Set())
    setReturnQtys(new Map())
    setRemarks('')
    setSavedTxId(null)
    setSavedReturnBill(null)
    setFarmerMismatch(false)
    setError(null)
  }

  // ── Done ─────────────────────────────────────────────────────────────────
  if (phase === 'done') {
    return (
      <div className="mx-auto max-w-lg rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
        <div className="mb-2 text-4xl">✓</div>
        <h2 className="mb-1 text-xl font-bold text-green-800">Return Recorded</h2>
        <p className="mb-1 text-sm text-green-700">
          Return Bill: <span className="font-semibold">{savedReturnBill}</span>
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

  // ── Form ──────────────────────────────────────────────────────────────────
  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">Return</h1>

      {/* Farmer */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Farmer
        </h2>
        <FarmerSelector
          allowAddFarmer={false}
          onChange={(f) => { setFarmer(f); setQueryBill(null); setBillInput(''); setFarmerMismatch(false) }}
        />
      </section>

      {/* Item Category */}
      {/* <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Item Category</span>
          <select
            value={categoryId}
            onChange={(e) => setCategoryId(e.target.value)}
            disabled={!farmer}
            className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:bg-slate-100"
          >
            <option value="">Select category…</option>
            {categories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.name}
              </option>
            ))}
          </select>
        </label>
      </section> */}

      {/* Bill Number Lookup */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
          Original Bill
        </h2>

        <div className="flex gap-2">
          <input
            type="text"
            value={billInput}
            onChange={(e) => { setBillInput(e.target.value); setQueryBill(null) }}
            onKeyDown={(e) => e.key === 'Enter' && handleFetch()}
            placeholder="Enter original bill number…"
            disabled={!farmer}
            className="flex-1 rounded-md border border-slate-300 px-3 py-2 disabled:bg-slate-100"
          />
          <button
            onClick={handleFetch}
            disabled={!farmer || !billInput.trim() || fetching}
            className="rounded-md bg-slate-700 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-50"
          >
            {fetching ? 'Fetching…' : 'Fetch'}
          </button>
        </div>

        {isFetchErr && !farmerMismatch && (
          <p className="text-sm text-red-600">
            {(fetchErr as { response?: { data?: { message?: string } } })?.response?.data?.message ??
              'Bill number not found or not returnable.'}
          </p>
        )}

        {farmerMismatch && (
          <p className="text-sm text-red-600">
            This bill does not belong to the selected farmer.
          </p>
        )}

        {/* Original transaction summary + item selection */}
        {originalTx && !farmerMismatch && (
          <div className="space-y-3 rounded-lg border border-slate-100 bg-slate-50 p-4">
            <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-slate-600">
              <span>
                Type:{' '}
                <span className="font-medium capitalize text-slate-800">
                  {originalTx.transactionType.replace('_', ' ').toLowerCase()}
                </span>
              </span>
              <span>
                Date: <span className="font-medium text-slate-800">{originalTx.transactionDate}</span>
              </span>
              <span>
                Total:{' '}
                <span className="font-medium text-slate-800">
                  ₹{originalTx.grandTotal.toFixed(2)}
                </span>
              </span>
            </div>

            <p className="text-xs font-semibold uppercase tracking-wide text-slate-500">
              Select items to return
            </p>

            <div className="space-y-2">
              {originalTx.items.map((item) => {
                const checked = checkedIds.has(item.itemId)
                const qtyStr = returnQtys.get(item.itemId) ?? ''
                const qty = parseFloat(qtyStr)
                const cap = item.returnableQuantity
                const qtyError = checked && (isNaN(qty) || qty <= 0 || qty > cap)
                const fullyReturned = cap <= 0

                return (
                  <div
                    key={item.itemId}
                    className={`flex flex-wrap items-center gap-3 rounded-md border px-3 py-2 ${
                      checked ? 'border-slate-300 bg-white' : 'border-transparent'
                    }`}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleItem(item.itemId)}
                      disabled={fullyReturned}
                      className="h-4 w-4 accent-slate-700 disabled:opacity-40"
                    />

                    <span className="flex-1 text-sm font-medium text-slate-800">
                      {itemNameMap.get(item.itemId) ?? item.itemId}
                    </span>

                    <span className="text-xs text-slate-500">
                      Sold: {item.quantity} × ₹{item.price.toFixed(2)}
                      {item.alreadyReturnedQuantity > 0 && (
                        <> · Already returned: {item.alreadyReturnedQuantity}</>
                      )}
                    </span>

                    <div className="flex items-center gap-1">
                      <span className="text-xs text-slate-500">Return qty:</span>
                      <input
                        type="text"
                        value={qtyStr}
                        onChange={(e) => {
                          const val = e.target.value
                          if (val === '' || /^\d+$/.test(val)) handleQty(item.itemId, val)
                        }}
                        disabled={!checked || fullyReturned}
                        className={`w-20 rounded border px-2 py-1 text-sm disabled:bg-slate-50 ${
                          (qtyError || (qtyStr && isNaN(parseFloat(qtyStr)))) ? 'border-red-400' : 'border-slate-300'
                        }`}
                      />
                    </div>

                    {fullyReturned ? (
                      <span className="text-xs text-slate-400">Fully returned</span>
                    ) : qtyStr && isNaN(parseFloat(qtyStr)) ? (
                      <span className="text-xs text-red-500">Enter a number</span>
                    ) : (
                      qtyError && <span className="text-xs text-red-500">1–{cap}</span>
                    )}
                  </div>
                )
              })}
            </div>
          </div>
        )}
      </section>

      {/* Return Date */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Return Date</span>
          <input
            type="date"
            value={returnDate}
            max={today()}
            onChange={(e) => setReturnDate(e.target.value)}
            className="rounded-md border border-slate-300 px-3 py-2"
          />
        </label>
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
          onClick={handleSave}
          disabled={!allLinesValid || saving}
          className="rounded-md bg-brand px-8 py-2.5 font-semibold text-white hover:opacity-90 disabled:opacity-40"
        >
          {saving ? 'Saving…' : 'Save Return'}
        </button>
      </div>
    </div>
  )
}
