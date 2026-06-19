'use client'

import { CalendarCheck } from 'lucide-react'
import { cn } from '@/lib/utils'
import { StatusBadge } from '@/components/ui/status-badge'
import { reservationHistory } from '@/lib/data'

export function ReservationsScreen() {
  const upcoming = reservationHistory.filter((r) => r.status === '예약 완료')
  const past = reservationHistory.filter((r) => r.status !== '예약 완료')

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <header className="px-4 py-3 bg-card border-b border-border">
        <h1 className="text-base font-bold text-foreground">내 예약</h1>
      </header>

      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">

        {upcoming.length > 0 && (
          <div>
            <p className="px-4 pt-5 pb-2 text-[11px] font-bold text-muted-foreground tracking-wider uppercase">예정된 방문</p>
            <div className="space-y-3 px-4">
              {upcoming.map((r) => (
                <div key={r.id} className="bg-foreground rounded-2xl p-4 text-background">
                  <div className="flex items-start gap-3">
                    <img
                      src={r.storeImage}
                      alt={r.storeName}
                      className="w-14 h-14 rounded-xl object-cover shrink-0"
                    />
                    <div className="flex-1 min-w-0 space-y-0.5">
                      <p className="text-[13px] font-bold leading-snug line-clamp-2 text-background">{r.storeName}</p>
                      <p className="text-[11px] text-background/70">{r.location}</p>
                      <p className="text-[11px] text-background/70">{r.date} · {r.timeSlot} 입장</p>
                      <p className="text-[10px] text-background/50 mt-0.5">{r.reservationNumber}</p>
                    </div>
                  </div>
                  <div className="mt-3 pt-3 border-t border-white/20 flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      {r.price > 0 ? (
                        <span className="text-[12px] font-bold text-background">{r.price.toLocaleString()}원</span>
                      ) : (
                        <span className="text-[12px] text-background/60">무료 예약</span>
                      )}
                    </div>
                    <div className="flex gap-2">
                      <button className="py-1.5 px-4 bg-white/10 rounded-lg text-[12px] font-semibold text-background">
                        예약 취소
                      </button>
                      <button className="py-1.5 px-4 bg-white rounded-lg text-[12px] font-semibold text-foreground">
                        QR 코드
                      </button>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {past.length > 0 && (
          <div>
            <p className="px-4 pt-5 pb-2 text-[11px] font-bold text-muted-foreground tracking-wider uppercase">이전 방문</p>
            <div className="space-y-2 px-4">
              {past.map((r) => (
                <div
                  key={r.id}
                  className={cn(
                    'bg-card rounded-xl border border-border p-3',
                    r.status === '예약 취소' && 'opacity-60',
                  )}
                >
                  <div className="flex items-center gap-3">
                    <img
                      src={r.storeImage}
                      alt={r.storeName}
                      className="w-12 h-12 rounded-lg object-cover shrink-0"
                    />
                    <div className="flex-1 min-w-0">
                      <p className="text-[12px] font-semibold text-foreground line-clamp-1">{r.storeName}</p>
                      <p className="text-[11px] text-muted-foreground mt-0.5">{r.location}</p>
                      <p className="text-[11px] text-muted-foreground mt-0.5">
                        {r.date} · {r.timeSlot}
                      </p>
                    </div>
                    <div className="text-right shrink-0 space-y-1.5">
                      <StatusBadge status={r.status} size="sm" />
                      {r.price > 0 ? (
                        <p className="text-[11px] font-bold text-foreground">{r.price.toLocaleString()}원</p>
                      ) : (
                        <p className="text-[11px] text-muted-foreground">무료</p>
                      )}
                    </div>
                  </div>
                  <div className="mt-2 pt-2 border-t border-border flex items-center justify-between">
                    <p className="text-[10px] text-muted-foreground">{r.reservationNumber}</p>
                    {r.status !== '예약 취소' && (
                      <button className="text-[11px] text-muted-foreground underline underline-offset-2">
                        예약 취소
                      </button>
                    )}
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {reservationHistory.length === 0 && (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-muted-foreground py-24">
            <CalendarCheck size={40} strokeWidth={1.4} />
            <p className="text-sm font-medium">예약 내역이 없습니다</p>
          </div>
        )}
      </div>
    </div>
  )
}
