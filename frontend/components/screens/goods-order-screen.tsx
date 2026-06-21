'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter } from 'next/navigation'
import { ArrowLeft, Loader2, MapPin } from 'lucide-react'
import { cn } from '@/lib/utils'
import { popupStores } from '@/lib/data'
import type { GoodsOrderPayload } from '@/lib/data'
import { goodsApi } from '@/lib/goods-api'
import type { GoodsSummaryResponse } from '@/lib/goods-api'
import { savePendingPayment } from '@/lib/payment-api'

interface GoodsOrderScreenProps {
  payload: GoodsOrderPayload
  onBack: () => void
}

export function GoodsOrderScreen({ payload, onBack }: GoodsOrderScreenProps) {
  const router = useRouter()
  const store = popupStores.find((s) => s.id === payload.storeId)
  const [goods, setGoods] = useState<GoodsSummaryResponse[]>([])
  const [loadingGoods, setLoadingGoods] = useState(true)
  const [name, setName] = useState('')
  const [phone, setPhone] = useState('')
  const [postalCode, setPostalCode] = useState('')
  const [address, setAddress] = useState('')
  const [detailAddress, setDetailAddress] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const idempotencyKeyRef = useRef<string | null>(null)

  useEffect(() => {
    let cancelled = false
    goodsApi.getByPopup(payload.storeId)
      .then((response) => {
        if (!cancelled) setGoods(response.content)
      })
      .catch((loadError: Error) => {
        if (!cancelled) setError(loadError.message)
      })
      .finally(() => {
        if (!cancelled) setLoadingGoods(false)
      })

    return () => {
      cancelled = true
    }
  }, [payload.storeId])

  if (!store) return null

  // Resolved cart items with product info
  const cartLines = payload.cart.flatMap(({ goodsId, quantity }) => {
    const item = goods.find((candidate) => String(candidate.goodsId) === goodsId)
    return item ? [{ item, quantity, lineTotal: item.price * quantity }] : []
  })

  const subtotal = cartLines.reduce((s, l) => s + l.lineTotal, 0)
  const finalAmount = subtotal

  const isFormValid = Boolean(
    name.trim() && phone.trim() && postalCode.trim() && address.trim() && cartLines.length,
  )

  async function handleSubmit() {
    if (!isFormValid || submitting) return

    setSubmitting(true)
    setError(null)
    try {
      if (!idempotencyKeyRef.current) idempotencyKeyRef.current = crypto.randomUUID()
      const order = await goodsApi.createOrder({
        items: payload.cart.map((item) => ({
          goodsId: Number(item.goodsId),
          quantity: item.quantity,
        })),
        couponId: null,
        idempotencyKey: idempotencyKeyRef.current,
        receiverName: name.trim(),
        receiverPhone: phone.trim(),
        postalCode: postalCode.trim(),
        address: address.trim(),
        addressDetail: detailAddress.trim() || null,
      })

      savePendingPayment({
        orderId: order.orderId,
        orderName: order.orderName,
        amount: order.amount,
        customerName: name.trim(),
      })
      router.push('/payments/checkout')
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : '굿즈 주문을 생성하지 못했습니다.')
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
                <div key={item.goodsId} className="flex items-center gap-3 p-3 bg-card">
                  <img
                    src={item.thumbnailImageUrl ?? '/placeholder.jpg'}
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
            <div className="bg-secondary rounded-xl p-3 text-center">
              <p className="text-[13px] text-muted-foreground">굿즈 주문 쿠폰 적용은 준비 중입니다.</p>
            </div>
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
                <span className="text-[13px] font-semibold text-muted-foreground">
                  0원
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

      {error && (
        <p className="px-4 pb-2 text-center text-xs text-[oklch(0.62_0.24_25)]">{error}</p>
      )}

      {/* Fixed CTA */}
      <div className="px-4 py-3 border-t border-border bg-card shrink-0">
        <button
          disabled={!isFormValid || submitting || loadingGoods}
          onClick={handleSubmit}
          className={cn(
            'w-full py-4 rounded-xl font-bold text-sm flex items-center justify-between px-5 transition-all',
            isFormValid && !submitting && !loadingGoods
              ? 'bg-foreground text-background active:scale-[0.98]'
              : 'bg-secondary text-muted-foreground cursor-not-allowed',
          )}
        >
          <span>{submitting ? '주문 생성 중...' : loadingGoods ? '굿즈 확인 중...' : '결제하기'}</span>
          {submitting ? (
            <Loader2 size={16} className="animate-spin" />
          ) : (
            <span className="font-black">{finalAmount.toLocaleString()}원 ›</span>
          )}
        </button>
      </div>
    </div>
  )
}
