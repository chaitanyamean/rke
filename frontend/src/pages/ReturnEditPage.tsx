import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useReturn, useUpdateReturn, type ReturnLineItemInput } from '../api/returns'
import { useItems } from '../api/items'
import { getErrorMessage } from '../lib/api'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

interface LineDraft {
  itemId: string
  quantity: string
}

/**
 * Admin-only correction screen for an existing return. Original bill number
 * and farmer are fixed (see backend ReturnUpdateRequest) — only the return
 * date, line items, and remarks can be corrected here.
 */
export default function ReturnEditPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: tx, isLoading, error: fetchError } = useReturn(id ?? null)
  const { data: allItems = [] } = useItems()
  const update = useUpdateReturn()

  const [returnDate, setReturnDate] = useState(today)
  const [lines, setLines] = useState<LineDraft[]>([])
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (!tx) return
    setReturnDate(tx.transactionDate)
    setLines(tx.items.map((i) => ({ itemId: i.itemId, quantity: String(i.quantity) })))
    setRemarks(tx.remarks ?? '')
  }, [tx])

  const itemNameMap = useMemo(() => new Map(allItems.map((i) => [i.id, i.name])), [allItems])

  const isComplete =
    returnDate &&
    returnDate <= today() &&
    lines.length > 0 &&
    lines.every((l) => l.itemId && parseFloat(l.quantity) > 0)

  const handleSave = async () => {
    if (!isComplete) return
    setError(null)
    try {
      const items: ReturnLineItemInput[] = lines.map((l) => ({
        itemId: l.itemId,
        quantity: parseFloat(l.quantity),
      }))
      await update.mutateAsync({
        id: id!,
        input: {
          returnDate,
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
    return <p className="text-sm text-red-600">{getErrorMessage(fetchError, 'Return not found.')}</p>
  }
  if (!tx) return null

  if (saved) {
    return (
      <div className="mx-auto max-w-lg rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
        <div className="mb-2 text-4xl">✓</div>
        <h2 className="mb-1 text-xl font-bold text-green-800">Return Updated</h2>
        <p className="mb-6 text-sm text-green-700">
          Return Bill: <span className="font-semibold">{tx.billNumber}</span>
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
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">
        Edit Return
        <span className="ml-2 font-mono text-base text-slate-400">{tx.billNumber}</span>
      </h1>
      {tx.originalBillNumber && (
        <p className="text-sm text-slate-500">
          Against original bill:{' '}
          <span className="font-mono font-medium text-slate-700">{tx.originalBillNumber}</span>
        </p>
      )}

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

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Returned Items
        </h2>
        <div className="space-y-2">
          {lines.map((line, idx) => (
            <div key={idx} className="flex flex-wrap items-center gap-3 rounded-md border border-slate-200 px-3 py-2">
              <span className="flex-1 text-sm font-medium text-slate-800">
                {itemNameMap.get(line.itemId) ?? line.itemId}
              </span>
              <div className="flex items-center gap-1">
                <span className="text-xs text-slate-500">Return qty:</span>
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
                  className="w-24 rounded border border-slate-300 px-2 py-1 text-sm"
                />
              </div>
            </div>
          ))}
        </div>
        <p className="mt-3 text-xs text-slate-400">
          Quantities are still capped server-side by how much of the original
          sale remains returnable — the save will fail with a clear error if a
          quantity is increased beyond what's available.
        </p>
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
