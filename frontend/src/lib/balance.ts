/**
 * Formats an outstanding balance as a plain-language label.
 * Sign convention: negative = farmer owes firm; positive = firm owes farmer.
 */
export function formatBalance(balance: number): {
  label: string
  direction: 'owes' | 'credit' | 'settled'
} {
  const abs = Math.abs(balance).toFixed(2)
  if (balance < 0) return { label: `Farmer owes ₹${abs}`, direction: 'owes' }
  if (balance > 0) return { label: `Firm owes farmer ₹${abs}`, direction: 'credit' }
  return { label: 'No outstanding balance', direction: 'settled' }
}
