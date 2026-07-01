'use client'

import { useCallback, useEffect, useState } from 'react'
import { CalendarCheck, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { StatusBadge } from '@/components/ui/status-badge'
import { reservationApi } from '@/lib/reservation-api'
import type { MyReservationResponse } from '@/lib/reservation-api'
import type { ReservationHistoryStatus } from '@/lib/data'

function toHistoryStatus(status: MyReservationResponse['status']): ReservationHistoryStatus {
  return status === 'CONFIRMED' ? '예약 완료' : '예약 취소'
}

function formatDate(date: string) {
  return date.replaceAll('-', '.')
}

function formatTime(time: string) {
  return time.slice(0, 5)
}

function reservationNumber(reservationId: number) {
  return `RSV-${reservationId}`
}

export function ReservationsScreen() {
  const [reservations, setReservations] = useState<MyReservationResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [cancelingId, setCancelingId] = useState<number | null>(null)

  const loadReservations = useCallback(async () => {
    setLoading(true)
    try {
      const response = await reservationApi.getMyReservations()
      setReservations(response.content ?? [])
      setError(null)
    } catch (e) {
      setError(e instanceof Error ? e.message : '예약 내역을 불러오지 못했습니다.')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadReservations()
  }, [loadReservations])

  async function handleCancel(reservationId: number) {
    if (cancelingId !== null) return
    if (!window.confirm('예약을 취소하시겠습니까?')) return

    setCancelingId(reservationId)
    try {
      await reservationApi.cancelReservation(reservationId)
      await loadReservations()
    } catch (e) {
      alert(e instanceof Error ? e.message : '예약 취소에 실패했습니다.')
    } finally {
      setCancelingId(null)
    }
  }

  const upcoming = reservations.filter((r) => r.status === 'CONFIRMED')
  const past = reservations.filter((r) => r.status !== 'CONFIRMED')

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <header className="px-4 py-3 bg-card border-b border-border">
        <h1 className="text-base font-bold text-foreground">내 예약</h1>
      </header>

      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">
        {loading && (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-muted-foreground py-24">
            <Loader2 size={32} className="animate-spin" />
            <p className="text-sm font-medium">예약 내역을 불러오는 중...</p>
          </div>
        )}

        {!loading && error && (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-muted-foreground py-24 px-6 text-center">
            <CalendarCheck size={40} strokeWidth={1.4} />
            <p className="text-sm font-medium">{error}</p>
            <button
              onClick={() => void loadReservations()}
              className="px-4 py-2 rounded-xl bg-secondary text-foreground text-sm font-semibold"
            >
              다시 시도
            </button>
          </div>
        )}

        {!loading && !error && upcoming.length > 0 && (
          <div>
            <p className="px-4 pt-5 pb-2 text-[11px] font-bold text-muted-foreground tracking-wider uppercase">예정된 방문</p>
            <div className="space-y-3 px-4">
              {upcoming.map((r) => (
                <div key={r.reservationId} className="bg-foreground rounded-2xl p-4 text-background">
                  <div className="space-y-0.5">
                    <p className="text-[13px] font-bold leading-snug line-clamp-2 text-background">{r.popupName}</p>
                    <p className="text-[11px] text-background/70">{r.location}</p>
                    <p className="text-[11px] text-background/70">{formatDate(r.reservationDate)} · {formatTime(r.reservationTime)} 입장</p>
                    <p className="text-[10px] text-background/50 mt-0.5">{reservationNumber(r.reservationId)}</p>
                  </div>
                  <div className="mt-3 pt-3 border-t border-white/20 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      {r.price > 0 ? (
                        <span className="text-[12px] font-bold text-background">{r.price.toLocaleString()}원</span>
                      ) : (
                        <span className="text-[12px] text-background/60">무료 예약</span>
                      )}
                    </div>
                    <button
                      disabled={cancelingId === r.reservationId}
                      onClick={() => void handleCancel(r.reservationId)}
                      className="py-1.5 px-4 bg-white/10 rounded-lg text-[12px] font-semibold text-background disabled:opacity-50"
                    >
                      {cancelingId === r.reservationId ? '취소 중' : '예약 취소'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {!loading && !error && past.length > 0 && (
          <div>
            <p className="px-4 pt-5 pb-2 text-[11px] font-bold text-muted-foreground tracking-wider uppercase">이전 방문</p>
            <div className="space-y-2 px-4">
              {past.map((r) => {
                const status = toHistoryStatus(r.status)
                return (
                  <div
                    key={r.reservationId}
                    className={cn(
                      'bg-card rounded-xl border border-border p-3',
                      status === '예약 취소' && 'opacity-60',
                    )}
                  >
                    <div className="flex items-center gap-3">
                      <div className="flex-1 min-w-0">
                        <p className="text-[12px] font-semibold text-foreground line-clamp-1">{r.popupName}</p>
                        <p className="text-[11px] text-muted-foreground mt-0.5">{r.location}</p>
                        <p className="text-[11px] text-muted-foreground mt-0.5">
                          {formatDate(r.reservationDate)} · {formatTime(r.reservationTime)}
                        </p>
                      </div>
                      <div className="text-right shrink-0 space-y-1.5">
                        <StatusBadge status={status} size="sm" />
                        {r.price > 0 ? (
                          <p className="text-[11px] font-bold text-foreground">{r.price.toLocaleString()}원</p>
                        ) : (
                          <p className="text-[11px] text-muted-foreground">무료</p>
                        )}
                      </div>
                    </div>
                    <div className="mt-2 pt-2 border-t border-border">
                      <p className="text-[10px] text-muted-foreground">{reservationNumber(r.reservationId)}</p>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        )}

        {!loading && !error && reservations.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-muted-foreground py-24">
            <CalendarCheck size={40} strokeWidth={1.4} />
            <p className="text-sm font-medium">예약 내역이 없습니다</p>
          </div>
        )}
      </div>
    </div>
  )
}
