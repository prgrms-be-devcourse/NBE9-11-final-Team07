'use client'

import { Suspense, useEffect, useRef, useState } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { CheckCircle, Loader2, XCircle } from 'lucide-react'
import {
  clearPendingPayment,
  confirmPayment,
  getPendingPayment,
} from '@/lib/payment-api'
import type { PaymentConfirmResponse } from '@/lib/payment-api'

function PaymentSuccessContent() {
  const searchParams = useSearchParams()
  const startedRef = useRef(false)
  const [result, setResult] = useState<PaymentConfirmResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (startedRef.current) return
    startedRef.current = true

    const paymentKey = searchParams.get('paymentKey')
    const orderId = searchParams.get('orderId')
    const amountValue = searchParams.get('amount')
    const amount = amountValue ? Number(amountValue) : NaN

    if (!paymentKey || !orderId || !Number.isSafeInteger(amount) || amount < 1) {
      setError('결제 결과 정보가 올바르지 않습니다.')
      return
    }

    const pendingPayment = getPendingPayment()
    if (
      pendingPayment &&
      (pendingPayment.orderId !== orderId || pendingPayment.amount !== amount)
    ) {
      clearPendingPayment()
      setError('요청한 주문 정보와 결제 결과가 일치하지 않습니다.')
      return
    }

    confirmPayment({ paymentKey, orderId, amount })
      .then((response) => {
        clearPendingPayment()
        setResult(response)
      })
      .catch((confirmError: Error) => {
        setError(confirmError.message)
      })
  }, [searchParams])

  if (!result && !error) {
    return (
      <ResultLayout>
        <Loader2 size={36} className="animate-spin text-muted-foreground" />
        <h1 className="text-lg font-bold text-foreground">결제를 승인하고 있습니다</h1>
        <p className="text-sm text-muted-foreground">잠시만 기다려 주세요.</p>
      </ResultLayout>
    )
  }

  if (error) {
    return (
      <ResultLayout>
        <XCircle size={42} className="text-[oklch(0.62_0.24_25)]" />
        <h1 className="text-lg font-bold text-foreground">결제 승인에 실패했습니다</h1>
        <p className="text-sm text-muted-foreground">{error}</p>
        <HomeLink />
      </ResultLayout>
    )
  }

  if (!result) return null

  return (
    <ResultLayout>
      <CheckCircle size={42} className="text-[oklch(0.4_0.1_145)]" />
      <h1 className="text-lg font-bold text-foreground">결제가 완료되었습니다</h1>
      <p className="text-sm text-muted-foreground">{result.orderName}</p>
      <p className="text-xl font-black text-foreground">{result.amount.toLocaleString()}원</p>
      <HomeLink />
    </ResultLayout>
  )
}

function ResultLayout({ children }: { children: React.ReactNode }) {
  return (
    <main className="min-h-screen flex items-center justify-center bg-[oklch(0.94_0_0)]">
      <div className="w-full max-w-[430px] min-h-screen sm:min-h-[812px] bg-background flex flex-col items-center justify-center gap-3 px-8 text-center sm:rounded-[2.5rem] sm:shadow-2xl">
        {children}
      </div>
    </main>
  )
}

function HomeLink() {
  return (
    <Link href="/" className="mt-4 w-full rounded-xl bg-foreground py-3.5 text-sm font-bold text-background">
      홈으로 이동
    </Link>
  )
}

export default function PaymentSuccessPage() {
  return (
    <Suspense fallback={<ResultLayout><Loader2 size={36} className="animate-spin" /></ResultLayout>}>
      <PaymentSuccessContent />
    </Suspense>
  )
}
