import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import type { Farmer } from '../types'
import { usePayment, useUpdatePayment, useUpdateReceipt } from '../api/payments'
import { useFarmers } from '../api/farmers'
import FarmerSelector from '../components/FarmerSelector'
import { getErrorMessage } from '../lib/api'

interface Props {
  direction: 'payment' | 'receipt'
}

function today(): string {
  return new Date().toISOString().slice(0, 10)
}

/**
 * Admin-only correction screen for an existing payment/receipt. Bill number is
 * fixed (see backend PaymentUpdateRequest) — only farmer, date, amount, and
 * remarks can be corrected here.
 */
export default function PaymentEditPage({ direction }: Props) {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const { data: tx, isLoading, error: fetchError } = usePayment(direction, id ?? null)
  const { data: farmers = [] } = useFarmers({}, true)
  const updatePayment = useUpdatePayment()
  const updateReceipt = useUpdateReceipt()
  const update = direction === 'payment' ? updatePayment : updateReceipt

  const [farmer, setFarmer] = useState<Farmer | null>(null)
  const [txDate, setTxDate] = useState(today)
  const [amount, setAmount] = useState('')
  const [remarks, setRemarks] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    if (!tx) return
    setTxDate(tx.transactionDate)
    setAmount(String(tx.grandTotal))
    setRemarks(tx.remarks ?? '')
  }, [tx])

  useEffect(() => {
    if (!tx || farmer) return
    const match = farmers.find((f) => f.id === tx.farmerId)
    if (match) setFarmer(match)
  }, [tx, farmers, farmer])

  const isComplete = farmer && txDate && txDate <= today() && parseFloat(amount) > 0

  const handleSave = async () => {
    if (!farmer || !isComplete) return
    setError(null)
    try {
      await update.mutateAsync({
        id: id!,
        input: {
          farmerId: farmer.id,
          transactionDate: txDate,
          amount: parseFloat(amount),
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
    return <p className="text-sm text-red-600">{getErrorMessage(fetchError, 'Transaction not found.')}</p>
  }
  if (!tx) return null

  const title = direction === 'payment' ? 'Payment' : 'Receipt'

  if (saved) {
    return (
      <div className="mx-auto max-w-lg rounded-xl border border-green-200 bg-green-50 p-8 text-center shadow">
        <div className="mb-2 text-4xl">✓</div>
        <h2 className="mb-1 text-xl font-bold text-green-800">{title} Updated</h2>
        <p className="mb-6 text-sm text-green-700">
          Bill Number: <span className="font-semibold">{tx.billNumber}</span>
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
        Edit {title}
        <span className="ml-2 font-mono text-base text-slate-400">{tx.billNumber}</span>
      </h1>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500">Farmer</h2>
        <FarmerSelector value={farmer} onChange={setFarmer} />
      </section>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm space-y-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Date</span>
            <input
              type="date"
              value={txDate}
              max={today()}
              onChange={(e) => setTxDate(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Amount (₹)</span>
            <input
              type="number"
              min="0.01"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
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
