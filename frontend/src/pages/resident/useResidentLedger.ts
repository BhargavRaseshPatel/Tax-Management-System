import { useQueries } from '@tanstack/react-query'
import { apiQuery, useApi } from '../../api/client'
import type { AccountBalance, Assessment, Me, Ownership, Payment, Property, TaxAccount, TaxBill } from '../../api/types'

export interface ResidentProperty {
  property: Property
  share: number | null
  assessments: Assessment[] // newest year first
  account: TaxAccount | null
  balance: AccountBalance | null
  bills: TaxBill[]
}

export interface ResidentPayment {
  payment: Payment
  bill: TaxBill
  property: Property
}

/**
 * Everything the signed-in resident owns, assembled from the existing per-resource endpoints.
 * Shared by both resident pages; TanStack Query caches each URL so nothing is fetched twice.
 */
export function useResidentLedger(options: { withPayments?: boolean } = {}) {
  const me = useApi<Me>('/api/me')
  const ownerId = me.data?.ownerId ?? null
  const propertiesQuery = useApi<Property[]>(ownerId ? `/api/owners/${ownerId}/properties` : null)
  const properties = propertiesQuery.data ?? []

  const perProperty = (path: (p: Property) => string, allow404 = false) =>
    properties.map((p) => apiQuery<unknown>(path(p), allow404))
  const shares = useQueries({ queries: perProperty((p) => `/api/properties/${p.id}/owners`) })
  const assessments = useQueries({ queries: perProperty((p) => `/api/properties/${p.id}/assessments`) })
  const accounts = useQueries({ queries: perProperty((p) => `/api/properties/${p.id}/tax-account`, true) })

  const accountList = accounts.map((q) => q.data as TaxAccount | null | undefined)
  // Only query accounts that exist, so every query key is unique.
  const accountIds = accountList.flatMap((a) => (a ? [a.id] : []))
  const balances = useQueries({ queries: accountIds.map((id) => apiQuery<AccountBalance>(`/api/tax-accounts/${id}/balance`)) })
  const bills = useQueries({ queries: accountIds.map((id) => apiQuery<TaxBill[]>(`/api/tax-accounts/${id}/bills`)) })
  const forAccount = <T,>(results: { data?: T }[], account: TaxAccount | null | undefined) =>
    account ? results[accountIds.indexOf(account.id)]?.data : undefined

  const rows: ResidentProperty[] = properties.map((property, i) => ({
    property,
    share: (shares[i]?.data as Ownership[] | undefined)?.find((s) => s.ownerId === ownerId)?.ownershipPercent ?? null,
    assessments: (assessments[i]?.data as Assessment[] | undefined) ?? [],
    account: accountList[i] ?? null,
    balance: forAccount(balances, accountList[i]) ?? null,
    bills: forAccount(bills, accountList[i]) ?? [],
  }))

  const allBills = rows.flatMap((r) => r.bills.map((bill) => ({ bill, property: r.property })))
  const paymentQueries = useQueries({
    queries: options.withPayments ? allBills.map(({ bill }) => apiQuery<Payment[]>(`/api/tax-bills/${bill.id}/payments`)) : [],
  })
  const payments: ResidentPayment[] = paymentQueries.flatMap((q, i) =>
    (q.data ?? []).map((payment) => ({ payment, ...allBills[i]! })))

  const all = [me, propertiesQuery, ...shares, ...assessments, ...accounts, ...balances, ...bills, ...paymentQueries]
  return {
    ownerId,
    ownerResolved: me.isSuccess,
    rows,
    payments,
    isPending: me.isPending || (ownerId !== null && propertiesQuery.isPending) || all.some((q) => q.isLoading),
    error: all.find((q) => q.error)?.error ?? null,
  }
}
