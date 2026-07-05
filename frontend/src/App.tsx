import { Routes, Route } from 'react-router-dom'
import { AdminRoute, ProtectedRoute } from './components/ProtectedRoute'
import Layout from './components/Layout'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import VillagesPage from './pages/VillagesPage'
import ItemCategoriesPage from './pages/ItemCategoriesPage'
import BillNumberTypesPage from './pages/BillNumberTypesPage'
import FarmerRegistrationPage from './pages/FarmerRegistrationPage'
import FarmerListPage from './pages/FarmerListPage'
import ItemRegistrationPage from './pages/ItemRegistrationPage'
import ItemListPage from './pages/ItemListPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />

      {/* Authenticated area */}
      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />

          {/* Open to any logged-in user */}
          <Route path="/farmers" element={<FarmerListPage />} />
          <Route path="/farmers/new" element={<FarmerRegistrationPage />} />
          <Route path="/items" element={<ItemListPage />} />

          {/* Admin-only */}
          <Route element={<AdminRoute />}>
            <Route path="/villages" element={<VillagesPage />} />
            <Route path="/item-categories" element={<ItemCategoriesPage />} />
            <Route path="/bill-number-types" element={<BillNumberTypesPage />} />
            <Route path="/items/new" element={<ItemRegistrationPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  )
}
