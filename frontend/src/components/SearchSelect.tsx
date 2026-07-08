import { useEffect, useMemo, useRef, useState } from 'react'

export interface SearchSelectOption {
  id: string
  label: string
  sublabel?: string
}

interface SearchSelectProps {
  options: SearchSelectOption[]
  value: string
  onChange: (id: string) => void
  placeholder?: string
  disabledPlaceholder?: string
  disabled?: boolean
  loading?: boolean
}

/**
 * Type-to-search combobox: a text input that filters a dropdown list as you
 * type, with keyboard navigation (Up/Down/Enter/Escape) and a clear button.
 * Generic over {id, label} options so it can back any "pick one from a list"
 * field (village, farmer name, father name, item, etc.).
 */
export default function SearchSelect({
  options,
  value,
  onChange,
  placeholder = 'Search…',
  disabledPlaceholder,
  disabled = false,
  loading = false,
}: SearchSelectProps) {
  const [query, setQuery] = useState('')
  const [open, setOpen] = useState(false)
  const [highlight, setHighlight] = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)

  const selected = useMemo(() => options.find((o) => o.id === value) ?? null, [options, value])

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase()
    if (!q) return options
    return options.filter(
      (o) =>
        o.label.toLowerCase().includes(q) || (o.sublabel?.toLowerCase().includes(q) ?? false),
    )
  }, [options, query])

  useEffect(() => {
    setHighlight(0)
  }, [query, open])

  const openList = () => {
    if (disabled) return
    setQuery('')
    setOpen(true)
  }

  const select = (option: SearchSelectOption) => {
    onChange(option.id)
    setQuery('')
    setOpen(false)
  }

  const clear = () => {
    onChange('')
    setQuery('')
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (!open) {
      if (e.key === 'ArrowDown' || e.key === 'Enter') {
        e.preventDefault()
        openList()
      }
      return
    }
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setHighlight((h) => Math.min(h + 1, filtered.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setHighlight((h) => Math.max(h - 1, 0))
    } else if (e.key === 'Enter') {
      e.preventDefault()
      const opt = filtered[highlight]
      if (opt) select(opt)
    } else if (e.key === 'Escape') {
      setOpen(false)
      setQuery('')
    }
  }

  const displayValue = open ? query : selected?.label ?? ''
  const effectivePlaceholder = disabled ? disabledPlaceholder ?? placeholder : placeholder

  return (
    <div ref={containerRef} className="relative">
      <div
        className={[
          'flex items-center rounded-md border px-3 py-2',
          disabled ? 'border-slate-200 bg-slate-100' : 'border-slate-300 bg-white',
        ].join(' ')}
      >
        <input
          type="text"
          value={displayValue}
          disabled={disabled}
          placeholder={effectivePlaceholder}
          onFocus={openList}
          onChange={(e) => {
            setQuery(e.target.value)
            setOpen(true)
          }}
          onKeyDown={handleKeyDown}
          onBlur={(e) => {
            // Options use onMouseDown+preventDefault, so genuine outside clicks
            // are the only thing that reach here — safe to close.
            if (!containerRef.current?.contains(e.relatedTarget as Node)) {
              setOpen(false)
              setQuery('')
            }
          }}
          className="w-full bg-transparent text-sm outline-none disabled:cursor-not-allowed disabled:text-slate-400"
        />
        {selected && !disabled && (
          <button
            type="button"
            tabIndex={-1}
            onMouseDown={(e) => e.preventDefault()}
            onClick={clear}
            aria-label="Clear selection"
            className="ml-1 shrink-0 text-slate-400 hover:text-slate-600"
          >
            ×
          </button>
        )}
      </div>

      {open && !disabled && (
        <ul className="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-md border border-slate-200 bg-white text-sm shadow-lg">
          {loading && <li className="px-3 py-2 text-slate-400">Loading…</li>}
          {!loading && filtered.length === 0 && (
            <li className="px-3 py-2 text-slate-400">No matches</li>
          )}
          {!loading &&
            filtered.map((option, idx) => (
              <li key={option.id}>
                <button
                  type="button"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => select(option)}
                  className={[
                    'flex w-full flex-col px-3 py-2 text-left',
                    idx === highlight ? 'bg-slate-100' : 'hover:bg-slate-50',
                    option.id === value ? 'font-medium text-slate-900' : 'text-slate-700',
                  ].join(' ')}
                >
                  <span>{option.label}</span>
                  {option.sublabel && (
                    <span className="text-xs text-slate-400">{option.sublabel}</span>
                  )}
                </button>
              </li>
            ))}
        </ul>
      )}
    </div>
  )
}
