import { Routes, Route } from 'react-router-dom'
import { AdminRoute, ProtectedRoute, SuperAdminRoute } from './components/ProtectedRoute'
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
import TenantListPage from './pages/admin/TenantListPage'
import TenantFormPage from './pages/admin/TenantFormPage'
import TenantFeaturesPage from './pages/admin/TenantFeaturesPage'
import SalePage from './pages/SalePage'
import PaymentPage from './pages/PaymentPage'
import ReturnPage from './pages/ReturnPage'

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
          <Route path="/sales/cash" element={<SalePage saleType="cash" />} />
          <Route path="/sales/credit" element={<SalePage saleType="credit" />} />
          <Route path="/payments/payment" element={<PaymentPage direction="payment" />} />
          <Route path="/payments/receipt" element={<PaymentPage direction="receipt" />} />
          <Route path="/returns" element={<ReturnPage />} />

          {/* Admin-only */}
          <Route element={<AdminRoute />}>
            <Route path="/villages" element={<VillagesPage />} />
            <Route path="/item-categories" element={<ItemCategoriesPage />} />
            <Route path="/bill-number-types" element={<BillNumberTypesPage />} />
            <Route path="/items/new" element={<ItemRegistrationPage />} />
          </Route>

          {/* Super admin only */}
          <Route element={<SuperAdminRoute />}>
            <Route path="/admin/tenants" element={<TenantListPage />} />
            <Route path="/admin/tenants/new" element={<TenantFormPage />} />
            <Route path="/admin/tenants/:id/edit" element={<TenantFormPage />} />
            <Route path="/admin/tenants/:id/features" element={<TenantFeaturesPage />} />
          </Route>
        </Route>
      </Route>
    </Routes>
  )
}
