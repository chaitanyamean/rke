export type StaffRole = 'super_admin' | 'admin' | 'staff'

export interface User {
  id: string
  tenantId: string | null
  username: string
  fullName: string | null
  role: StaffRole
}

export interface Tenant {
  id: string
  name: string
  slug: string
  logoUrl: string | null
  primaryColor: string | null
  active: boolean
  createdAt: string
  updatedAt: string
}

export interface TenantFeature {
  id: string
  tenantId: string
  featureKey: string
  enabled: boolean
}

export interface Village {
  id: string
  name: string
  landmark: string | null
  createdAt: string
  updatedAt: string
}

export interface ItemCategory {
  id: string
  tenantId: string
  name: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface BillNumberType {
  id: string
  tenantId: string
  name: string
  itemCategoryId: string
  description: string | null
  createdAt: string
  updatedAt: string
}

export interface Farmer {
  id: string
  tenantId: string
  name: string
  fatherName: string | null
  villageId: string
  address: string | null
  mobileNumber: string | null
  reference: string | null
  createdAt: string
  updatedAt: string
}

export interface Item {
  id: string
  tenantId: string
  itemCategoryId: string
  name: string
  creditPrice: number
  cashPrice: number
  createdAt: string
  updatedAt: string
}

export interface CottonLotEntry {
  id: string
  farmerId: string
  villageId: string
  quantity: number
  price: number
  amount: number
}

export interface CottonLot {
  id: string
  vehicleSerialNumber: string
  vehicleRegistrationNumber: string | null
  mutaHamaliName: string | null
  commonPrice: number
  totalQuantity: number
  totalAmount: number
  lotDate: string
  entries: CottonLotEntry[]
}

export type TransactionType = 'CASH_SALE' | 'CREDIT_SALE' | 'CASH_PAYMENT' | 'CASH_RECEIPT' | 'RETURN'
export type TransactionStatus = 'ACTIVE' | 'VOIDED'

export interface TransactionItem {
  id: string
  itemId: string
  quantity: number
  price: number
  amount: number
}

export interface Transaction {
  id: string
  farmerId: string
  billNumber: string
  /** Non-null only for RETURN transactions. */
  originalBillNumber: string | null
  billNumberTypeId: string
  transactionType: TransactionType
  transactionDate: string
  grandTotal: number
  remarks: string | null
  status: TransactionStatus
  items: TransactionItem[]
}
