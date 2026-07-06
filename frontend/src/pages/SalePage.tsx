import SaleForm from '../components/SaleForm'

interface Props {
  saleType: 'cash' | 'credit'
}

export default function SalePage({ saleType }: Props) {
  return <SaleForm key={saleType} saleType={saleType} />
}
