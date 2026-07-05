import axios, { AxiosError } from 'axios'

// The API base URL is provided at build time via Vite's env system.
// It maps to the API_BASE_URL environment variable documented in .env.example.
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8000'

export const api = axios.create({
  baseURL: API_BASE_URL,
  // Send/receive the session cookie on cross-origin requests.
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
})

export interface HealthResponse {
  status: string
  service: string
  timestamp: string
}

export async function getHealth(): Promise<HealthResponse> {
  const { data } = await api.get<HealthResponse>('/api/health')
  return data
}

interface ApiErrorBody {
  message?: string
  error?: string
  fieldErrors?: Record<string, string>
}

/** Extracts a human-friendly message (including field errors) from an API error. */
export function getErrorMessage(err: unknown, fallback = 'Something went wrong'): string {
  const axiosErr = err as AxiosError<ApiErrorBody>
  const body = axiosErr?.response?.data
  if (body?.fieldErrors && Object.keys(body.fieldErrors).length > 0) {
    return Object.entries(body.fieldErrors)
      .map(([field, msg]) => `${field}: ${msg}`)
      .join(', ')
  }
  return body?.message || body?.error || axiosErr?.message || fallback
}
