'use client'

import { ArrowLeft, ShoppingBag } from 'lucide-react'
import { cn } from '@/lib/utils'
import { purchasedGoods } from '@/lib/data'
import type { OrderStatus } from '@/lib/data'

interface PurchasesScreenProps {
  onBack: () => void
  onViewDetail: (orderId: string) => void
}

function OrderStatusBadge({ status }: { status: OrderStatus }) {
  const styles: Record<OrderStatus, string> = {
    '결제 완료':   'bg-blue-50 text-blue-600',
    '배송 준비중': 'bg-amber-50 text-amber-600',
    '배송중':      'bg-[oklch(0.95_0.04_145)] text-[oklch(0.38_0.1_145)]',
    '배송 완료':   'bg-secondary text-muted-foreground',
    '주문 취소':   'bg-secondary text-muted-foreground',
  }
  return (
    <span className={cn('text-[10px] font-bold px-2 py-0.5 rounded-full', styles[status])}>
      {status}
    </span>
  )
}

export function PurchasesScreen({ onBack, onViewDetail }: PurchasesScreenProps) {
  return (
    <div className="flex flex-col h-full overflow-hidden">
      <div className="flex items-center gap-3 px-4 py-3 border-b border-border bg-card shrink-0">
        <button
          onClick={onBack}
          className="flex items-center justify-center w-9 h-9 rounded-full hover:bg-secondary transition-colors"
          aria-label="뒤로 가기"
        >
          <ArrowLeft size={20} />
        </button>
        <h1 className="text-base font-bold text-foreground">구매 내역</h1>
      </div>

      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">
        {purchasedGoods.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 text-muted-foreground py-24">
            <ShoppingBag size={40} strokeWidth={1.4} />
            <p className="text-sm font-medium">구매 내역이 없습니다</p>
          </div>
        ) : (
          <div className="space-y-2 px-4 pt-4">
            {purchasedGoods.map((order) => {
              const rep = order.items[0]
              const extra = order.items.length - 1
              return (
                <button
                  key={order.id}
                  onClick={() => onViewDetail(order.id)}
                  className="w-full text-left bg-card rounded-xl border border-border p-3.5 active:opacity-70 transition-opacity"
                >
                  <div className="flex items-start gap-3">
                    <img
                      src={rep.image}
                      alt={rep.name}
                      className="w-14 h-14 rounded-lg object-cover shrink-0"
                    />
                    <div className="flex-1 min-w-0 space-y-0.5">
                      <p className="text-[10px] text-muted-foreground">{order.orderNumber}</p>
                      <p className="text-[13px] font-semibold text-foreground leading-snug line-clamp-1">
                        {rep.name}
                        {extra > 0 && (
                          <span className="text-muted-foreground font-normal"> 외 {extra}개</span>
                        )}
                      </p>
                      <p className="text-[11px] text-muted-foreground">{order.paidAt}</p>
                    </div>
                    <div className="text-right shrink-0 space-y-1.5">
                      <p className="text-[13px] font-black text-foreground">{order.finalAmount.toLocaleString()}원</p>
                      <OrderStatusBadge status={order.orderStatus} />
                    </div>
                  </div>
                </button>
              )
            })}
          </div>
        )}
      </div>
    </div>
  )
}
