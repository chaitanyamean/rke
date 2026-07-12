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
import StaffUsersPage from './pages/admin/StaffUsersPage'
import SalePage from './pages/SalePage'
import SaleEditPage from './pages/SaleEditPage'
import PaymentPage from './pages/PaymentPage'
import PaymentEditPage from './pages/PaymentEditPage'
import ReturnPage from './pages/ReturnPage'
import ReturnEditPage from './pages/ReturnEditPage'
import CottonLotPage from './pages/CottonLotPage'
import ReportsLandingPage from './pages/reports/ReportsLandingPage'
import FarmerLedgerPage from './pages/reports/FarmerLedgerPage'
import VillageOutstandingsPage from './pages/reports/VillageOutstandingsPage'
import ItemSalesPage from './pages/reports/ItemSalesPage'
import DateSalesPage from './pages/reports/DateSalesPage'
import DatePaymentsPage from './pages/reports/DatePaymentsPage'

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
          <Route path="/cotton" element={<CottonLotPage />} />
          <Route path="/reports" element={<ReportsLandingPage />} />
          <Route path="/reports/ledger" element={<FarmerLedgerPage />} />
          <Route path="/reports/village-outstandings" element={<VillageOutstandingsPage />} />
          <Route path="/reports/item-sales" element={<ItemSalesPage />} />
          <Route path="/reports/date-sales" element={<DateSalesPage />} />
          <Route path="/reports/date-payments" element={<DatePaymentsPage />} />

          {/* Admin-only */}
          <Route element={<AdminRoute />}>
            <Route path="/villages" element={<VillagesPage />} />
            <Route path="/item-categories" element={<ItemCategoriesPage />} />
            <Route path="/bill-number-types" element={<BillNumberTypesPage />} />
            <Route path="/items/new" element={<ItemRegistrationPage />} />
            <Route path="/staff-users" element={<StaffUsersPage />} />

            {/* Transaction edits — corrections only, restricted to admin. */}
            <Route path="/sales/cash/:id/edit" element={<SaleEditPage saleType="cash" />} />
            <Route path="/sales/credit/:id/edit" element={<SaleEditPage saleType="credit" />} />
            <Route path="/payments/payment/:id/edit" element={<PaymentEditPage direction="payment" />} />
            <Route path="/payments/receipt/:id/edit" element={<PaymentEditPage direction="receipt" />} />
            <Route path="/returns/:id/edit" element={<ReturnEditPage />} />
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
