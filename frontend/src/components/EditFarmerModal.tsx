import { useState, type FormEvent } from 'react'
import { useVillages } from '../api/villages'
import { useUpdateFarmer } from '../api/farmers'
import { getErrorMessage } from '../lib/api'
import type { Farmer } from '../types'

interface EditFarmerModalProps {
  farmer: Farmer
  onUpdated: (farmer: Farmer) => void
  onClose: () => void
}

/**
 * Modal for editing an existing farmer's details. Mirrors AddFarmerModal's
 * fields but pre-fills from the given farmer and calls the update endpoint.
 */
export default function EditFarmerModal({ farmer, onUpdated, onClose }: EditFarmerModalProps) {
  const { data: villages = [] } = useVillages()
  const update = useUpdateFarmer()

  const [name, setName] = useState(farmer.name)
  const [fatherName, setFatherName] = useState(farmer.fatherName ?? '')
  const [villageId, setVillageId] = useState(farmer.villageId)
  const [address, setAddress] = useState(farmer.address ?? '')
  const [mobileNumber, setMobileNumber] = useState(farmer.mobileNumber ?? '')
  const [reference, setReference] = useState(farmer.reference ?? '')
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
    if (!fatherName.trim()) {
      setError('Father name is required')
      return
    }
    try {
      const updated = await update.mutateAsync({
        id: farmer.id,
        input: {
          name: name.trim(),
          fatherName: fatherName.trim() || undefined,
          villageId,
          address: address.trim() || undefined,
          mobileNumber: mobileNumber.trim() || undefined,
          reference: reference.trim() || undefined,
        },
      })
      onUpdated(updated)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
      <div className="w-full max-w-md rounded-lg bg-white p-5 shadow-xl">
        <div className="mb-4 flex items-center justify-between">
          <h3 className="text-lg font-semibold text-slate-800">Edit Farmer</h3>
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
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Name <span className="text-red-500">*</span>
            </span>
            <input
              id="farmer-edit-name"
              name="name"
              autoFocus
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Father Name <span className="text-red-500">*</span>
            </span>
            <input
              id="farmer-edit-father-name"
              name="fatherName"
              value={fatherName}
              onChange={(e) => setFatherName(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Village <span className="text-red-500">*</span>
            </span>
            <select
              id="farmer-edit-village"
              name="villageId"
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
              id="farmer-edit-mobile"
              name="mobileNumber"
              value={mobileNumber}
              onChange={(e) => setMobileNumber(e.target.value)}
              placeholder="10 digits"
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Address</span>
            <input
              id="farmer-edit-address"
              name="address"
              value={address}
              onChange={(e) => setAddress(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>

          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Reference</span>
            <input
              id="farmer-edit-reference"
              name="reference"
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
              disabled={update.isPending}
              className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
            >
              {update.isPending ? 'Saving…' : 'Save changes'}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
