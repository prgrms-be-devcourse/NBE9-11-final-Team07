'use client'

import { useState, useEffect } from 'react'
import { ArrowLeft, ShoppingBag } from 'lucide-react'
import { cn } from '@/lib/utils'
import { goodsApi, ORDER_STATUS_LABEL } from '@/lib/goods-api'
import type { GoodsOrderSummary, GoodsOrderStatus } from '@/lib/goods-api'

interface PurchasesScreenProps {
    onBack: () => void
    onViewDetail: (orderId: string) => void
}

function OrderStatusBadge({ status }: { status: GoodsOrderStatus }) {
    const styles: Record<GoodsOrderStatus, string> = {
        PENDING:  'bg-amber-50 text-amber-600',
        PAID:     'bg-blue-50 text-blue-600',
        REFUNDED: 'bg-secondary text-muted-foreground',
        EXPIRED:  'bg-secondary text-muted-foreground',
    }
    return (
        <span className={cn('text-[10px] font-bold px-2 py-0.5 rounded-full', styles[status])}>
            {ORDER_STATUS_LABEL[status]}
        </span>
    )
}

export function PurchasesScreen({ onBack, onViewDetail }: PurchasesScreenProps) {
    const [orders, setOrders] = useState<GoodsOrderSummary[]>([])
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        let active = true
        goodsApi.getMyGoodsOrders()
            .then((res) => { if (active) setOrders(res.content) })
            .catch(() => { if (active) setOrders([]) })
            .finally(() => { if (active) setLoading(false) })
        return () => { active = false }
    }, [])

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
                {loading ? (
                    <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
                        불러오는 중...
                    </div>
                ) : orders.length === 0 ? (
                    <div className="flex flex-col items-center justify-center h-full gap-3 text-muted-foreground py-24">
                        <ShoppingBag size={40} strokeWidth={1.4} />
                        <p className="text-sm font-medium">구매 내역이 없습니다</p>
                    </div>
                ) : (
                    <div className="space-y-2 px-4 pt-4">
                        {orders.map((order) => {
                            const rep = order.items[0]
                            const extra = order.items.length - 1
                            const dateStr = order.paidAt ?? order.createdAt
                            return (
                                <button
                                    key={order.goodsOrderId}
                                    onClick={() => onViewDetail(String(order.goodsOrderId))}
                                    className="w-full text-left bg-card rounded-xl border border-border p-3.5 active:opacity-70 transition-opacity"
                                >
                                    <div className="flex items-start gap-3">
                                        <img
                                            src={rep?.thumbnailImageUrl ?? '/placeholder.png'}
                                            alt={rep?.name ?? ''}
                                            className="w-14 h-14 rounded-lg object-cover shrink-0"
                                        />
                                        <div className="flex-1 min-w-0 space-y-0.5">
                                            <p className="text-[10px] text-muted-foreground">
                                                #{order.goodsOrderId}
                                            </p>
                                            <p className="text-[13px] font-semibold text-foreground leading-snug line-clamp-1">
                                                {rep?.name ?? ''}
                                                {extra > 0 && (
                                                    <span className="text-muted-foreground font-normal"> 외 {extra}개</span>
                                                )}
                                            </p>
                                            <p className="text-[11px] text-muted-foreground">{dateStr}</p>
                                        </div>
                                        <div className="text-right shrink-0 space-y-1.5">
                                            <p className="text-[13px] font-black text-foreground">
                                                {order.finalAmount.toLocaleString()}원
                                            </p>
                                            <OrderStatusBadge status={order.status} />
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
