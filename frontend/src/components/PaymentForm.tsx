import { useState } from 'react'
import type { Farmer } from '../types'
import type { PaymentInput } from '../api/payments'
import { useCreatePayment, useCreateReceipt, useFarmerBalance } from '../api/payments'
import { useBillNumberTypes } from '../api/billNumberTypes'
import FarmerSelector from './FarmerSelector'

type Direction = 'payment' | 'receipt'
type Phase = 'form' | 'review' | 'done'

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

interface Props {
  direction: Direction
}

export default function PaymentForm({ direction }: Props) {
  const [phase, setPhase] = useState<Phase>('form')
  const [savedTxId, setSavedTxId] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saveDisabled, setSaveDisabled] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const [farmer, setFarmer] = useState<Farmer | null>(null)
  const [billTypeId, setBillTypeId] = useState('')
  const [billNumber, setBillNumber] = useState('')
  const [txDate, setTxDate] = useState(today)
  const [amount, setAmount] = useState('')
  const [remarks, setRemarks] = useState('')

  const { data: billTypes = [] } = useBillNumberTypes()
  const { data: balance, isLoading: balanceLoading } = useFarmerBalance(farmer?.id ?? null)

  const createPayment = useCreatePayment()
  const createReceipt = useCreateReceipt()

  const isFormComplete =
    farmer &&
    billTypeId &&
    billNumber.trim() &&
    txDate &&
    parseFloat(amount) > 0

  const title = direction === 'payment' ? 'Cash Payment' : 'Cash Receipt'

  const handleEnd = () => {
    if (!isFormComplete) {
      setError('Please complete all required fields.')
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
      const payload: PaymentInput = {
        farmerId: farmer.id,
        billNumberTypeId: billTypeId,
        billNumber: billNumber.trim(),
        transactionDate: txDate,
        amount: parseFloat(amount),
        remarks: remarks.trim() || undefined,
      }
      const mut = direction === 'payment' ? createPayment : createReceipt
      const tx = await mut.mutateAsync(payload)
      setSavedTxId(tx.id)
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
    setBillTypeId('')
    setBillNumber('')
    setTxDate(today())
    setAmount('')
    setRemarks('')
    setSavedTxId(null)
    setSaveDisabled(false)
    setError(null)
  }

  // ── Done ─────────────────────────────────────────────────────────────────
  if (phase === 'done') {
    return (
      <div className="mx-auto max-w-lg rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
        <div className="mb-2 text-4xl">✓</div>
        <h2 className="mb-1 text-xl font-bold text-green-800">{title} Saved</h2>
        <p className="mb-1 text-sm text-green-700">
          Bill Number: <span className="font-semibold">{billNumber}</span>
        </p>
        <p className="mb-6 text-xs text-green-600 break-all">Transaction ID: {savedTxId}</p>
        <button
          onClick={handleReset}
          className="rounded-md bg-green-700 px-6 py-2 text-white hover:bg-green-800"
        >
          OK — New Entry
        </button>
      </div>
    )
  }

  // ── Review ────────────────────────────────────────────────────────────────
  if (phase === 'review') {
    const billTypeName = billTypes.find((bt) => bt.id === billTypeId)?.name ?? billTypeId

    return (
      <div className="mx-auto max-w-2xl space-y-6">
        <h1 className="text-2xl font-bold text-slate-800">{title} — Review</h1>

        <div className="rounded-xl border border-slate-200 bg-white p-6 shadow-sm space-y-3 text-sm">
          <Row label="Farmer" value={`${farmer?.name}${farmer?.fatherName ? ' / ' + farmer.fatherName : ''}`} />
          <Row label="Bill Type" value={billTypeName} />
          <Row label="Bill Number" value={billNumber} />
          <Row label="Date" value={txDate} />
          <Row label="Remarks" value={remarks || '—'} />
          <hr className="border-slate-100" />
          <div className="flex justify-between text-base font-bold text-slate-800">
            <span>Amount</span>
            <span>₹{parseFloat(amount).toFixed(2)}</span>
          </div>
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
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">{title}</h1>

      {/* Farmer + Balance */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">Farmer</h2>
        <FarmerSelector onChange={setFarmer} />

        {farmer && (
          <div className="flex items-center justify-between rounded-lg bg-slate-50 border border-slate-200 px-4 py-3">
            <span className="text-sm text-slate-600">Outstanding Balance</span>
            {balanceLoading ? (
              <span className="text-sm text-slate-400">Loading…</span>
            ) : (
              <span
                className={`text-lg font-bold ${
                  (balance ?? 0) > 0
                    ? 'text-red-600'
                    : (balance ?? 0) < 0
                    ? 'text-green-600'
                    : 'text-slate-600'
                }`}
              >
                ₹{(balance ?? 0).toFixed(2)}
                {(balance ?? 0) > 0 && ' (owes)'}
                {(balance ?? 0) < 0 && ' (credit)'}
              </span>
            )}
          </div>
        )}
      </section>

      {/* Payment details */}
      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <h2 className="text-sm font-semibold uppercase tracking-wide text-slate-500">
          Payment Details
        </h2>

        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Bill Number Type <span className="text-red-500">*</span>
            </span>
            <select
              value={billTypeId}
              onChange={(e) => setBillTypeId(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            >
              <option value="">Select type…</option>
              {billTypes.map((bt) => (
                <option key={bt.id} value={bt.id}>
                  {bt.name}
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Bill Number <span className="text-red-500">*</span>
            </span>
            <input
              type="text"
              value={billNumber}
              onChange={(e) => setBillNumber(e.target.value)}
              placeholder="Enter bill number"
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Date <span className="text-red-500">*</span>
            </span>
            <input
              type="date"
              value={txDate}
              onChange={(e) => setTxDate(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Amount (₹) <span className="text-red-500">*</span>
            </span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="0.00"
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
        </div>

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
