'use client'

import { useState } from 'react'
import { ArrowLeft, TicketIcon, CheckCircle, Loader2, Tag, Clock, ShoppingCart } from 'lucide-react'
import { cn } from '@/lib/utils'
import { popupStores, claimedCoupons } from '@/lib/data'
import type { CouponIssuancePayload } from '@/lib/data'

type IssuanceState = 'available' | 'issuing' | 'issued' | 'already-issued' | 'sold-out'

interface CouponIssuanceScreenProps {
  payload: CouponIssuancePayload
  onBack: () => void
  onGoMyCoupons: () => void
}

export function CouponIssuanceScreen({ payload, onBack, onGoMyCoupons }: CouponIssuanceScreenProps) {
  const store = popupStores.find((s) => s.id === payload.storeId)
  const coupon = store?.coupons.find((c) => c.id === payload.couponId)

  // Determine initial state from data
  function getInitialState(): IssuanceState {
    if (!coupon) return 'sold-out'
    if (coupon.status === '소진') return 'sold-out'
    const alreadyOwned = claimedCoupons.some((cc) => cc.id === payload.couponId && !cc.isUsed)
    if (alreadyOwned) return 'already-issued'
    return 'available'
  }

  const [state, setState] = useState<IssuanceState>(getInitialState)

  if (!store || !coupon) return null

  function handleClaim() {
    setState('issuing')
    setTimeout(() => {
      setState('issued')
    }, 1500)
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
        <h1 className="text-base font-bold text-foreground">쿠폰 발급</h1>
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-hide">
        <div className="p-4 space-y-4">

          {/* Store name */}
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-lg overflow-hidden bg-secondary shrink-0">
              <img src={store.image} alt={store.name} className="w-full h-full object-cover" />
            </div>
            <p className="text-sm font-semibold text-muted-foreground truncate">{store.name}</p>
          </div>

          {/* Coupon Card */}
          <div className="relative bg-card border-2 border-foreground/10 rounded-2xl overflow-hidden">
            {/* Top accent bar */}
            <div className={cn(
              'h-1.5 w-full',
              coupon.status === '소진' ? 'bg-secondary' : 'bg-[oklch(0.62_0.24_25)]'
            )} />

            {/* Tear perforation */}
            <div className="flex items-center px-4 py-0">
              <div className="flex-1 border-t-2 border-dashed border-border" />
              <div className="w-3 h-3 rounded-full bg-[oklch(0.94_0_0)] border border-border mx-2 shrink-0" />
              <div className="flex-1 border-t-2 border-dashed border-border" />
            </div>

            <div className="px-5 py-5 space-y-4">
              {/* Icon + title + discount */}
              <div className="flex items-start gap-4">
                <div className={cn(
                  'flex items-center justify-center w-12 h-12 rounded-xl shrink-0',
                  coupon.status === '소진' ? 'bg-secondary' : 'bg-[oklch(0.97_0.03_25)]'
                )}>
                  <TicketIcon
                    size={22}
                    className={coupon.status === '소진' ? 'text-muted-foreground' : 'text-[oklch(0.62_0.24_25)]'}
                  />
                </div>
                <div className="flex-1 min-w-0">
                  <h2 className="text-[15px] font-bold text-foreground leading-snug text-balance">{coupon.title}</h2>
                  <p className="text-[22px] font-black text-[oklch(0.62_0.24_25)] mt-1 leading-none">{coupon.discount}</p>
                </div>
              </div>

              {/* Divider */}
              <div className="border-t border-dashed border-border" />

              {/* Details */}
              <div className="space-y-2.5">
                <div className="flex items-center gap-2.5">
                  <ShoppingCart size={13} className="text-muted-foreground shrink-0" />
                  <span className="text-[12px] text-muted-foreground">최소 주문 금액</span>
                  <span className="text-[12px] font-semibold text-foreground ml-auto">{coupon.minOrder}</span>
                </div>
                <div className="flex items-center gap-2.5">
                  <Clock size={13} className="text-muted-foreground shrink-0" />
                  <span className="text-[12px] text-muted-foreground">유효 기간</span>
                  <span className="text-[12px] font-semibold text-foreground ml-auto">~ {coupon.expiresAt}</span>
                </div>
                <div className="flex items-center gap-2.5">
                  <Tag size={13} className="text-muted-foreground shrink-0" />
                  <span className="text-[12px] text-muted-foreground">사용 가능 장소</span>
                  <span className="text-[12px] font-semibold text-foreground ml-auto">현장 결제 시</span>
                </div>
              </div>
            </div>
          </div>

          {/* State-specific feedback */}
          {state === 'issued' && (
            <div className="flex items-center gap-3 bg-[oklch(0.94_0.06_145)] rounded-xl p-4">
              <CheckCircle size={20} className="text-[oklch(0.3_0.1_145)] shrink-0" />
              <div>
                <p className="text-[13px] font-bold text-[oklch(0.3_0.1_145)]">쿠폰이 발급되었습니다!</p>
                <p className="text-[11px] text-[oklch(0.4_0.08_145)] mt-0.5">내 쿠폰 목록에서 확인하실 수 있습니다.</p>
              </div>
            </div>
          )}

          {state === 'already-issued' && (
            <div className="flex items-center gap-3 bg-[oklch(0.97_0.06_58)] rounded-xl p-4">
              <TicketIcon size={20} className="text-[oklch(0.45_0.15_58)] shrink-0" />
              <p className="text-[13px] font-semibold text-[oklch(0.45_0.15_58)]">이미 보유한 쿠폰입니다.</p>
            </div>
          )}

          {state === 'sold-out' && (
            <div className="flex items-center gap-3 bg-secondary rounded-xl p-4">
              <TicketIcon size={20} className="text-muted-foreground shrink-0" />
              <p className="text-[13px] font-semibold text-muted-foreground">쿠폰이 모두 소진되었습니다.</p>
            </div>
          )}

        </div>
      </div>

      {/* Fixed CTA */}
      <div className="px-4 py-3 border-t border-border bg-card shrink-0 space-y-2">
        {state === 'issued' && (
          <button
            onClick={onGoMyCoupons}
            className="w-full py-3.5 rounded-xl border-2 border-foreground text-foreground font-bold text-sm hover:bg-secondary transition-colors"
          >
            내 쿠폰 보러 가기
          </button>
        )}
        <button
          disabled={state !== 'available' && state !== 'issuing'}
          onClick={state === 'available' ? handleClaim : undefined}
          className={cn(
            'w-full py-4 rounded-xl font-bold text-sm transition-all flex items-center justify-center gap-2',
            state === 'available'
              ? 'bg-foreground text-background active:scale-[0.98]'
              : state === 'issuing'
              ? 'bg-foreground/80 text-background cursor-wait'
              : 'bg-secondary text-muted-foreground cursor-not-allowed',
          )}
        >
          {state === 'issuing' && <Loader2 size={16} className="animate-spin" />}
          {state === 'available' && '쿠폰 받기'}
          {state === 'issuing' && '발급 중...'}
          {state === 'issued' && '발급 완료'}
          {state === 'already-issued' && '이미 보유한 쿠폰'}
          {state === 'sold-out' && '쿠폰 소진'}
        </button>
      </div>
    </div>
  )
}
