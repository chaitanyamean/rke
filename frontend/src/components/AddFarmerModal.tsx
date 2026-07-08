import { useState, type FormEvent } from 'react'
import { useVillages } from '../api/villages'
import { useCreateFarmer } from '../api/farmers'
import { getErrorMessage } from '../lib/api'
import type { Farmer } from '../types'

interface AddFarmerModalProps {
  /** Pre-selected village (from the parent selector), if any. Still editable. */
  initialVillageId?: string
  onCreated: (farmer: Farmer) => void
  onClose: () => void
}

/**
 * Small modal for registering a farmer without leaving the current screen
 * (e.g. mid-sale, when the farmer doesn't exist yet). Mirrors the fields on
 * the full Farmer Registration page; village is still a real village-master
 * selection, not free text.
 */
export default function AddFarmerModal({ initialVillageId, onCreated, onClose }: AddFarmerModalProps) {
  const { data: villages = [] } = useVillages()
  const create = useCreateFarmer()

  const [name, setName] = useState('')
  const [fatherName, setFatherName] = useState('')
  const [villageId, setVillageId] = useState(initialVillageId ?? '')
  const [address, setAddress] = useState('')
  const [mobileNumber, setMobileNumber] = useState('')
  const [reference, setReference] = useState('')
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!name.trim()) {
      setError('Farmer name is required')
      return
    }
    if (!villageId) {
      setError('Village is required')
      return
    }
    try {
      const farmer = await create.mutateAsync({
        name: name.trim(),
        fatherName: fatherName.trim() || undefined,
        villageId,
        address: address.trim() || undefined,
        mobileNumber: mobileNumber.trim() || undefined,
        reference: reference.trim() || undefined,
      })
      onCreated(farmer)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-slate-800">New Farmer</h3>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="text-slate-400 hover:text-slate-600"
          >
            ✕
          </button>
        </div>

        <form onSubmit={onSubmit} className="space-y-3">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Name *</span>
            <input
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Father Name</span>
            <input
              value={fatherName}
              onChange={(e) => setFatherName(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Village *</span>
            <select
              value={villageId}
              onChange={(e) => setVillageId(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            >
              <option value="">Select village…</option>
              {villages.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.name}
                </option>
              ))}
            </select>
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Mobile Number</span>
            <input
              value={mobileNumber}
              onChange={(e) => setMobileNumber(e.target.value)}
              placeholder="10 digits"
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Address</span>
            <input
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Reference</span>
            <input
              value={reference}
              onChange={(e) => setReference(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          {error && <p className="text-sm text-red-600">{error}</p>}

          <div className="flex justify-end gap-2 pt-2">
            <button
              type="button"
              onClick={onClose}
              className="rounded-md border border-slate-300 px-4 py-2 text-sm text-slate-700 hover:bg-slate-50"
            >
              Cancel
            </button>
            <button
              type="submit"
              disabled={create.isPending}
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {create.isPending ? 'Saving…' : 'Save & Select'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
