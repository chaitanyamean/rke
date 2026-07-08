import { useMemo, useState } from 'react'
import { useVillages } from '../api/villages'
import { useFarmers } from '../api/farmers'
import SearchSelect from './SearchSelect'
import AddFarmerModal from './AddFarmerModal'
import type { Farmer } from '../types'

interface FarmerSelectorProps {
  /** Called with the fully resolved farmer, or null while the selection is incomplete. */
  onChange: (farmer: Farmer | null) => void
}

/**
 * Two-step farmer selector reused across transaction screens:
 *   Village → Farmer Name (filtered by village) → Father Name (to disambiguate)
 *   → Address auto-fills read-only from the resolved farmer.
 *
 * Each step is a type-to-search combobox (SearchSelect) rather than a plain
 * <select>, since village/farmer lists can get long.
 */
export default function FarmerSelector({ onChange }: FarmerSelectorProps) {
  const [villageId, setVillageId] = useState('')
  const [name, setName] = useState('')
  const [farmerId, setFarmerId] = useState('')
  const [showAddFarmer, setShowAddFarmer] = useState(false)

  const { data: villages = [] } = useVillages()
  const { data: farmers = [], isLoading } = useFarmers({ villageId }, Boolean(villageId))

  const villageOptions = useMemo(
    () => villages.map((v) => ({ id: v.id, label: v.name })),
    [villages],
  )

  const names = useMemo(
    () => Array.from(new Set(farmers.map((f) => f.name))).sort((a, b) => a.localeCompare(b)),
    [farmers],
  )
  const nameOptions = useMemo(() => names.map((n) => ({ id: n, label: n })), [names])

  const candidates = useMemo(() => farmers.filter((f) => f.name === name), [farmers, name])
  const candidateOptions = useMemo(
    () =>
      candidates.map((f) => ({
        id: f.id,
        label: f.fatherName || '(no father name)',
      })),
    [candidates],
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

  // New farmer created via the modal: select it (village/name/father all resolve
  // to this one farmer) and hand it straight to the parent.
  const handleFarmerCreated = (farmer: Farmer) => {
    setVillageId(farmer.villageId)
    setName(farmer.name)
    setFarmerId(farmer.id)
    setShowAddFarmer(false)
    onChange(farmer)
  }

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Village</span>
        <SearchSelect
          options={villageOptions}
          value={villageId}
          onChange={handleVillage}
          placeholder="Search village…"
        />
      </label>

      <div className="block">
        <div className="mb-1 flex items-center justify-between">
          <span className="text-sm font-medium text-slate-700">Farmer Name</span>
          <button
            type="button"
            onClick={() => setShowAddFarmer(true)}
            className="text-xs font-medium text-slate-600 hover:text-slate-900 hover:underline"
          >
            + New Farmer
          </button>
        </div>
        <SearchSelect
          options={nameOptions}
          value={name}
          onChange={handleName}
          disabled={!villageId}
          loading={isLoading}
          placeholder="Search farmer…"
          disabledPlaceholder="Select village first"
        />
      </div>

      <label className="block">
        <span className="mb-1 block text-sm font-medium text-slate-700">Father Name</span>
        <SearchSelect
          options={candidateOptions}
          value={farmerId}
          onChange={handleFather}
          disabled={candidates.length <= 1}
          placeholder="Search father name…"
          disabledPlaceholder={resolved ? resolved.fatherName || '(no father name)' : '—'}
        />
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

      {showAddFarmer && (
        <AddFarmerModal
          initialVillageId={villageId || undefined}
          onCreated={handleFarmerCreated}
          onClose={() => setShowAddFarmer(false)}
        />
      )}
    </div>
  )
}
