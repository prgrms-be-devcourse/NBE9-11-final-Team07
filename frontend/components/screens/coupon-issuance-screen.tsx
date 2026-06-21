'use client'

import { useEffect, useState } from 'react'
import { ArrowLeft, TicketIcon, CheckCircle, Loader2, Tag, Clock, ShoppingCart } from 'lucide-react'
import { cn } from '@/lib/utils'
import { popupStores } from '@/lib/data'
import type { CouponIssuancePayload } from '@/lib/data'
import { ApiError } from '@/lib/api'
import { couponApi, formatDiscount } from '@/lib/coupon-api'
import type { CouponResponse } from '@/lib/coupon-api'

type IssuanceState = 'loading' | 'available' | 'issuing' | 'issued' | 'already-issued' | 'sold-out'

interface CouponIssuanceScreenProps {
  payload: CouponIssuancePayload
  onBack: () => void
  onGoMyCoupons: () => void
}

export function CouponIssuanceScreen({ payload, onBack, onGoMyCoupons }: CouponIssuanceScreenProps) {
  const store = popupStores.find((s) => s.id === payload.storeId)
  const [coupon, setCoupon] = useState<CouponResponse | null>(null)
  const [state, setState] = useState<IssuanceState>('loading')
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false

    async function loadCoupon() {
      try {
        const coupons = await couponApi.getPublicCoupons(payload.storeId)
        const current = coupons.find((item) => String(item.id) === payload.couponId)
        if (!current) throw new Error('발급 가능한 쿠폰을 찾을 수 없습니다.')

        let alreadyIssued = false
        try {
          const myCoupons = await couponApi.getMyCoupons()
          alreadyIssued = myCoupons.some((item) => item.couponId === current.id)
        } catch (myCouponsError) {
          if (!(myCouponsError instanceof ApiError) || myCouponsError.status !== 401) {
            throw myCouponsError
          }
        }

        if (!cancelled) {
          setCoupon(current)
          setState(alreadyIssued ? 'already-issued' : 'available')
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError instanceof Error ? loadError.message : '쿠폰을 불러오지 못했습니다.')
          setState('sold-out')
        }
      }
    }

    loadCoupon()
    return () => {
      cancelled = true
    }
  }, [payload.couponId, payload.storeId])

  async function handleClaim() {
    setState('issuing')
    setError(null)
    try {
      await couponApi.issueCoupon(payload.couponId)
      setState('issued')
    } catch (issueError) {
      if (issueError instanceof ApiError && issueError.code === 'COUPON_ALREADY_ISSUED') {
        setState('already-issued')
      } else {
        setState('available')
        setError(
          issueError instanceof ApiError && issueError.status === 401
            ? '로그인 후 쿠폰을 발급받을 수 있습니다.'
            : issueError instanceof Error
              ? issueError.message
              : '쿠폰 발급에 실패했습니다.',
        )
      }
    }
  }

  if (!store) return null

  if (!coupon) {
    return (
      <div className="flex flex-col h-full">
        <div className="flex items-center gap-3 px-4 py-3 border-b border-border bg-card">
          <button onClick={onBack} className="w-9 h-9" aria-label="뒤로 가기"><ArrowLeft size={20} /></button>
          <h1 className="text-base font-bold">쿠폰 발급</h1>
        </div>
        <div className="flex-1 flex items-center justify-center px-6 text-center text-sm text-muted-foreground">
          {state === 'loading' ? <Loader2 size={24} className="animate-spin" /> : error}
        </div>
      </div>
    )
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
              coupon.status !== 'ACTIVE' ? 'bg-secondary' : 'bg-[oklch(0.62_0.24_25)]'
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
                  coupon.status !== 'ACTIVE' ? 'bg-secondary' : 'bg-[oklch(0.97_0.03_25)]'
                )}>
                  <TicketIcon
                    size={22}
                    className={coupon.status !== 'ACTIVE' ? 'text-muted-foreground' : 'text-[oklch(0.62_0.24_25)]'}
                  />
                </div>
                <div className="flex-1 min-w-0">
                  <h2 className="text-[15px] font-bold text-foreground leading-snug text-balance">{coupon.name}</h2>
                  <p className="text-[22px] font-black text-[oklch(0.62_0.24_25)] mt-1 leading-none">{formatDiscount(coupon)}</p>
                </div>
              </div>

              {/* Divider */}
              <div className="border-t border-dashed border-border" />

              {/* Details */}
              <div className="space-y-2.5">
                <div className="flex items-center gap-2.5">
                  <ShoppingCart size={13} className="text-muted-foreground shrink-0" />
                  <span className="text-[12px] text-muted-foreground">최소 주문 금액</span>
                  <span className="text-[12px] font-semibold text-foreground ml-auto">
                    {coupon.minOrderAmount ? `${coupon.minOrderAmount.toLocaleString()}원 이상` : '없음'}
                  </span>
                </div>
                <div className="flex items-center gap-2.5">
                  <Clock size={13} className="text-muted-foreground shrink-0" />
                  <span className="text-[12px] text-muted-foreground">유효 기간</span>
                  <span className="text-[12px] font-semibold text-foreground ml-auto">~ {coupon.expiredAt.slice(0, 10)}</span>
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

          {error && state !== 'sold-out' && (
            <p className="text-[12px] text-[oklch(0.62_0.24_25)] text-center">{error}</p>
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
