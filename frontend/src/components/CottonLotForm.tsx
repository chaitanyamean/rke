import { useEffect, useMemo, useState } from 'react'
import { useVillages } from '../api/villages'
import { useFarmers } from '../api/farmers'
import { useSerialPreview, useCreateCottonLot } from '../api/cotton'
import type { CottonLotEntryInput } from '../api/cotton'
import type { Village } from '../types'

type Phase = 'form' | 'done'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

// ─── per-row state ──────────────────────────────────────────────────────────

interface RowState {
  key: string
  villageId: string
  farmerId: string
  quantity: string
  price: string
}

function makeRow(commonPrice: string): RowState {
  return { key: crypto.randomUUID(), villageId: '', farmerId: '', quantity: '', price: commonPrice }
}

// ─── Entry row sub-component ────────────────────────────────────────────────

interface EntryRowProps {
  row: RowState
  villages: Village[]
  commonPrice: string
  onChange: (patch: Partial<RowState>) => void
  onRemove: () => void
  canRemove: boolean
}

function EntryRow({ row, villages, commonPrice, onChange, onRemove, canRemove }: EntryRowProps) {
  const { data: farmers = [], isLoading } = useFarmers({ villageId: row.villageId }, !!row.villageId)

  const qty = parseFloat(row.quantity)
  const price = parseFloat(row.price)
  const amount = !isNaN(qty) && !isNaN(price) ? qty * price : null

  const handleVillage = (villageId: string) => {
    onChange({ villageId, farmerId: '' })
  }

  // Sync price to new commonPrice only while price field is still at default.
  useEffect(() => {
    if (row.price === '' || row.price === commonPrice) {
      onChange({ price: commonPrice })
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [commonPrice])

  return (
    <tr className="border-b border-slate-100 last:border-0">
      <td className="py-2 pr-2">
        <select
          value={row.villageId}
          onChange={(e) => handleVillage(e.target.value)}
          className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
        >
          <option value="">Village…</option>
          {villages.map((v) => (
            <option key={v.id} value={v.id}>
              {v.name}
            </option>
          ))}
        </select>
      </td>

      <td className="py-2 pr-2">
        <select
          value={row.farmerId}
          onChange={(e) => onChange({ farmerId: e.target.value })}
          disabled={!row.villageId || isLoading}
          className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm disabled:bg-slate-100"
        >
          <option value="">{row.villageId ? 'Farmer…' : '—'}</option>
          {farmers.map((f) => (
            <option key={f.id} value={f.id}>
              {f.name}{f.fatherName ? ` (${f.fatherName})` : ''}
            </option>
          ))}
        </select>
      </td>

      <td className="py-2 pr-2 w-28">
        <input
          type="number"
          min="0.001"
          step="any"
          value={row.quantity}
          onChange={(e) => onChange({ quantity: e.target.value })}
          placeholder="Qty"
          className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
        />
      </td>

      <td className="py-2 pr-2 w-28">
        <input
          type="number"
          min="0"
          step="any"
          value={row.price}
          onChange={(e) => onChange({ price: e.target.value })}
          placeholder="Price"
          className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
        />
      </td>

      <td className="py-2 pr-2 text-right text-sm font-medium text-slate-700 w-28">
        {amount !== null ? `₹${amount.toFixed(2)}` : '—'}
      </td>

      <td className="py-2 text-center w-8">
        {canRemove && (
          <button
            type="button"
            onClick={onRemove}
            className="text-slate-400 hover:text-red-500 text-lg leading-none"
            aria-label="Remove row"
          >
            ×
          </button>
        )}
      </td>
    </tr>
  )
}

// ─── Main form ───────────────────────────────────────────────────────────────

export default function CottonLotForm() {
  const [phase, setPhase] = useState<Phase>('form')
  const [savedSerial, setSavedSerial] = useState<string | null>(null)
  const [savedId, setSavedId] = useState<string | null>(null)

  // Header fields
  const [vehicleReg, setVehicleReg] = useState('')
  const [mutaHamali, setMutaHamali] = useState('')
  const [commonPrice, setCommonPrice] = useState('')
  const [lotDate, setLotDate] = useState(today)

  // Entry rows
  const [rows, setRows] = useState<RowState[]>([makeRow('')])

  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const { data: serialPreview } = useSerialPreview()
  const { data: villages = [] } = useVillages()
  const createLot = useCreateCottonLot()

  const updateRow = (key: string, patch: Partial<RowState>) => {
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)))
  }

  const removeRow = (key: string) => {
    setRows((prev) => prev.filter((r) => r.key !== key))
  }

  const addRow = () => {
    setRows((prev) => [...prev, makeRow(commonPrice)])
  }

  // Totals
  const { totalQty, totalAmount } = useMemo(() => {
    let q = 0
    let a = 0
    for (const row of rows) {
      const qty = parseFloat(row.quantity)
      const price = parseFloat(row.price)
      if (!isNaN(qty) && !isNaN(price)) {
        q += qty
        a += qty * price
      }
    }
    return { totalQty: q, totalAmount: a }
  }, [rows])

  // Validation
  const rowsValid = rows.length > 0 && rows.every((r) => {
    const qty = parseFloat(r.quantity)
    const price = parseFloat(r.price)
    return r.villageId && r.farmerId && qty > 0 && price >= 0
  })
  const commonPriceValid = !isNaN(parseFloat(commonPrice)) && parseFloat(commonPrice) >= 0
  const canSave = rowsValid && commonPriceValid && !!lotDate && !saving

  const handleSave = async () => {
    if (!canSave) return
    setSaving(true)
    setError(null)
    try {
      const entries: CottonLotEntryInput[] = rows.map((r) => ({
        farmerId: r.farmerId,
        villageId: r.villageId,
        quantity: parseFloat(r.quantity),
        price: parseFloat(r.price),
      }))
      const lot = await createLot.mutateAsync({
        vehicleRegistrationNumber: vehicleReg.trim() || undefined,
        mutaHamaliName: mutaHamali.trim() || undefined,
        commonPrice: parseFloat(commonPrice),
        lotDate,
        entries,
      })
      setSavedSerial(lot.vehicleSerialNumber)
      setSavedId(lot.id)
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
    setVehicleReg('')
    setMutaHamali('')
    setCommonPrice('')
    setLotDate(today())
    setRows([makeRow('')])
    setSavedSerial(null)
    setSavedId(null)
    setError(null)
  }

  // ── Done ──────────────────────────────────────────────────────────────────
  if (phase === 'done') {
    return (
      <div className="mx-auto max-w-lg rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
        <div className="mb-2 text-4xl">✓</div>
        <h2 className="mb-1 text-xl font-bold text-green-800">Cotton Lot Saved</h2>
        <p className="mb-1 text-sm text-green-700">
          Vehicle Serial: <span className="font-semibold">{savedSerial}</span>
        </p>
        <p className="mb-6 text-xs text-green-600 break-all">Lot ID: {savedId}</p>
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
    <div className="mx-auto max-w-5xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">Cotton Lot Entry</h1>

      {/* Lot Header */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Lot Details
        </h2>

        {/* Auto-generated serial */}
        <div className="mb-4 flex items-center gap-3 rounded-lg border border-slate-200 bg-slate-50 px-4 py-3">
          <span className="text-sm text-slate-500">Vehicle Serial Number</span>
          <span className="ml-auto font-mono text-lg font-bold text-slate-800">
            {serialPreview ?? '—'}
          </span>
          <span className="rounded bg-slate-200 px-1.5 py-0.5 text-xs text-slate-600">auto</span>
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Vehicle Registration Number
            </span>
            <input
              type="text"
              value={vehicleReg}
              onChange={(e) => setVehicleReg(e.target.value)}
              placeholder="e.g. AP 39 AB 1234"
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Muta Hamali Name</span>
            <input
              type="text"
              value={mutaHamali}
              onChange={(e) => setMutaHamali(e.target.value)}
              placeholder="Name"
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Common Price (₹ / quintal)
            </span>
            <input
              type="number"
              min="0"
              step="any"
              value={commonPrice}
              onChange={(e) => setCommonPrice(e.target.value)}
              placeholder="0.00"
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Lot Date</span>
            <input
              type="date"
              value={lotDate}
              onChange={(e) => setLotDate(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
        </div>
      </section>

      {/* Cotton Entries */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">
          Cotton Entries
        </h2>

        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px]">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="pb-2 pr-2">Village</th>
                <th className="pb-2 pr-2">Farmer</th>
                <th className="pb-2 pr-2">Quantity</th>
                <th className="pb-2 pr-2">Price (₹)</th>
                <th className="pb-2 pr-2 text-right">Amount</th>
                <th className="pb-2 w-8" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <EntryRow
                  key={row.key}
                  row={row}
                  villages={villages}
                  commonPrice={commonPrice}
                  onChange={(patch) => updateRow(row.key, patch)}
                  onRemove={() => removeRow(row.key)}
                  canRemove={rows.length > 1}
                />
              ))}
            </tbody>
          </table>
        </div>

        <button
          type="button"
          onClick={addRow}
          className="mt-3 rounded-md border border-dashed border-slate-400 px-4 py-2 text-sm text-slate-600 hover:border-slate-600 hover:text-slate-800"
        >
          + Add Cotton
        </button>

        {/* Running totals */}
        <div className="mt-4 flex justify-end gap-6 border-t border-slate-100 pt-3 text-sm">
          <span className="text-slate-500">
            Total Qty: <span className="font-semibold text-slate-800">{totalQty.toFixed(3)}</span>
          </span>
          <span className="text-slate-500">
            Total Amount:{' '}
            <span className="font-semibold text-slate-800">₹{totalAmount.toFixed(2)}</span>
          </span>
        </div>
      </section>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex justify-end">
        <button
          onClick={handleSave}
          disabled={!canSave}
          className="rounded-md bg-brand px-8 py-2.5 font-semibold text-white hover:opacity-90 disabled:opacity-40"
        >
          {saving ? 'Saving…' : 'Save Lot'}
        </button>
      </div>
    </div>
  )
}
