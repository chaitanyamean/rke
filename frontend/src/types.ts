export type StaffRole = 'super_admin' | 'admin' | 'staff'

export interface User {
  id: string
  tenantId: string | null
  username: string
  fullName: string | null
  role: StaffRole
}

export interface Village {
  id: string
  tenantId: string
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
