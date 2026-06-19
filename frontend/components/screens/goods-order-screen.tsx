'use client'

import { useState } from 'react'
import { ArrowLeft, ChevronDown, MapPin } from 'lucide-react'
import { cn } from '@/lib/utils'
import { popupStores, claimedCoupons } from '@/lib/data'
import type { GoodsOrderPayload } from '@/lib/data'

interface GoodsOrderScreenProps {
  payload: GoodsOrderPayload
  onBack: () => void
  onComplete: () => void
}

// Available coupons: user's unused claimed coupons for this store
function getAvailableCoupons(storeId: string) {
  const store = popupStores.find((s) => s.id === storeId)
  if (!store) return []
  return claimedCoupons
    .filter((c) => !c.isUsed && c.storeName === store.name)
    .map((c) => ({
      id: c.id,
      label: c.title,
      discountText: c.discount,
      expiresAt: c.expiresAt,
    }))
}

function parseCouponDiscount(discountText: string, total: number): number {
  // e.g. "10% 할인" or "3,000원 할인"
  if (discountText.includes('%')) {
    const pct = parseFloat(discountText)
    return Math.round((total * pct) / 100)
  }
  const match = discountText.replace(/,/g, '').match(/(\d+)원/)
  if (match) return Math.min(parseInt(match[1], 10), total)
  return 0
}

export function GoodsOrderScreen({ payload, onBack, onComplete }: GoodsOrderScreenProps) {
  const store = popupStores.find((s) => s.id === payload.storeId)
  const [selectedCouponId, setSelectedCouponId] = useState<string | null>(null)
  const [showCouponPicker, setShowCouponPicker] = useState(false)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [postalCode, setPostalCode] = useState('')
  const [address, setAddress] = useState('')
  const [detailAddress, setDetailAddress] = useState('')

  if (!store) return null

  const availableCoupons = getAvailableCoupons(payload.storeId)
  const selectedCoupon = availableCoupons.find((c) => c.id === selectedCouponId) ?? null

  // Resolved cart items with product info
  const cartLines = payload.cart.map(({ goodsId, quantity }) => {
    const item = store.goods.find((g) => g.id === goodsId)!
    return { item, quantity, lineTotal: item.price * quantity }
  })

  const subtotal = cartLines.reduce((s, l) => s + l.lineTotal, 0)
  const discount = selectedCoupon ? parseCouponDiscount(selectedCoupon.discountText, subtotal) : 0
  const finalAmount = subtotal - discount

  const isFormValid = name.trim() && phone.trim() && postalCode.trim() && address.trim()

  return (
    <div className="flex flex-col h-full overflow-hidden">
      {/* Header */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-border bg-card shrink-0">
        <button
          onClick={onBack}
          className="flex items-center justify-center w-9 h-9 rounded-full hover:bg-secondary transition-colors"
          aria-label="뒤로 가기"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-base font-bold text-foreground">굿즈 주문</h1>
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-hide">
        <div className="p-4 space-y-5">

          {/* Store info */}
          <div className="flex items-center gap-3 bg-secondary rounded-xl p-3">
            <img
              src={store.image}
              alt={store.name}
              className="w-12 h-12 rounded-lg object-cover shrink-0"
            />
            <div className="min-w-0">
              <p className="text-[13px] font-bold text-foreground truncate">{store.name}</p>
              <p className="text-[11px] text-muted-foreground flex items-center gap-1 mt-0.5">
                <MapPin size={10} strokeWidth={1.8} />
                {store.location}
              </p>
            </div>
          </div>

          {/* Selected Products */}
          <section className="space-y-3">
            <h2 className="text-sm font-bold text-foreground">선택 상품</h2>
            <div className="divide-y divide-border rounded-xl overflow-hidden border border-border">
              {cartLines.map(({ item, quantity, lineTotal }) => (
                <div key={item.id} className="flex items-center gap-3 p-3 bg-card">
                  <img
                    src={item.image}
                    alt={item.name}
                    className="w-14 h-14 rounded-lg object-cover shrink-0"
                  />
                  <div className="flex-1 min-w-0 space-y-0.5">
                    <p className="text-[13px] font-semibold text-foreground leading-snug line-clamp-2">{item.name}</p>
                    <p className="text-[12px] text-muted-foreground">
                      {item.price.toLocaleString()}원 × {quantity}개
                    </p>
                  </div>
                  <p className="text-[14px] font-black text-foreground shrink-0">
                    {lineTotal.toLocaleString()}원
                  </p>
                </div>
              ))}
            </div>
          </section>

          {/* Coupon Section */}
          <section className="space-y-3">
            <h2 className="text-sm font-bold text-foreground">쿠폰 적용</h2>
            {availableCoupons.length === 0 ? (
              <div className="bg-secondary rounded-xl p-3 text-center">
                <p className="text-[13px] text-muted-foreground">사용 가능한 쿠폰이 없습니다.</p>
              </div>
            ) : (
              <div className="relative">
                <button
                  onClick={() => setShowCouponPicker((v) => !v)}
                  className="w-full flex items-center justify-between px-4 py-3.5 border-2 border-border rounded-xl bg-card text-left transition-colors hover:border-foreground/40"
                >
                  <span
                    className={cn(
                      'text-[13px] font-medium',
                      selectedCoupon ? 'text-foreground font-semibold' : 'text-muted-foreground',
                    )}
                  >
                    {selectedCoupon ? `${selectedCoupon.label} (${selectedCoupon.discountText})` : '쿠폰을 선택하세요'}
                  </span>
                  <ChevronDown
                    size={16}
                    className={cn('text-muted-foreground transition-transform', showCouponPicker && 'rotate-180')}
                  />
                </button>
                {showCouponPicker && (
                  <div className="absolute top-full left-0 right-0 z-10 mt-1 bg-card border border-border rounded-xl shadow-lg overflow-hidden">
                    <button
                      onClick={() => { setSelectedCouponId(null); setShowCouponPicker(false) }}
                      className="w-full px-4 py-3 text-left text-[13px] text-muted-foreground hover:bg-secondary transition-colors"
                    >
                      쿠폰 미적용
                    </button>
                    {availableCoupons.map((c) => (
                      <button
                        key={c.id}
                        onClick={() => { setSelectedCouponId(c.id); setShowCouponPicker(false) }}
                        className={cn(
                          'w-full px-4 py-3 text-left hover:bg-secondary transition-colors border-t border-border',
                          selectedCouponId === c.id && 'bg-secondary',
                        )}
                      >
                        <p className={cn('text-[13px] font-semibold', selectedCouponId === c.id ? 'text-foreground' : 'text-foreground/80')}>{c.label}</p>
                        <p className="text-[11px] text-[oklch(0.62_0.24_25)] font-bold mt-0.5">{c.discountText}</p>
                        <p className="text-[10px] text-muted-foreground mt-0.5">~ {c.expiresAt} 까지</p>
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
            {selectedCoupon && (
              <div className="flex items-center justify-between px-3 py-2 bg-[oklch(0.97_0.02_145)] rounded-lg border border-[oklch(0.88_0.06_145)]">
                <span className="text-[12px] text-[oklch(0.35_0.1_145)] font-medium">할인 적용</span>
                <span className="text-[13px] font-bold text-[oklch(0.35_0.1_145)]">-{discount.toLocaleString()}원</span>
              </div>
            )}
          </section>

          {/* Shipping Information */}
          <section className="space-y-3">
            <h2 className="text-sm font-bold text-foreground">배송 정보</h2>
            <div className="space-y-2.5">
              <div className="space-y-1">
                <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">수령인 이름</label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  placeholder="홍길동"
                  className="w-full px-4 py-3 rounded-xl border-2 border-border bg-card text-sm text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-foreground transition-colors"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">연락처</label>
                <input
                  type="tel"
                  value={phone}
                  onChange={(e) => setPhone(e.target.value)}
                  placeholder="010-0000-0000"
                  className="w-full px-4 py-3 rounded-xl border-2 border-border bg-card text-sm text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-foreground transition-colors"
                />
              </div>
              <div className="flex gap-2">
                <div className="flex-1 space-y-1">
                  <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">우편번호</label>
                  <input
                    type="text"
                    value={postalCode}
                    onChange={(e) => setPostalCode(e.target.value)}
                    placeholder="00000"
                    className="w-full px-4 py-3 rounded-xl border-2 border-border bg-card text-sm text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-foreground transition-colors"
                  />
                </div>
                <div className="space-y-1 shrink-0 flex flex-col">
                  <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide opacity-0 select-none">검색</label>
                  <button className="px-3 h-[46px] rounded-xl border-2 border-foreground bg-foreground text-background text-[12px] font-bold whitespace-nowrap hover:opacity-90 transition-opacity">
                    주소 검색
                  </button>
                </div>
              </div>
              <div className="space-y-1">
                <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">주소</label>
                <input
                  type="text"
                  value={address}
                  onChange={(e) => setAddress(e.target.value)}
                  placeholder="도로명 주소"
                  className="w-full px-4 py-3 rounded-xl border-2 border-border bg-card text-sm text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-foreground transition-colors"
                />
              </div>
              <div className="space-y-1">
                <label className="text-[11px] font-semibold text-muted-foreground uppercase tracking-wide">상세 주소</label>
                <input
                  type="text"
                  value={detailAddress}
                  onChange={(e) => setDetailAddress(e.target.value)}
                  placeholder="동/호수, 건물명 등"
                  className="w-full px-4 py-3 rounded-xl border-2 border-border bg-card text-sm text-foreground placeholder:text-muted-foreground/50 focus:outline-none focus:border-foreground transition-colors"
                />
              </div>
            </div>
          </section>

          {/* Payment Summary */}
          <section className="space-y-3">
            <h2 className="text-sm font-bold text-foreground">결제 금액</h2>
            <div className="bg-secondary rounded-xl p-4 space-y-2.5">
              <div className="flex items-center justify-between">
                <span className="text-[13px] text-muted-foreground">상품 금액</span>
                <span className="text-[13px] font-semibold text-foreground">{subtotal.toLocaleString()}원</span>
              </div>
              <div className="flex items-center justify-between">
                <span className="text-[13px] text-muted-foreground">쿠폰 할인</span>
                <span className={cn('text-[13px] font-semibold', discount > 0 ? 'text-[oklch(0.4_0.1_145)]' : 'text-muted-foreground')}>
                  {discount > 0 ? `-${discount.toLocaleString()}원` : '0원'}
                </span>
              </div>
              <div className="border-t border-border pt-2.5 flex items-center justify-between">
                <span className="text-[14px] font-bold text-foreground">최종 결제 금액</span>
                <span className="text-[16px] font-black text-foreground">{finalAmount.toLocaleString()}원</span>
              </div>
            </div>
          </section>

          <div className="h-2" />
        </div>
      </div>

      {/* Fixed CTA */}
      <div className="px-4 py-3 border-t border-border bg-card shrink-0">
        <button
          disabled={!isFormValid}
          onClick={onComplete}
          className={cn(
            'w-full py-4 rounded-xl font-bold text-sm flex items-center justify-between px-5 transition-all',
            isFormValid
              ? 'bg-foreground text-background active:scale-[0.98]'
              : 'bg-secondary text-muted-foreground cursor-not-allowed',
          )}
        >
          <span>결제하기</span>
          <span className="font-black">{finalAmount.toLocaleString()}원 ›</span>
        </button>
      </div>
    </div>
  )
}
