'use client'

import { useEffect, useId, useRef, useState } from 'react'
import { ANONYMOUS, loadTossPayments } from '@tosspayments/tosspayments-sdk'
import type {
  TossPaymentsWidgets,
  WidgetAgreementWidget,
  WidgetPaymentMethodWidget,
} from '@tosspayments/tosspayments-sdk'
import { Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'

interface TossPaymentWidgetProps {
  orderId: string
  orderName: string
  amount: number
  customerName?: string
  customerEmail?: string
  className?: string
}

const clientKey = process.env.NEXT_PUBLIC_TOSS_CLIENT_KEY

export function TossPaymentWidget({
  orderId,
  orderName,
  amount,
  customerName,
  customerEmail,
  className,
}: TossPaymentWidgetProps) {
  const reactId = useId().replaceAll(':', '')
  const paymentMethodId = `payment-method-${reactId}`
  const agreementId = `agreement-${reactId}`
  const widgetsRef = useRef<TossPaymentsWidgets | null>(null)
  const [ready, setReady] = useState(false)
  const [requesting, setRequesting] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    let paymentMethodWidget: WidgetPaymentMethodWidget | null = null
    let agreementWidget: WidgetAgreementWidget | null = null

    async function initialize() {
      if (!clientKey) {
        setError('토스페이먼츠 클라이언트 키가 설정되지 않았습니다.')
        return
      }
      if (!Number.isInteger(amount) || amount < 1) {
        setError('결제 금액이 올바르지 않습니다.')
        return
      }

      try {
        const tossPayments = await loadTossPayments(clientKey)
        const widgets = tossPayments.widgets({ customerKey: ANONYMOUS })
        await widgets.setAmount({ currency: 'KRW', value: amount })

        paymentMethodWidget = await widgets.renderPaymentMethods({
          selector: `#${paymentMethodId}`,
          variantKey: 'DEFAULT',
        })
        agreementWidget = await widgets.renderAgreement({
          selector: `#${agreementId}`,
          variantKey: 'AGREEMENT',
        })

        if (cancelled) {
          await Promise.allSettled([
            paymentMethodWidget.destroy(),
            agreementWidget.destroy(),
          ])
          return
        }

        widgetsRef.current = widgets
        setReady(true)
      } catch (initializeError) {
        if (!cancelled) {
          setError(
            initializeError instanceof Error
              ? initializeError.message
              : '결제위젯을 불러오지 못했습니다.',
          )
        }
      }
    }

    initialize()
    return () => {
      cancelled = true
      widgetsRef.current = null
      if (paymentMethodWidget) void paymentMethodWidget.destroy().catch(() => undefined)
      if (agreementWidget) void agreementWidget.destroy().catch(() => undefined)
    }
  }, [agreementId, amount, paymentMethodId])

  async function handlePayment() {
    if (!widgetsRef.current || requesting) return

    setRequesting(true)
    setError(null)
    try {
      await widgetsRef.current.requestPayment({
        orderId,
        orderName,
        successUrl: `${window.location.origin}/payments/success`,
        failUrl: `${window.location.origin}/payments/fail`,
        customerName,
        customerEmail,
      })
    } catch (paymentError) {
      setError(
        paymentError instanceof Error
          ? paymentError.message
          : '결제를 요청하지 못했습니다.',
      )
      setRequesting(false)
    }
  }

  return (
    <section className={cn('space-y-3', className)}>
      <div id={paymentMethodId} />
      <div id={agreementId} />

      {error && (
        <p className="px-1 text-center text-xs text-[oklch(0.62_0.24_25)]">{error}</p>
      )}

      <button
        type="button"
        disabled={!ready || requesting}
        onClick={handlePayment}
        className={cn(
          'w-full rounded-xl py-4 text-sm font-bold transition-all',
          ready && !requesting
            ? 'bg-foreground text-background active:scale-[0.98]'
            : 'cursor-not-allowed bg-secondary text-muted-foreground',
        )}
      >
        {requesting ? (
          <span className="flex items-center justify-center gap-2">
            <Loader2 size={16} className="animate-spin" />
            결제 요청 중...
          </span>
        ) : ready ? (
          `${amount.toLocaleString()}원 결제하기`
        ) : (
          '결제위젯 불러오는 중...'
        )}
      </button>
    </section>
  )
}
