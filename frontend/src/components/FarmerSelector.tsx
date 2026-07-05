import { useMemo, useState } from 'react'
import { useVillages } from '../api/villages'
import { useFarmers } from '../api/farmers'
import type { Farmer } from '../types'

interface FarmerSelectorProps {
  /** Called with the fully resolved farmer, or null while the selection is incomplete. */
  onChange: (farmer: Farmer | null) => void
}

/**
 * Two-step farmer selector reused across transaction screens:
 *   Village → Farmer Name (filtered by village) → Father Name (to disambiguate)
 *   → Address auto-fills read-only from the resolved farmer.
 */
export default function FarmerSelector({ onChange }: FarmerSelectorProps) {
  const [villageId, setVillageId] = useState('')
  const [name, setName] = useState('')
  const [farmerId, setFarmerId] = useState('')

  const { data: villages = [] } = useVillages()
  const { data: farmers = [], isLoading } = useFarmers({ villageId }, Boolean(villageId))

  const names = useMemo(
    () => Array.from(new Set(farmers.map((f) => f.name))).sort((a, b) => a.localeCompare(b)),
    [farmers],
  )
  const candidates = useMemo(
    () => farmers.filter((f) => f.name === name),
    [farmers, name],
  )
  const resolved = farmers.find((f) => f.id === farmerId) ?? null

  const handleVillage = (value: string) => {
    setVillageId(value)
    setName('')
    setFarmerId('')
    onChange(null)
  }

  const handleName = (value: string) => {
    setName(value)
    const matches = farmers.filter((f) => f.name === value)
    if (matches.length === 1) {
      setFarmerId(matches[0].id)
      onChange(matches[0])
    } else {
      setFarmerId('')
      onChange(null)
    }
  }

  const handleFather = (value: string) => {
    setFarmerId(value)
    onChange(farmers.find((f) => f.id === value) ?? null)
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Village</span>
        <select
          value={villageId}
          onChange={(e) => handleVillage(e.target.value)}
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
        <span className="mb-1 block text-sm font-medium text-slate-700">Farmer Name</span>
        <select
          value={name}
          onChange={(e) => handleName(e.target.value)}
          disabled={!villageId || isLoading}
          className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:bg-slate-100"
        >
          <option value="">{villageId ? 'Select farmer…' : 'Select village first'}</option>
          {names.map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </label>

      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Father Name</span>
        <select
          value={farmerId}
          onChange={(e) => handleFather(e.target.value)}
          disabled={candidates.length <= 1}
          className="w-full rounded-md border border-slate-300 px-3 py-2 disabled:bg-slate-100"
        >
          {candidates.length <= 1 ? (
            <option value={farmerId}>
              {resolved ? resolved.fatherName || '(no father name)' : '—'}
            </option>
          ) : (
            <>
              <option value="">Select to disambiguate…</option>
              {candidates.map((f) => (
                <option key={f.id} value={f.id}>
                  {f.fatherName || '(no father name)'}
                </option>
              ))}
            </>
          )}
        </select>
      </label>

      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Address</span>
        <input
          readOnly
          value={resolved?.address ?? ''}
          placeholder="Auto-filled from farmer"
          className="w-full rounded-md border border-slate-200 bg-slate-100 px-3 py-2 text-slate-600"
        />
      </label>
    </div>
  )
}
