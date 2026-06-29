'use client'

import { useEffect, useState } from 'react'
import { Loader2, Ticket } from 'lucide-react'
import { couponApi, formatDiscount } from '@/lib/coupon-api'
import type { UserCouponResponse } from '@/lib/coupon-api'
import { cn } from '@/lib/utils'

export function CouponsScreen() {
  const [coupons, setCoupons] = useState<UserCouponResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    couponApi.getMyCoupons()
      .then((response) => {
        if (!cancelled) setCoupons(response.filter((coupon) => coupon.status !== 'USED'))
      })
      .catch((loadError: Error) => {
        if (!cancelled) setError(loadError.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <header className="px-4 py-3 bg-card border-b border-border shrink-0">
        <h1 className="text-base font-bold text-foreground">내 쿠폰</h1>
        <p className="text-[11px] text-muted-foreground mt-0.5">발급받은 쿠폰 목록</p>
      </header>

      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <Loader2 size={24} className="animate-spin text-muted-foreground" />
          </div>
        ) : error ? (
          <div className="flex items-center justify-center h-full px-8 text-center text-sm text-[oklch(0.62_0.24_25)]">
            {error}
          </div>
        ) : coupons.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-muted-foreground py-24">
            <Ticket size={40} strokeWidth={1.4} />
            <p className="text-sm font-medium">보유한 쿠폰이 없습니다.</p>
            <p className="text-[12px] text-muted-foreground">팝업 상세 페이지에서 쿠폰을 받아보세요</p>
          </div>
        ) : (
          <div className="space-y-2 px-4 pt-4">
            {coupons.map((coupon) => {
              const isExpired = coupon.status === 'EXPIRED'

              return (
                <div
                  key={coupon.id}
                  className={cn(
                    'flex items-center gap-3 bg-card rounded-xl border-2 border-foreground/15 p-3.5',
                    isExpired && 'bg-secondary/70 border-border opacity-70',
                  )}
                >
                  <div className={cn(
                    'w-10 h-10 rounded-lg flex items-center justify-center shrink-0 bg-[oklch(0.94_0.04_145)]',
                    isExpired && 'bg-border',
                  )}>
                    <Ticket size={18} className={isExpired ? 'text-muted-foreground' : 'text-[oklch(0.4_0.1_145)]'} />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className={cn('text-[13px] font-semibold text-foreground line-clamp-1', isExpired && 'text-muted-foreground')}>
                      {coupon.name}
                    </p>
                    <p className="text-[11px] text-muted-foreground mt-0.5">{coupon.popupStoreTitle}</p>
                    <p className="text-[11px] text-muted-foreground mt-0.5">~{coupon.expiredAt.slice(0, 10)} 까지</p>
                  </div>
                  <div className="text-right shrink-0 space-y-1">
                    <p className={cn('text-[14px] font-black text-[oklch(0.62_0.24_25)]', isExpired && 'text-muted-foreground')}>
                      {formatDiscount(coupon)}
                    </p>
                    <span className={cn(
                      'inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold bg-[oklch(0.94_0.04_145)] text-[oklch(0.3_0.1_145)]',
                      isExpired && 'bg-border text-muted-foreground',
                    )}>
                      {isExpired ? '사용기한 만료' : '미사용'}
                    </span>
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
