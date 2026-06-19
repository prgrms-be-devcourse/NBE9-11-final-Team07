'use client'

import { CheckCircle, ShoppingBag, Home } from 'lucide-react'
import { popupStores } from '@/lib/data'
import type { GoodsOrderPayload } from '@/lib/data'

interface GoodsOrderCompleteScreenProps {
  payload: GoodsOrderPayload
  onGoHome: () => void
  onGoMyPage: () => void
}

export function GoodsOrderCompleteScreen({ payload, onGoHome, onGoMyPage }: GoodsOrderCompleteScreenProps) {
  const store = popupStores.find((s) => s.id === payload.storeId)
  if (!store) return null

  const cartLines = payload.cart.map(({ goodsId, quantity }) => {
    const item = store.goods.find((g) => g.id === goodsId)!
    return { item, quantity, lineTotal: item.price * quantity }
  })
  const total = cartLines.reduce((s, l) => s + l.lineTotal, 0)
  const totalItems = payload.cart.reduce((s, l) => s + l.quantity, 0)

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <div className="flex-1 overflow-y-auto scrollbar-hide">
        <div className="flex flex-col items-center px-6 pt-12 pb-6 space-y-6">

          {/* Success icon */}
          <div className="flex flex-col items-center gap-3">
            <div className="w-20 h-20 rounded-full bg-foreground flex items-center justify-center">
              <CheckCircle size={40} strokeWidth={2} className="text-background" />
            </div>
            <div className="text-center space-y-1">
              <h1 className="text-xl font-black text-foreground">주문 완료!</h1>
              <p className="text-sm text-muted-foreground">굿즈 주문이 정상적으로 접수되었습니다.</p>
            </div>
          </div>

          {/* Order summary card */}
          <div className="w-full bg-secondary rounded-2xl overflow-hidden">
            {/* Store header */}
            <div className="flex items-center gap-3 p-4 border-b border-border">
              <img src={store.image} alt={store.name} className="w-10 h-10 rounded-lg object-cover shrink-0" />
              <p className="text-[13px] font-bold text-foreground">{store.name}</p>
            </div>

            {/* Items */}
            <div className="divide-y divide-border">
              {cartLines.map(({ item, quantity, lineTotal }) => (
                <div key={item.id} className="flex items-center gap-3 px-4 py-3">
                  <img src={item.image} alt={item.name} className="w-12 h-12 rounded-lg object-cover shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="text-[12px] font-semibold text-foreground line-clamp-1">{item.name}</p>
                    <p className="text-[11px] text-muted-foreground mt-0.5">{quantity}개</p>
                  </div>
                  <p className="text-[13px] font-bold text-foreground shrink-0">{lineTotal.toLocaleString()}원</p>
                </div>
              ))}
            </div>

            {/* Total */}
            <div className="flex items-center justify-between px-4 py-3.5 bg-card border-t border-border">
              <span className="text-[13px] font-bold text-foreground">
                총 {totalItems}개 상품
              </span>
              <span className="text-[16px] font-black text-foreground">{total.toLocaleString()}원</span>
            </div>
          </div>

          {/* Delivery notice */}
          <div className="w-full bg-[oklch(0.97_0.01_220)] border border-[oklch(0.88_0.04_220)] rounded-xl p-4">
            <p className="text-[12px] text-[oklch(0.35_0.08_220)] font-medium leading-relaxed">
              주문 접수 후 1–3 영업일 내에 배송이 시작됩니다. 배송 현황은 마이페이지에서 확인하실 수 있습니다.
            </p>
          </div>
        </div>
      </div>

      {/* Fixed bottom buttons */}
      <div className="px-4 py-3 border-t border-border bg-card shrink-0 space-y-2">
        <button
          onClick={onGoMyPage}
          className="w-full py-4 rounded-xl bg-foreground text-background font-bold text-sm flex items-center justify-center gap-2 active:scale-[0.98] transition-all"
        >
          <ShoppingBag size={16} strokeWidth={2.5} />
          주문 내역 확인
        </button>
        <button
          onClick={onGoHome}
          className="w-full py-3.5 rounded-xl bg-secondary text-foreground font-semibold text-sm flex items-center justify-center gap-2 active:scale-[0.98] transition-all"
        >
          <Home size={15} strokeWidth={2} />
          홈으로 돌아가기
        </button>
      </div>
    </div>
  )
}
