import axios from 'axios'

// The API base URL is provided at build time via Vite's env system.
// It maps to the API_BASE_URL environment variable documented in .env.example.
export const API_BASE_URL: string =
  import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8000'

export const api = axios.create({
  baseURL: API_BASE_URL,
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
