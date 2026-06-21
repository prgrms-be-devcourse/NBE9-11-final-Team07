'use client'

import { useEffect, useState } from 'react'
import Link from 'next/link'
import { Loader2 } from 'lucide-react'
import { TossPaymentWidget } from '@/components/payments/toss-payment-widget'
import { getPendingPayment } from '@/lib/payment-api'
import type { PendingPayment } from '@/lib/payment-api'

export default function PaymentCheckoutPage() {
  const [payment, setPayment] = useState<PendingPayment | null | undefined>(undefined)

  useEffect(() => {
    setPayment(getPendingPayment())
  }, [])

  return (
    <main className="min-h-screen flex items-center justify-center bg-[oklch(0.94_0_0)]">
      <div className="w-full max-w-[430px] min-h-screen sm:h-[812px] bg-background flex flex-col sm:rounded-[2.5rem] sm:overflow-hidden sm:shadow-2xl sm:border sm:border-black/10">
        <header className="px-4 py-3 border-b border-border bg-card shrink-0">
          <h1 className="text-base font-bold text-foreground">결제수단 선택</h1>
        </header>

        <div className="flex-1 overflow-y-auto scrollbar-hide p-4">
          {payment === undefined ? (
            <div className="h-full flex items-center justify-center">
              <Loader2 size={24} className="animate-spin text-muted-foreground" />
            </div>
          ) : payment === null ? (
            <div className="h-full flex flex-col items-center justify-center gap-4 text-center">
              <p className="text-sm text-muted-foreground">결제할 주문 정보를 찾을 수 없습니다.</p>
              <Link href="/" className="w-full rounded-xl bg-foreground py-3.5 text-sm font-bold text-background">
                홈으로 이동
              </Link>
            </div>
          ) : (
            <>
              <div className="mb-4 rounded-xl bg-secondary p-4 flex items-center justify-between gap-3">
                <span className="text-sm text-muted-foreground truncate">{payment.orderName}</span>
                <strong className="text-base text-foreground shrink-0">{payment.amount.toLocaleString()}원</strong>
              </div>
              <TossPaymentWidget
                orderId={payment.orderId}
                orderName={payment.orderName}
                amount={payment.amount}
                customerName={payment.customerName}
                customerEmail={payment.customerEmail}
              />
            </>
          )}
        </div>
      </div>
    </main>
  )
}
