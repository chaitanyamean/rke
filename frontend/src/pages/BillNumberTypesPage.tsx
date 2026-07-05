import { useMemo, useState, type FormEvent } from 'react'
import {
  useBillNumberTypes,
  useCreateBillNumberType,
  useUpdateBillNumberType,
} from '../api/billNumberTypes'
import { useItemCategories } from '../api/itemCategories'
import { getErrorMessage } from '../lib/api'

export default function BillNumberTypesPage() {
  const { data: types = [], isLoading } = useBillNumberTypes()
  const { data: categories = [] } = useItemCategories()
  const create = useCreateBillNumberType()
  const update = useUpdateBillNumberType()

  const [editingId, setEditingId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [itemCategoryId, setItemCategoryId] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)

  const categoryName = useMemo(() => {
    const map = new Map(categories.map((c) => [c.id, c.name]))
    return (id: string) => map.get(id) ?? id
  }, [categories])

  const reset = () => {
    setEditingId(null)
    setName('')
    setItemCategoryId('')
    setDescription('')
    setError(null)
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (!itemCategoryId) {
      setError('Item category is required')
      return
    }
    const input = { name, itemCategoryId, description: description || undefined }
    try {
      if (editingId) {
        await update.mutateAsync({ id: editingId, input })
      } else {
        await create.mutateAsync(input)
      }
      reset()
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-slate-800">Bill Number Types</h1>

      <form onSubmit={onSubmit} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-4 sm:grid-cols-3">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Name *</span>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Item Category *</span>
            <select
              value={itemCategoryId}
              onChange={(e) => setItemCategoryId(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            >
              <option value="">Select category…</option>
              {categories.map((c) => (
                <option key={c.id} value={c.id}>
                  {c.name}
                </option>
              ))}
            </select>
          </label>
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">Description</span>
            <input
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
          </label>
        </div>
        {error && <p className="mt-3 text-sm text-red-600">{error}</p>}
        <div className="mt-4 flex gap-2">
          <button
            type="submit"
            disabled={create.isPending || update.isPending}
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800 disabled:opacity-60"
          >
            {editingId ? 'Update type' : 'Add type'}
          </button>
          {editingId && (
            <button type="button" onClick={reset} className="rounded-md border border-slate-300 px-4 py-2 text-sm">
              Cancel
            </button>
          )}
        </div>
      </form>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-2 font-medium">Name</th>
              <th className="px-4 py-2 font-medium">Category</th>
              <th className="px-4 py-2 font-medium">Description</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={4} className="px-4 py-6 text-center text-slate-400">Loading…</td>
              </tr>
            )}
            {!isLoading && types.length === 0 && (
              <tr>
                <td colSpan={4} className="px-4 py-6 text-center text-slate-400">No bill number types yet</td>
              </tr>
            )}
            {types.map((t) => (
              <tr key={t.id} className="border-t border-slate-100">
                <td className="px-4 py-2 text-slate-800">{t.name}</td>
                <td className="px-4 py-2 text-slate-500">{categoryName(t.itemCategoryId)}</td>
                <td className="px-4 py-2 text-slate-500">{t.description ?? '—'}</td>
                <td className="px-4 py-2 text-right">
                  <button
                    onClick={() => {
                      setEditingId(t.id)
                      setName(t.name)
                      setItemCategoryId(t.itemCategoryId)
                      setDescription(t.description ?? '')
                      setError(null)
                    }}
                    className="text-sm text-slate-600 hover:underline"
                  >
                    Edit
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
