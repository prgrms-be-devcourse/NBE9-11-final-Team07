'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, Loader2, MapPin, Calendar, Clock } from 'lucide-react'
import { cn } from '@/lib/utils'
import { popupStores, formatDateKorean } from '@/lib/data'
import type { ReservationPayload } from '@/lib/data'
import { reservationApi } from '@/lib/reservation-api'
import type { PopupStoreDetailResponse } from '@/lib/reservation-api'
import { savePendingPayment } from '@/lib/payment-api'

interface ReservationPaymentScreenProps {
  payload: ReservationPayload
  onBack: () => void
  onComplete: () => void
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h2 className="text-sm font-bold text-foreground">{children}</h2>
}

function InputField({
  label,
  placeholder,
  type = 'text',
  value,
  onChange,
}: {
  label: string
  placeholder: string
  type?: string
  value: string
  onChange: (v: string) => void
}) {
  return (
    <div className="space-y-1.5">
      <label className="text-xs font-semibold text-muted-foreground">{label}</label>
      <input
        type={type}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={placeholder}
        className={cn(
          'w-full bg-secondary border border-border rounded-xl px-4 py-3.5',
          'text-sm text-foreground placeholder:text-muted-foreground',
          'focus:outline-none focus:ring-2 focus:ring-foreground/20 focus:border-foreground/40',
          'transition-all',
        )}
      />
    </div>
  )
}

export function ReservationPaymentScreen({
  payload,
  onBack,
  onComplete,
}: ReservationPaymentScreenProps) {
  const router = useRouter()
  const store = popupStores.find((s) => s.id === payload.storeId)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [popupDetail, setPopupDetail] = useState<PopupStoreDetailResponse | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const reservationIdRef = useRef<number | null>(null)
  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    let cancelled = false
    reservationApi.getPopupDetail(payload.storeId)
      .then((response) => {
        if (!cancelled) setPopupDetail(response)
      })
      .catch(() => {
        // The payment preparation response remains the source of truth for the amount.
      })

    return () => {
      cancelled = true
    }
  }, [payload.storeId])

  if (!store) return null

  const displayName = popupDetail?.title ?? store.name
  const displayLocation = popupDetail?.location ?? store.location
  const displayImage = popupDetail?.imageUrl ?? store.image
  const ticketPrice = popupDetail?.price ?? store.ticketPrice
  const isFree = popupDetail ? popupDetail.feeType === 'FREE' : ticketPrice === 0
  const canProceed = name.trim().length > 0 && /^010-\d{4}-\d{4}$/.test(phone)

  async function handleSubmit() {
    if (!canProceed || submitting) return

    setSubmitting(true)
    setError(null)
    try {
      if (!reservationIdRef.current) {
        const reservation = await reservationApi.createReservation(payload.slotId)
        reservationIdRef.current = reservation.reservationId
      }
      if (!idempotencyKeyRef.current) {
        idempotencyKeyRef.current = crypto.randomUUID()
      }

      const payment = await reservationApi.startPayment(reservationIdRef.current, {
        name: name.trim(),
        phone,
        idempotencyKey: idempotencyKeyRef.current,
      })

      if (payment.orderId && payment.orderName && payment.amount) {
        savePendingPayment({
          orderId: payment.orderId,
          orderName: payment.orderName,
          amount: payment.amount,
          customerName: name.trim(),
        })
        router.push('/payments/checkout')
      } else {
        onComplete()
      }
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '예약 결제를 준비하지 못했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-border bg-card shrink-0">
        <button
          onClick={onBack}
          className="flex items-center justify-center w-9 h-9 -ml-1 rounded-full hover:bg-secondary transition-colors"
          aria-label="뒤로 가기"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-base font-bold text-foreground">예약 정보 확인</h1>
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-hide">
        <div className="p-4 space-y-5">

          {/* Reservation Info */}
          <div className="space-y-3">
            <SectionTitle>예약 정보</SectionTitle>
            <div className="bg-secondary rounded-2xl overflow-hidden">
              {/* Poster + store name */}
              <div className="flex items-center gap-3 p-4 border-b border-border">
                <div className="w-16 h-16 rounded-xl overflow-hidden bg-card shrink-0">
                  <img
                    src={displayImage}
                    alt={displayName}
                    className="w-full h-full object-cover"
                  />
                </div>
                <div className="min-w-0">
                  <p className="text-sm font-bold text-foreground leading-snug line-clamp-2">{displayName}</p>
                  <div className="flex items-center gap-1 mt-1">
                    <MapPin size={11} className="text-muted-foreground shrink-0" />
                    <p className="text-xs text-muted-foreground truncate">{displayLocation}</p>
                  </div>
                </div>
              </div>

              {/* Date + Time */}
              <div className="divide-y divide-border">
                <div className="flex items-center gap-3 px-4 py-3">
                  <Calendar size={14} className="text-muted-foreground shrink-0" />
                  <span className="text-xs text-muted-foreground w-12 shrink-0">방문 날짜</span>
                  <span className="text-sm font-semibold text-foreground ml-auto">{formatDateKorean(payload.date)}</span>
                </div>
                <div className="flex items-center gap-3 px-4 py-3">
                  <Clock size={14} className="text-muted-foreground shrink-0" />
                  <span className="text-xs text-muted-foreground w-12 shrink-0">입장 시간</span>
                  <span className="text-sm font-semibold text-foreground ml-auto">{payload.time}</span>
                </div>
              </div>
            </div>
          </div>

          {/* Visitor Info */}
          <div className="space-y-3">
            <SectionTitle>방문자 정보</SectionTitle>
            <div className="space-y-3">
              <InputField
                label="이름"
                placeholder="홍길동"
                value={name}
                onChange={setName}
              />
              <InputField
                label="휴대폰 번호"
                placeholder="010-0000-0000"
                type="tel"
                value={phone}
                onChange={setPhone}
              />
            </div>
          </div>

          {/* Payment Amount */}
          <div className="space-y-3">
            <SectionTitle>결제 금액</SectionTitle>
            <div className="bg-secondary rounded-2xl divide-y divide-border overflow-hidden">
              <div className="flex items-center justify-between px-4 py-3">
                <span className="text-sm text-muted-foreground">입장권 금액</span>
                <span className="text-sm font-semibold text-foreground">
                  {isFree ? '무료' : `${ticketPrice.toLocaleString()}원`}
                </span>
              </div>
              <div className="flex items-center justify-between px-4 py-3.5 bg-card">
                <span className="text-sm font-bold text-foreground">최종 결제 금액</span>
                <span className="text-base font-black text-foreground">
                  {isFree ? '무료' : `${ticketPrice.toLocaleString()}원`}
                </span>
              </div>
            </div>
          </div>

          {error && (
            <p className="text-center text-xs text-[oklch(0.62_0.24_25)]">{error}</p>
          )}

          {/* Terms note */}
          <p className="text-[11px] text-muted-foreground leading-relaxed px-1">
            예약 완료 후 취소 시 재예약이 제한될 수 있습니다. 방문 당일 예약 확인증을 제시해 주세요.
          </p>

          <div className="h-2" />
        </div>
      </div>

      {/* Fixed CTA */}
      <div className="px-4 py-3 border-t border-border bg-card shrink-0">
        <button
          disabled={!canProceed || submitting}
          onClick={handleSubmit}
          className={cn(
            'w-full py-4 rounded-xl font-bold text-sm transition-all',
            canProceed && !submitting
              ? 'bg-foreground text-background active:scale-[0.98]'
              : 'bg-secondary text-muted-foreground cursor-not-allowed',
          )}
        >
          {submitting ? (
            <span className="flex items-center justify-center gap-2">
              <Loader2 size={16} className="animate-spin" />
              예약 처리 중...
            </span>
          ) : isFree ? '무료 예약하기' : `결제하기 · ${ticketPrice.toLocaleString()}원`}
        </button>
      </div>
    </div>
  )
}
