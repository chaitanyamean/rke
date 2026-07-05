import { useState, type FormEvent } from 'react'
import { useVillages } from '../api/villages'
import { useCreateFarmer } from '../api/farmers'
import { getErrorMessage } from '../lib/api'

const EMPTY = {
  name: '',
  fatherName: '',
  villageId: '',
  address: '',
  mobileNumber: '',
  reference: '',
}

export default function FarmerRegistrationPage() {
  const { data: villages = [] } = useVillages()
  const create = useCreateFarmer()
  const [form, setForm] = useState({ ...EMPTY })
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<string | null>(null)

  const set = (key: keyof typeof EMPTY) => (e: { target: { value: string } }) =>
    setForm((f) => ({ ...f, [key]: e.target.value }))

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSuccess(null)
    if (!form.villageId) {
      setError('Village is required')
      return
    }
    try {
      const created = await create.mutateAsync({
        name: form.name,
        fatherName: form.fatherName || undefined,
        villageId: form.villageId,
        address: form.address || undefined,
        mobileNumber: form.mobileNumber || undefined,
        reference: form.reference || undefined,
      })
      setSuccess(`Registered ${created.name}`)
      setForm({ ...EMPTY })
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">Farmer Registration</h1>

      <form onSubmit={onSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Name *</span>
            <input value={form.name} onChange={set('name')} className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Father Name</span>
            <input value={form.fatherName} onChange={set('fatherName')} className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Village *</span>
            <select value={form.villageId} onChange={set('villageId')} className="w-full rounded-md border border-slate-300 px-3 py-2">
              <option value="">Select village…</option>
              {villages.map((v) => (
                <option key={v.id} value={v.id}>{v.name}</option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Mobile Number</span>
            <input value={form.mobileNumber} onChange={set('mobileNumber')} placeholder="10 digits" className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
          <label className="block sm:col-span-2">
            <span className="mb-1 block text-sm font-medium text-slate-700">Address</span>
            <input value={form.address} onChange={set('address')} className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
          <label className="block sm:col-span-2">
            <span className="mb-1 block text-sm font-medium text-slate-700">Reference</span>
            <input value={form.reference} onChange={set('reference')} className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}
        {success && <p className="text-sm text-green-700">{success}</p>}

        <button
          type="submit"
          disabled={create.isPending}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
        >
          {create.isPending ? 'Saving…' : 'Register farmer'}
        </button>
      </form>
    </div>
  )
}
