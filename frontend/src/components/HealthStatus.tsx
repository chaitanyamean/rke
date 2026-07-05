import { useQuery } from '@tanstack/react-query'
import { getHealth } from '../lib/api'

export default function HealthStatus() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['health'],
    queryFn: getHealth,
    retry: 1,
  })

  if (isLoading) {
    return (
      <div className="rounded-lg border border-slate-200 bg-white px-6 py-4 text-slate-500 shadow-sm">
        Checking backend…
      </div>
    )
  }

  if (isError || data?.status !== 'UP') {
    return (
      <div className="rounded-lg border border-red-200 bg-red-50 px-6 py-4 font-medium text-red-700 shadow-sm">
        Backend unavailable
      </div>
    )
  }

  return (
    <div className="rounded-lg border border-green-200 bg-green-50 px-6 py-4 font-medium text-green-700 shadow-sm">
      Backend connected
    </div>
  )
}
