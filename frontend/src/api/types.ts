/** Mirrors the Spring Boot response/request DTOs (see docs/api.md). Money arrives as JSON numbers. */

export type Role = 'RESIDENT' | 'TAX_OFFICER' | 'ADMIN'
export type PropertyClass = 'RESIDENTIAL' | 'COMMERCIAL' | 'INDUSTRIAL'
export type BillType = 'INTERIM' | 'FINAL'
export type BillStatus = 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'CANCELLED'
export type PaymentMethod = 'CHEQUE' | 'BANK_TRANSFER' | 'PRE_AUTHORIZED' | 'COUNTER'
export type PaymentStatus = 'RECORDED' | 'REVERSED'

export const ROLES: Role[] = ['RESIDENT', 'TAX_OFFICER', 'ADMIN']
export const PROPERTY_CLASSES: PropertyClass[] = ['RESIDENTIAL', 'COMMERCIAL', 'INDUSTRIAL']
export const PAYMENT_METHODS: PaymentMethod[] = ['CHEQUE', 'BANK_TRANSFER', 'PRE_AUTHORIZED', 'COUNTER']

/** Spring Data PagedModel. */
export interface Page<T> {
  content: T[]
  page: { size: number; number: number; totalElements: number; totalPages: number }
}

interface Audited {
  createdAt: string
  updatedAt: string
}

export interface User extends Audited {
  id: number
  email: string
  fullName: string
  role: Role
  active: boolean
}

export interface Owner extends Audited {
  id: number
  userId: number | null
  fullName: string
  email: string | null
  phone: string | null
  mailingAddress: string | null
}

export interface Property extends Audited {
  id: number
  rollNumber: string
  streetAddress: string
  city: string
  postalCode: string
  propertyClass: PropertyClass
}

export interface Ownership {
  propertyId: number
  ownerId: number
  ownerName: string
  ownershipPercent: number
}

export interface Assessment extends Audited {
  id: number
  propertyId: number
  taxYear: number
  assessedValue: number
  assessmentDate: string
}

export interface TaxRate extends Audited {
  id: number
  taxYear: number
  propertyClass: PropertyClass
  rate: number
  description: string | null
}

export interface TaxAccount extends Audited {
  id: number
  accountNumber: string
  propertyId: number
  status: 'ACTIVE' | 'CLOSED'
}

export interface TaxBill extends Audited {
  id: number
  billNumber: string
  taxAccountId: number
  assessmentId: number
  taxRateId: number
  taxYear: number
  billType: BillType
  amount: number
  paidAmount: number
  outstandingAmount: number
  issueDate: string
  dueDate: string
  status: BillStatus
  /** When the PDF was first generated and stored; null if nobody has downloaded it yet. */
  documentGeneratedAt: string | null
}

export interface AccountBalance {
  taxAccountId: number
  totalBilled: number
  totalPaid: number
  outstanding: number
}

export interface Payment extends Audited {
  id: number
  taxBillId: number
  amount: number
  paymentDate: string
  method: PaymentMethod
  referenceNumber: string
  status: PaymentStatus
  recordedById: number
}

export type ReportType = 'TAX_ROLL_SUMMARY' | 'OUTSTANDING_BALANCES' | 'PAYMENTS_RECONCILIATION'
export const REPORT_TYPES: ReportType[] = ['TAX_ROLL_SUMMARY', 'OUTSTANDING_BALANCES', 'PAYMENTS_RECONCILIATION']

/** An asynchronous report job (state kept in DynamoDB). */
export interface ReportJob {
  jobId: string
  type: ReportType
  status: 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  taxYear: number
  requestedBy: number
  requestedAt: string
  updatedAt: string
  completedAt: string | null
  errorMessage: string | null
  downloadable: boolean
}

/** Who is signed in (from /api/dev/token locally; /api/me works with any token). */
export interface SessionUser {
  id: number
  email: string
  fullName: string
  role: Role
}

export interface Me extends SessionUser {
  ownerId: number | null
}

/** RFC 9457 ProblemDetail returned for every error. */
export interface Problem {
  title?: string
  status?: number
  detail?: string
  errors?: Record<string, string>
  constraint?: string
}
