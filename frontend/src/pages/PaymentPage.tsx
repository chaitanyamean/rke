import PaymentForm from '../components/PaymentForm'

interface Props {
  direction: 'payment' | 'receipt'
}

export default function PaymentPage({ direction }: Props) {
  // key forces a full remount when switching between payment and receipt,
  // so stale form state (phase, farmer, amounts) from the previous direction
  // never bleeds through.
  return <PaymentForm key={direction} direction={direction} />
}
