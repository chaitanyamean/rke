import type { ReactNode } from 'react'

interface Props {
  title: string
  filters: ReactNode
  onRun: () => void
  isLoading: boolean
  ran: boolean
  children: ReactNode
  /** Optional extra buttons rendered after "Run Report" */
  actions?: ReactNode
}

/**
 * Consistent layout shared by all report pages:
 *   title → filter row → "Run Report" button → results table
 */
export default function ReportShell({ title, filters, onRun, isLoading, ran, children, actions }: Props) {
  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-bold text-slate-800">{title}</h1>

      <section className="rounded-xl border border-slate-200 bg-white p-5 shadow-sm">
        <div className="flex flex-wrap items-end gap-4">
          {filters}
          <button
            onClick={onRun}
            disabled={isLoading}
            className="rounded-md bg-brand px-5 py-2 text-sm font-semibold text-white hover:opacity-90 disabled:opacity-50"
          >
            {isLoading ? 'Loading…' : 'Run Report'}
          </button>
          {actions}
        </div>
      </section>

      {ran && (
        <section className="rounded-xl border border-slate-200 bg-white shadow-sm overflow-hidden">
          {children}
        </section>
      )}
    </div>
  )
}
