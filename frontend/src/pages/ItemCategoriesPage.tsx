import { useState, type FormEvent } from 'react'
import {
  useCreateItemCategory,
  useItemCategories,
  useUpdateItemCategory,
} from '../api/itemCategories'
import { getErrorMessage } from '../lib/api'

export default function ItemCategoriesPage() {
  const { data: categories = [], isLoading } = useItemCategories()
  const create = useCreateItemCategory()
  const update = useUpdateItemCategory()

  const [editingId, setEditingId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [error, setError] = useState<string | null>(null)

  const reset = () => {
    setEditingId(null)
    setName('')
    setDescription('')
    setError(null)
  }

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    const input = { name, description: description || undefined }
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
      <h1 className="text-2xl font-bold text-slate-800">Item Categories</h1>

      <form onSubmit={onSubmit} className="rounded-lg border border-slate-200 bg-white p-4 shadow-sm">
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block">
            <span className="mb-1 block text-sm font-medium text-slate-700">
              Name <span className="text-red-500">*</span>
            </span>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              className="w-full rounded-md border border-slate-300 px-3 py-2"
            />
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
            {editingId ? 'Update category' : 'Add category'}
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
              <th className="px-4 py-2 font-medium">Description</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={3} className="px-4 py-6 text-center text-slate-400">Loading…</td>
              </tr>
            )}
            {!isLoading && categories.length === 0 && (
              <tr>
                <td colSpan={3} className="px-4 py-6 text-center text-slate-400">No categories yet</td>
              </tr>
            )}
            {categories.map((c) => (
              <tr key={c.id} className="border-t border-slate-100">
                <td className="px-4 py-2 text-slate-800">{c.name}</td>
                <td className="px-4 py-2 text-slate-500">{c.description ?? '—'}</td>
                <td className="px-4 py-2 text-right">
                  <button
                    onClick={() => {
                      setEditingId(c.id)
                      setName(c.name)
                      setDescription(c.description ?? '')
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
