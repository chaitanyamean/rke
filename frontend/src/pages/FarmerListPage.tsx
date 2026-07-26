import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useFarmers } from '../api/farmers'
import { useVillages } from '../api/villages'
import { useAuth } from '../auth/AuthContext'
import EditFarmerModal from '../components/EditFarmerModal'
import type { Farmer } from '../types'

export default function FarmerListPage() {
  const { isAdmin } = useAuth()
  const [name, setName] = useState('')
  const [fatherName, setFatherName] = useState('')
  const [villageId, setVillageId] = useState('')
  const [mobile, setMobile] = useState('')
  const [editingFarmer, setEditingFarmer] = useState<Farmer | null>(null)

  const { data: villages = [] } = useVillages()
  const { data: farmers = [], isLoading } = useFarmers({ name, fatherName, villageId, mobile })

  const villageName = useMemo(() => {
    const map = new Map(villages.map((v) => [v.id, v.name]))
    return (id: string) => map.get(id) ?? id
  }, [villages])

  return (
    <div className="space-y-6">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-slate-800">Farmers</h1>
        {isAdmin && (
          <Link
            to="/farmers/new"
            className="rounded-md bg-slate-900 px-4 py-2 text-sm font-medium text-white hover:bg-slate-800"
          >
            + Register farmer
          </Link>
        )}
      </div>

      <div className="grid gap-4 rounded-lg border border-slate-200 bg-white p-4 shadow-sm sm:grid-cols-4">
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Name</span>
          <input id="farmer-search-name" name="name" value={name} onChange={(e) => setName(e.target.value)} placeholder="Search by name" className="w-full rounded-md border border-slate-300 px-3 py-2" />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Father Name</span>
          <input id="farmer-search-father-name" name="fatherName" value={fatherName} onChange={(e) => setFatherName(e.target.value)} placeholder="Search by father name" className="w-full rounded-md border border-slate-300 px-3 py-2" />
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Village</span>
          <select id="farmer-search-village" name="villageId" value={villageId} onChange={(e) => setVillageId(e.target.value)} className="w-full rounded-md border border-slate-300 px-3 py-2">
            <option value="">All villages</option>
            {villages.map((v) => (
              <option key={v.id} value={v.id}>{v.name}</option>
            ))}
          </select>
        </label>
        <label className="block">
          <span className="mb-1 block text-sm font-medium text-slate-700">Mobile</span>
          <input id="farmer-search-mobile" name="mobile" value={mobile} onChange={(e) => setMobile(e.target.value)} placeholder="Search by mobile" className="w-full rounded-md border border-slate-300 px-3 py-2" />
        </label>
      </div>

      <div className="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm">
        <table className="w-full text-left text-sm">
          <thead className="bg-slate-50 text-slate-500">
            <tr>
              <th className="px-4 py-2 font-medium">Name</th>
              <th className="px-4 py-2 font-medium">Father Name</th>
              <th className="px-4 py-2 font-medium">Village</th>
              <th className="px-4 py-2 font-medium">Mobile</th>
              <th className="px-4 py-2 font-medium">Address</th>
              {isAdmin && <th className="px-4 py-2 font-medium">Actions</th>}
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={isAdmin ? 6 : 5} className="px-4 py-6 text-center text-slate-400">Loading…</td>
              </tr>
            )}
            {!isLoading && farmers.length === 0 && (
              <tr>
                <td colSpan={isAdmin ? 6 : 5} className="px-4 py-6 text-center text-slate-400">No farmers found</td>
              </tr>
            )}
            {farmers.map((f) => (
              <tr key={f.id} className="border-t border-slate-100">
                <td className="px-4 py-2 text-slate-800">{f.name}</td>
                <td className="px-4 py-2 text-slate-500">{f.fatherName ?? '—'}</td>
                <td className="px-4 py-2 text-slate-500">{villageName(f.villageId)}</td>
                <td className="px-4 py-2 text-slate-500">{f.mobileNumber ?? '—'}</td>
                <td className="px-4 py-2 text-slate-500">{f.address ?? '—'}</td>
                {isAdmin && (
                  <td className="px-4 py-2">
                    <button
                      type="button"
                      onClick={() => setEditingFarmer(f)}
                      className="text-sm font-medium text-blue-600 hover:underline"
                    >
                      Edit
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {editingFarmer && (
        <EditFarmerModal
          farmer={editingFarmer}
          onClose={() => setEditingFarmer(null)}
          onUpdated={() => setEditingFarmer(null)}
        />
      )}
    </div>
  )
}
