'use client'

import { CheckCircle, Calendar, Clock, MapPin, Home, Ticket } from 'lucide-react'
import { popupStores, formatDateKorean } from '@/lib/data'
import type { ReservationPayload } from '@/lib/data'
import type { ReservationPaymentResponse } from '@/lib/reservation-api'

interface ReservationCompleteScreenProps {
  payload: ReservationPayload
  reservation: ReservationPaymentResponse | null
  onGoHome: () => void
  onGoReservations: () => void
}

export function ReservationCompleteScreen({
  payload,
  reservation,
  onGoHome,
  onGoReservations,
}: ReservationCompleteScreenProps) {
  const store = popupStores.find((s) => s.id === payload.storeId)
  if (!store) return null

  const popupName = reservation?.popupName ?? store.name
  const location = reservation?.location ?? store.location
  const reservationDate = reservation?.reservationDate ?? payload.date
  const reservationTime = reservation?.reservationTime?.slice(0, 5) ?? payload.time
  const reservationNumber = reservation?.reservationId ? `RSV-${reservation.reservationId}` : null

  return (
    <div className="flex flex-col h-full overflow-hidden bg-background">
      <div className="flex-1 overflow-y-auto scrollbar-hide">
        <div className="flex flex-col items-center px-6 pt-14 pb-8 text-center">
          {/* Success icon */}
          <div className="flex items-center justify-center w-20 h-20 rounded-full bg-[oklch(0.94_0.04_145)] mb-5">
            <CheckCircle size={40} strokeWidth={2} className="text-[oklch(0.4_0.1_145)]" />
          </div>

          <h1 className="text-2xl font-black text-foreground text-balance">예약 완료!</h1>
          <p className="text-sm text-muted-foreground mt-2 text-balance">
            예약이 성공적으로 완료되었습니다.<br />당일 현장에서 예약 확인증을 제시해주세요.
          </p>

          {/* Ticket card */}
          <div className="w-full mt-8 bg-secondary rounded-2xl overflow-hidden border border-border text-left">
            <div className="px-4 py-4 bg-foreground text-background">
              <p className="text-base font-black leading-tight text-balance">{popupName}</p>
            </div>

            {/* Ticket dashes */}
            <div className="flex items-center px-4 py-0.5">
              <div className="w-5 h-5 rounded-full bg-background -ml-7 shrink-0" />
              <div className="flex-1 border-t-2 border-dashed border-border mx-2" />
              <div className="w-5 h-5 rounded-full bg-background -mr-7 shrink-0" />
            </div>

            {/* Details */}
            <div className="px-4 py-4 space-y-3">
              <div className="flex items-center gap-3">
                <MapPin size={14} className="text-muted-foreground shrink-0" />
                <span className="text-xs text-muted-foreground w-16 shrink-0">장소</span>
                <span className="text-sm font-semibold text-foreground text-right flex-1">{location}</span>
              </div>
              <div className="flex items-center gap-3">
                <Calendar size={14} className="text-muted-foreground shrink-0" />
                <span className="text-xs text-muted-foreground w-16 shrink-0">방문 날짜</span>
                <span className="text-sm font-semibold text-foreground text-right flex-1">{formatDateKorean(reservationDate)}</span>
              </div>
              <div className="flex items-center gap-3">
                <Clock size={14} className="text-muted-foreground shrink-0" />
                <span className="text-xs text-muted-foreground w-16 shrink-0">입장 시간</span>
                <span className="text-sm font-semibold text-foreground text-right flex-1">{reservationTime}</span>
              </div>
              {reservationNumber && (
                <div className="flex items-center gap-3">
                  <Ticket size={14} className="text-muted-foreground shrink-0" />
                  <span className="text-xs text-muted-foreground w-16 shrink-0">예약 번호</span>
                  <span className="text-sm font-semibold text-foreground text-right flex-1">{reservationNumber}</span>
                </div>
              )}
            </div>
          </div>
        </div>
      </div>

      {/* Fixed bottom actions */}
      <div className="px-4 py-3 border-t border-border bg-card shrink-0 space-y-2.5">
        <button
          onClick={onGoReservations}
          className="w-full py-4 rounded-xl bg-foreground text-background font-bold text-sm active:scale-[0.98] transition-all flex items-center justify-center gap-2"
        >
          <Ticket size={16} />
          내 예약 확인하기
        </button>
        <button
          onClick={onGoHome}
          className="w-full py-3 rounded-xl bg-secondary text-foreground font-semibold text-sm active:scale-[0.98] transition-all flex items-center justify-center gap-2"
        >
          <Home size={15} />
          홈으로 돌아가기
        </button>
      </div>
    </div>
  )
}
