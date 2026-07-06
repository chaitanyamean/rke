import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { useItems, useUpdateItem } from '../api/items'
import { useItemCategories } from '../api/itemCategories'
import { getErrorMessage } from '../lib/api'
import { ITEM_UNITS, type Item } from '../types'

export default function ItemListPage() {
  const { isAdmin } = useAuth()
  const { data: items = [], isLoading } = useItems()
  const { data: categories = [] } = useItemCategories()
  const update = useUpdateItem()

  const [editingId, setEditingId] = useState<string | null>(null)
  const [draft, setDraft] = useState({ name: '', creditPrice: '', cashPrice: '', unit: '' })
  const [error, setError] = useState<string | null>(null)

  const grouped = useMemo(() => {
    const byCategory = new Map<string, Item[]>()
    for (const item of items) {
      const list = byCategory.get(item.itemCategoryId) ?? []
      list.push(item)
      byCategory.set(item.itemCategoryId, list)
    }
    return byCategory
  }, [items])

  const startEdit = (item: Item) => {
    setEditingId(item.id)
    setDraft({
      name: item.name,
      creditPrice: String(item.creditPrice),
      cashPrice: String(item.cashPrice),
      unit: item.unit,
    })
    setError(null)
  }

  const save = async (item: Item) => {
    setError(null)
    try {
      await update.mutateAsync({
        id: item.id,
        input: {
          itemCategoryId: item.itemCategoryId,
          name: draft.name,
          creditPrice: Number(draft.creditPrice),
          cashPrice: Number(draft.cashPrice),
          unit: draft.unit,
        },
      })
      setEditingId(null)
    } catch (err) {
      setError(getErrorMessage(err))
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Items</h1>
        {isAdmin && (
          <Link
            to="/items/new"
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            + Add item
          </Link>
        )}
      </div>

      {error && <p className="text-sm text-red-600">{error}</p>}
      {isLoading && <p className="text-slate-400">Loading…</p>}
      {!isLoading && items.length === 0 && <p className="text-slate-400">No items yet</p>}

      {categories
        .filter((c) => (grouped.get(c.id)?.length ?? 0) > 0)
        .map((category) => (
          <section key={category.id} className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
            <h2 className="border-b border-slate-100 bg-slate-50 px-4 py-2 font-semibold text-slate-700">
              {category.name}
            </h2>
            <table className="w-full text-left text-sm">
              <thead className="text-slate-500">
                <tr>
                  <th className="px-4 py-2 font-medium">Item</th>
                  <th className="px-4 py-2 font-medium">Unit</th>
                  <th className="px-4 py-2 font-medium">Credit Price</th>
                  <th className="px-4 py-2 font-medium">Cash Price</th>
                  {isAdmin && <th className="px-4 py-2" />}
                </tr>
              </thead>
              <tbody>
                {(grouped.get(category.id) ?? []).map((item) => {
                  const editing = editingId === item.id
                  return (
                    <tr key={item.id} className="border-t border-slate-100">
                      <td className="px-4 py-2 text-slate-800">
                        {editing ? (
                          <input
                            value={draft.name}
                            onChange={(e) => setDraft((d) => ({ ...d, name: e.target.value }))}
                            className="w-full rounded-md border border-slate-300 px-2 py-1"
                          />
                        ) : (
                          item.name
                        )}
                      </td>
                      <td className="px-4 py-2 text-slate-600">
                        {editing ? (
                          <select
                            value={draft.unit}
                            onChange={(e) => setDraft((d) => ({ ...d, unit: e.target.value }))}
                            className="w-24 rounded-md border border-slate-300 px-2 py-1"
                          >
                            {ITEM_UNITS.map((u) => (
                              <option key={u} value={u}>{u}</option>
                            ))}
                          </select>
                        ) : (
                          item.unit
                        )}
                      </td>
                      <td className="px-4 py-2 text-slate-600">
                        {editing ? (
                          <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            value={draft.creditPrice}
                            onChange={(e) => setDraft((d) => ({ ...d, creditPrice: e.target.value }))}
                            className="w-28 rounded-md border border-slate-300 px-2 py-1"
                          />
                        ) : (
                          item.creditPrice.toFixed(2)
                        )}
                      </td>
                      <td className="px-4 py-2 text-slate-600">
                        {editing ? (
                          <input
                            type="number"
                            min="0.01"
                            step="0.01"
                            value={draft.cashPrice}
                            onChange={(e) => setDraft((d) => ({ ...d, cashPrice: e.target.value }))}
                            className="w-28 rounded-md border border-slate-300 px-2 py-1"
                          />
                        ) : (
                          item.cashPrice.toFixed(2)
                        )}
                      </td>
                      {isAdmin && (
                        <td className="px-4 py-2 text-right">
                          {editing ? (
                            <div className="flex justify-end gap-2">
                              <button onClick={() => save(item)} disabled={update.isPending} className="text-sm text-green-700 hover:underline">
                                Save
                              </button>
                              <button onClick={() => setEditingId(null)} className="text-sm text-slate-500 hover:underline">
                                Cancel
                              </button>
                            </div>
                          ) : (
                            <button onClick={() => startEdit(item)} className="text-sm text-slate-600 hover:underline">
                              Edit
                            </button>
                          )}
                        </td>
                      )}
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </section>
        ))}
    </div>
  )
}
