import PaymentForm from '../components/PaymentForm'

interface Props {
  direction: 'payment' | 'receipt'
}

export default function PaymentPage({ direction }: Props) {
  return <PaymentForm direction={direction} />
}
