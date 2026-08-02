import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useCottonLot, useUpdateCottonLot } from '../api/cotton'
import type { CottonLotEntryInput } from '../api/cotton'
import { useVillages } from '../api/villages'
import { useFarmers } from '../api/farmers'
import type { Village } from '../types'
import { getErrorMessage } from '../lib/api'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

interface RowState {
  key: string
  villageId: string
  farmerId: string
  quantity: string
  price: string
}

interface EntryRowProps {
  row: RowState
  villages: Village[]
  onChange: (patch: Partial<RowState>) => void
  onRemove: () => void
  canRemove: boolean
}

function EntryRow({ row, villages, onChange, onRemove, canRemove }: EntryRowProps) {
  const { data: farmers = [], isLoading } = useFarmers({ villageId: row.villageId }, !!row.villageId)

  const qty = parseFloat(row.quantity)
  const price = parseFloat(row.price)
  const amount = !isNaN(qty) && !isNaN(price) ? qty * price : null

  return (
    <tr className="border-b border-slate-100 last:border-0">
      <td className="py-2 pr-2">
        <select
          value={row.villageId}
          onChange={(e) => onChange({ villageId: e.target.value, farmerId: '' })}
          className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
        >
          <option value="">Village…</option>
          {villages.map((v) => <option key={v.id} value={v.id}>{v.name}</option>)}
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
          type="text"
          value={row.quantity}
          onChange={(e) => {
            const val = e.target.value
            if (val === '' || /^\d+$/.test(val)) onChange({ quantity: val })
          }}
          placeholder="Qty"
          className="w-full rounded border border-slate-300 px-2 py-1.5 text-sm"
        />
      </td>
      <td className="py-2 pr-2 w-28">
        <input
          type="text"
          value={row.price}
          onChange={(e) => onChange({ price: e.target.value })}
          placeholder="Price"
          className={`w-full rounded border px-2 py-1.5 text-sm ${
            row.price && isNaN(parseFloat(row.price)) ? 'border-red-400' : 'border-slate-300'
          }`}
        />
      </td>
      <td className="py-2 pr-2 text-right text-sm font-medium text-slate-700 w-28">
        {amount !== null ? `₹${amount.toFixed(2)}` : '—'}
      </td>
      <td className="py-2 text-center w-8">
        {canRemove && (
          <button type="button" onClick={onRemove}
            className="text-slate-400 hover:text-red-500 text-lg leading-none">×</button>
        )}
      </td>
    </tr>
  )
}

export default function CottonLotEditPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: lot, isLoading, error: fetchError } = useCottonLot(id ?? null)
  const { data: villages = [] } = useVillages()
  const update = useUpdateCottonLot()

  const [vehicleReg, setVehicleReg] = useState('')
  const [mutaHamali, setMutaHamali] = useState('')
  const [commonPrice, setCommonPrice] = useState('')
  const [lotDate, setLotDate] = useState(today)
  const [rows, setRows] = useState<RowState[]>([])
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  // Prefill when lot loads
  useEffect(() => {
    if (!lot) return
    setVehicleReg(lot.vehicleRegistrationNumber ?? '')
    setMutaHamali(lot.mutaHamaliName ?? '')
    setCommonPrice(String(lot.commonPrice))
    setLotDate(lot.lotDate)
    setRows(lot.entries.map((e) => ({
      key: e.id,
      villageId: e.villageId,
      farmerId: e.farmerId,
      quantity: String(e.quantity),
      price: String(e.price),
    })))
  }, [lot])

  const updateRow = (key: string, patch: Partial<RowState>) =>
    setRows((prev) => prev.map((r) => (r.key === key ? { ...r, ...patch } : r)))

  const { totalQty, totalAmount } = useMemo(() => {
    let q = 0, a = 0
    for (const row of rows) {
      const qty = parseFloat(row.quantity)
      const price = parseFloat(row.price)
      if (!isNaN(qty) && !isNaN(price)) { q += qty; a += qty * price }
    }
    return { totalQty: q, totalAmount: a }
  }, [rows])

  const rowsValid = rows.length > 0 && rows.every((r) => {
    const qty = parseFloat(r.quantity)
    const price = parseFloat(r.price)
    return r.villageId && r.farmerId && qty > 0 && price >= 0
  })
  const commonPriceValid = !isNaN(parseFloat(commonPrice)) && parseFloat(commonPrice) >= 0
  const canSave = rowsValid && commonPriceValid && !!lotDate && lotDate <= today() && !update.isPending

  const handleSave = async () => {
    if (!canSave) return
    setError(null)
    try {
      const entries: CottonLotEntryInput[] = rows.map((r) => ({
        farmerId: r.farmerId,
        villageId: r.villageId,
        quantity: parseFloat(r.quantity),
        price: parseFloat(r.price),
      }))
      await update.mutateAsync({
        id: id!,
        input: {
          vehicleRegistrationNumber: vehicleReg.trim() || undefined,
          mutaHamaliName: mutaHamali.trim() || undefined,
          commonPrice: parseFloat(commonPrice),
          lotDate,
          entries,
        },
      })
      setSaved(true)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  if (isLoading) return <p className="text-slate-400">Loading…</p>
  if (fetchError) return <p className="text-sm text-red-600">{getErrorMessage(fetchError, 'Cotton lot not found.')}</p>
  if (!lot) return null

  if (saved) {
    return (
      <div className="mx-auto max-w-lg rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
        <div className="mb-2 text-4xl">✓</div>
        <h2 className="mb-1 text-xl font-bold text-green-800">Cotton Lot Updated</h2>
        <p className="mb-6 text-sm text-green-700">
          Serial: <span className="font-semibold">{lot.vehicleSerialNumber}</span>
        </p>
        <button onClick={() => navigate(-1)}
          className="rounded-md bg-green-700 px-6 py-2 text-white hover:bg-green-800">Back</button>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">
        Edit Cotton Lot
        <span className="ml-2 font-mono text-base text-slate-400">{lot.vehicleSerialNumber}</span>
      </h1>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Lot Details</h2>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Vehicle Registration Number</span>
            <input type="text" value={vehicleReg} onChange={(e) => setVehicleReg(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Muta Hamali Name</span>
            <input type="text" value={mutaHamali} onChange={(e) => setMutaHamali(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Common Price (₹ / kgs)</span>
            <input type="text" value={commonPrice} onChange={(e) => setCommonPrice(e.target.value)}
              className={`w-full rounded-md border px-3 py-2 ${
                commonPrice && isNaN(parseFloat(commonPrice)) ? 'border-red-400' : 'border-slate-300'
              }`} />
            {commonPrice && isNaN(parseFloat(commonPrice)) && (
              <p className="mt-1 text-xs text-red-600">Please enter a valid number</p>
            )}
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Lot Date</span>
            <input type="date" value={lotDate} max={today()} onChange={(e) => setLotDate(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
        </div>
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Cotton Entries</h2>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px]">
            <thead>
              <tr className="border-b border-slate-200 text-left text-xs font-semibold uppercase tracking-wide text-slate-500">
                <th className="pb-2 pr-2">Village</th>
                <th className="pb-2 pr-2">Farmer</th>
                <th className="pb-2 pr-2">Qty (Kgs)</th>
                <th className="pb-2 pr-2">Price (₹/kg)</th>
                <th className="pb-2 pr-2 text-right">Amount</th>
                <th className="pb-2 w-8" />
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <EntryRow key={row.key} row={row} villages={villages}
                  onChange={(patch) => updateRow(row.key, patch)}
                  onRemove={() => setRows((r) => r.filter((x) => x.key !== row.key))}
                  canRemove={rows.length > 0} />
              ))}
            </tbody>
          </table>
        </div>
        <button type="button"
          onClick={() => setRows((r) => [...r, { key: crypto.randomUUID(), villageId: '', farmerId: '', quantity: '', price: commonPrice }])}
          className="mt-3 rounded-md border border-dashed border-slate-400 px-4 py-2 text-sm text-slate-600 hover:border-slate-600">
          + Add Cotton
        </button>
        <div className="mt-4 flex justify-end gap-6 border-t border-slate-100 pt-3 text-sm">
          <span className="text-slate-500">Total Qty: <span className="font-semibold text-slate-800">{totalQty.toFixed(3)}</span></span>
          <span className="text-slate-500">Total Amount: <span className="font-semibold text-slate-800">₹{totalAmount.toFixed(2)}</span></span>
        </div>
      </section>

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex justify-end gap-3">
        <button type="button" onClick={() => navigate(-1)}
          className="rounded-md border border-slate-300 px-5 py-2.5 text-slate-700 hover:bg-slate-50">Cancel</button>
        <button onClick={handleSave} disabled={!canSave}
          className="rounded-md bg-brand px-8 py-2.5 font-semibold text-white hover:opacity-90 disabled:opacity-40">
          {update.isPending ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
    </div>
  )
}
