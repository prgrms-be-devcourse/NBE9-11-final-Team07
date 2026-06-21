'use client'

import { Suspense, useEffect } from 'react'
import Link from 'next/link'
import { useSearchParams } from 'next/navigation'
import { Loader2, XCircle } from 'lucide-react'
import { clearPendingPayment } from '@/lib/payment-api'

function PaymentFailContent() {
  const searchParams = useSearchParams()
  const message = searchParams.get('message') ?? '결제가 완료되지 않았습니다.'
  const code = searchParams.get('code')

  useEffect(() => {
    clearPendingPayment()
  }, [])

  return (
    <main className="min-h-screen flex items-center justify-center bg-[oklch(0.94_0_0)]">
      <div className="w-full max-w-[430px] min-h-screen sm:min-h-[812px] bg-background flex flex-col items-center justify-center gap-3 px-8 text-center sm:rounded-[2.5rem] sm:shadow-2xl">
        <XCircle size={42} className="text-[oklch(0.62_0.24_25)]" />
        <h1 className="text-lg font-bold text-foreground">결제에 실패했습니다</h1>
        <p className="text-sm text-muted-foreground">{message}</p>
        {code && <p className="text-xs text-muted-foreground">오류 코드: {code}</p>}
        <Link href="/" className="mt-4 w-full rounded-xl bg-foreground py-3.5 text-sm font-bold text-background">
          홈으로 이동
        </Link>
      </div>
    </main>
  )
}

export default function PaymentFailPage() {
  return (
    <Suspense fallback={<Loader2 size={36} className="animate-spin" />}>
      <PaymentFailContent />
    </Suspense>
  )
}
