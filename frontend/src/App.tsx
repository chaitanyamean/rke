import { Routes, Route } from 'react-router-dom'
import HealthStatus from './components/HealthStatus'

function Home() {
  return (
    <main className="min-h-screen bg-slate-50 flex flex-col items-center justify-center gap-6 p-8">
      <h1 className="text-3xl font-bold text-slate-800">RKE</h1>
      <p className="text-slate-500">React + Vite frontend · Spring Boot backend</p>
      <HealthStatus />
    </main>
  )
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
    </Routes>
  )
}
