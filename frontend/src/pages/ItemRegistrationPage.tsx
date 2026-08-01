import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useItemCategories } from '../api/itemCategories'
import { useCreateItem } from '../api/items'
import { getErrorMessage } from '../lib/api'
import { ITEM_UNITS } from '../types'

export default function ItemRegistrationPage() {
  const { data: categories = [] } = useItemCategories()
  const create = useCreateItem()
  const navigate = useNavigate()

  const [itemCategoryId, setItemCategoryId] = useState('')
  const [name, setName] = useState('')
  const [creditPrice, setCreditPrice] = useState('')
  const [cashPrice, setCashPrice] = useState('')
  const [unit, setUnit] = useState('')
  const [error, setError] = useState<string | null>(null)

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!itemCategoryId) {
      setError('Item category is required')
      return
    }
    if (!unit) {
      setError('Unit is required')
      return
    }
    try {
      await create.mutateAsync({
        itemCategoryId,
        name,
        creditPrice: Number(creditPrice),
        cashPrice: Number(cashPrice),
        unit,
      })
      navigate('/items')
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">Item Registration</h1>

      <form onSubmit={onSubmit} className="space-y-4 rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block sm:col-span-2">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Item Category <span className="text-red-500">*</span>
            </span>
            <select value={itemCategoryId} onChange={(e) => setItemCategoryId(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2">
              <option value="">Select category…</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>{c.name}</option>
              ))}
            </select>
          </label>
          <label className="block sm:col-span-2">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Item Name <span className="text-red-500">*</span>
            </span>
            <input value={name} onChange={(e) => setName(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2" />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Credit Price <span className="text-red-500">*</span>
            </span>
            <input
              type="text"
              value={creditPrice}
              onChange={(e) => {
                const val = e.target.value
                if (val === '' || /^\d*\.?\d*$/.test(val)) setCreditPrice(val)
              }}
              className={`w-full rounded-md border px-3 py-2 ${
                creditPrice && isNaN(parseFloat(creditPrice)) ? 'border-red-400' : 'border-slate-300'
              }`}
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Cash Price <span className="text-red-500">*</span>
            </span>
            <input
              type="text"
              value={cashPrice}
              onChange={(e) => {
                const val = e.target.value
                if (val === '' || /^\d*\.?\d*$/.test(val)) setCashPrice(val)
              }}
              className={`w-full rounded-md border px-3 py-2 ${
                cashPrice && isNaN(parseFloat(cashPrice)) ? 'border-red-400' : 'border-slate-300'
              }`}
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Unit <span className="text-red-500">*</span>
            </span>
            <select value={unit} onChange={(e) => setUnit(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2">
              <option value="">Select unit…</option>
              {ITEM_UNITS.map((u) => (
                <option key={u} value={u}>{u}</option>
              ))}
            </select>
          </label>
        </div>

        {error && <p className="text-sm text-red-600">{error}</p>}

        <button
          type="submit"
          disabled={create.isPending}
          className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
        >
          {create.isPending ? 'Saving…' : 'Add item'}
        </button>
      </form>
    </div>
  )
}
