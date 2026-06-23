'use client'

import { useState, useEffect } from 'react'
import { ArrowLeft, MapPin, CheckCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import { goodsApi, ORDER_STATUS_LABEL } from '@/lib/goods-api'
import type { GoodsOrderDetail, GoodsOrderStatus } from '@/lib/goods-api'

interface PurchaseDetailScreenProps {
    orderId: string
    onBack: () => void
}

function OrderStatusBadge({ status }: { status: GoodsOrderStatus }) {
    const styles: Record<GoodsOrderStatus, string> = {
        PENDING:  'bg-amber-50 text-amber-600',
        PAID:     'bg-blue-50 text-blue-600',
        REFUNDED: 'bg-secondary text-muted-foreground',
        EXPIRED:  'bg-secondary text-muted-foreground',
    }
    return (
        <span className={cn('text-[11px] font-bold px-2.5 py-1 rounded-full', styles[status])}>
            {ORDER_STATUS_LABEL[status]}
        </span>
    )
}

export function PurchaseDetailScreen({ orderId, onBack }: PurchaseDetailScreenProps) {
    const [order, setOrder] = useState<GoodsOrderDetail | null>(null)
    const [loading, setLoading] = useState(true)

    useEffect(() => {
        let active = true
        goodsApi.getGoodsOrderDetail(parseInt(orderId))
            .then((data) => { if (active) setOrder(data) })
            .catch(() => { if (active) setOrder(null) })
            .finally(() => { if (active) setLoading(false) })
        return () => { active = false }
    }, [orderId])

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
                <h1 className="text-base font-bold text-foreground">주문 상세</h1>
            </div>

            <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">
                {loading ? (
                    <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
                        불러오는 중...
                    </div>
                ) : !order ? (
                    <div className="flex items-center justify-center h-full text-muted-foreground text-sm">
                        주문 정보를 불러올 수 없습니다.
                    </div>
                ) : (
                    <div className="p-4 space-y-5">

                        {/* Order status hero */}
                        <div className="flex items-center justify-between bg-card rounded-xl border border-border p-4">
                            <div>
                                <p className="text-[11px] text-muted-foreground">#{order.goodsOrderId}</p>
                                <p className="text-[13px] font-semibold text-foreground mt-0.5">{order.storeName}</p>
                            </div>
                            <OrderStatusBadge status={order.status} />
                        </div>

                        {/* Product list */}
                        <section className="space-y-2">
                            <h2 className="text-sm font-bold text-foreground">주문 상품</h2>
                            <div className="divide-y divide-border rounded-xl overflow-hidden border border-border">
                                {order.items.map((item) => (
                                    <div key={item.goodsId} className="flex items-center gap-3 p-3 bg-card">
                                        <img
                                            src={item.thumbnailImageUrl ?? '/placeholder.png'}
                                            alt={item.goodsName}
                                            className="w-14 h-14 rounded-lg object-cover shrink-0"
                                        />
                                        <div className="flex-1 min-w-0 space-y-0.5">
                                            <p className="text-[13px] font-semibold text-foreground leading-snug line-clamp-2">
                                                {item.goodsName}
                                            </p>
                                            <p className="text-[12px] text-muted-foreground">
                                                {item.unitPrice?.toLocaleString()}원 × {item.quantity}개
                                            </p>
                                        </div>
                                        <p className="text-[14px] font-black text-foreground shrink-0">
                                            {item.itemAmount.toLocaleString()}원
                                        </p>
                                    </div>
                                ))}
                            </div>
                        </section>

                        {/* Payment summary */}
                        <section className="space-y-2">
                            <h2 className="text-sm font-bold text-foreground">결제 금액</h2>
                            <div className="bg-secondary rounded-xl p-4 space-y-2.5">
                                <div className="flex items-center justify-between">
                                    <span className="text-[13px] text-muted-foreground">상품 금액</span>
                                    <span className="text-[13px] font-semibold text-foreground">
                                        {order.totalAmount.toLocaleString()}원
                                    </span>
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-[13px] text-muted-foreground">쿠폰 할인</span>
                                    <span className={cn(
                                        'text-[13px] font-semibold',
                                        (order.discountAmount ?? 0) > 0 ? 'text-[oklch(0.4_0.1_145)]' : 'text-muted-foreground',
                                    )}>
                                        {(order.discountAmount ?? 0) > 0 ? `-${order.discountAmount!.toLocaleString()}원` : '0원'}
                                    </span>
                                </div>
                                <div className="border-t border-border pt-2.5 flex items-center justify-between">
                                    <span className="text-[14px] font-bold text-foreground">최종 결제 금액</span>
                                    <span className="text-[16px] font-black text-foreground">
                                        {order.finalAmount.toLocaleString()}원
                                    </span>
                                </div>
                            </div>
                        </section>

                        {/* Shipping info */}
                        <section className="space-y-2">
                            <h2 className="text-sm font-bold text-foreground">배송 정보</h2>
                            <div className="bg-card rounded-xl border border-border p-4 space-y-2">
                                <div className="flex items-start gap-2">
                                    <MapPin size={14} strokeWidth={1.8} className="text-muted-foreground mt-0.5 shrink-0" />
                                    <div className="space-y-0.5">
                                        <p className="text-[13px] font-semibold text-foreground">{order.receiverName}</p>
                                        <p className="text-[12px] text-muted-foreground">{order.receiverPhone}</p>
                                        <p className="text-[12px] text-muted-foreground">
                                            {order.address} {order.addressDetail}
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </section>

                        {/* Payment status */}
                        <section className="space-y-2">
                            <h2 className="text-sm font-bold text-foreground">결제 정보</h2>
                            <div className="bg-card rounded-xl border border-border p-4 space-y-2.5">
                                <div className="flex items-center justify-between">
                                    <span className="text-[13px] text-muted-foreground">결제 상태</span>
                                    <div className="flex items-center gap-1.5">
                                        <CheckCircle size={13} className="text-[oklch(0.38_0.1_145)]" />
                                        <span className="text-[13px] font-semibold text-[oklch(0.38_0.1_145)]">
                                            {ORDER_STATUS_LABEL[order.status]}
                                        </span>
                                    </div>
                                </div>
                                <div className="flex items-center justify-between">
                                    <span className="text-[13px] text-muted-foreground">결제 완료 시각</span>
                                    <span className="text-[13px] font-semibold text-foreground">
                                        {order.orderedAt ?? '-'}
                                    </span>
                                </div>
                            </div>
                            {/* TODO: 환불 백엔드 완성 후 활성화 */}
                            <button
                                disabled
                                className="w-full py-3 rounded-xl bg-secondary text-muted-foreground font-semibold text-sm cursor-not-allowed mt-2"
                            >
                                환불 신청
                            </button>
                        </section>

                    </div>
                )}
            </div>
        </div>
    )
}
