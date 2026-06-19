'use client'

import { Ticket } from 'lucide-react'
import { claimedCoupons } from '@/lib/data'

export function CouponsScreen() {
  // Per spec: only show coupons with ISSUED (미사용) status
  const issuedCoupons = claimedCoupons.filter((c) => !c.isUsed)

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <header className="px-4 py-3 bg-card border-b border-border shrink-0">
        <h1 className="text-base font-bold text-foreground">내 쿠폰</h1>
        <p className="text-[11px] text-muted-foreground mt-0.5">사용 가능한 쿠폰 목록</p>
      </header>

      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">
        {issuedCoupons.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-muted-foreground py-24">
            <Ticket size={40} strokeWidth={1.4} />
            <p className="text-sm font-medium">보유한 쿠폰이 없습니다.</p>
            <p className="text-[12px] text-muted-foreground">팝업 상세 페이지에서 쿠폰을 받아보세요</p>
          </div>
        ) : (
          <div className="space-y-2 px-4 pt-4">
            {issuedCoupons.map((coupon) => (
              <div
                key={coupon.id}
                className="flex items-center gap-3 bg-card rounded-xl border-2 border-foreground/15 p-3.5"
              >
                <div className="w-10 h-10 rounded-lg flex items-center justify-center shrink-0 bg-[oklch(0.94_0.04_145)]">
                  <Ticket size={18} className="text-[oklch(0.4_0.1_145)]" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-[13px] font-semibold text-foreground line-clamp-1">{coupon.title}</p>
                  <p className="text-[11px] text-muted-foreground mt-0.5">{coupon.storeName}</p>
                  <p className="text-[11px] text-muted-foreground mt-0.5">~{coupon.expiresAt} 까지</p>
                </div>
                <div className="text-right shrink-0 space-y-1">
                  <p className="text-[14px] font-black text-[oklch(0.62_0.24_25)]">{coupon.discount}</p>
                  <span className="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-semibold bg-[oklch(0.94_0.04_145)] text-[oklch(0.3_0.1_145)]">
                    미사용
                  </span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
