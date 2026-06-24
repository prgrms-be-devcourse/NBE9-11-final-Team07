'use client'

import { useCallback, useEffect, useState } from 'react'
import { ChevronRight, CalendarCheck, ShoppingBag, Store } from 'lucide-react'
import { cn } from '@/lib/utils'
import { StatusBadge } from '@/components/ui/status-badge'
import { reservationApi } from '@/lib/reservation-api'
import { goodsApi, ORDER_STATUS_LABEL } from '@/lib/goods-api'
import type { MyReservationResponse } from '@/lib/reservation-api'
import type { GoodsOrderSummary } from '@/lib/goods-api'
import type { ReservationHistoryStatus } from '@/lib/data'

interface MyPageScreenProps {
  onViewAllReservations: () => void
  onViewAllPurchases: () => void
  onViewPurchaseDetail: (orderId: string) => void
  onGoPopupStoreManagement: () => void
}

// Treat the logged-in user as an organizer for demo purposes
const IS_ORGANIZER = true

function toHistoryStatus(status: MyReservationResponse['status']): ReservationHistoryStatus {
  return status === 'CONFIRMED' ? '예약 완료' : '예약 취소'
}

function formatDate(date: string) {
  return date.replaceAll('-', '.')
}

function formatTime(time: string) {
  return time.slice(0, 5)
}

function formatOrderedAt(dateTime: string) {
  return dateTime.replace('T', ' ').slice(0, 16)
}

function SectionHeader({
  title,
  onViewAll,
}: {
  title: string
  onViewAll: () => void
}) {
  return (
    <div className="flex items-center justify-between px-4 pt-5 pb-2">
      <h2 className="text-[13px] font-bold text-foreground">{title}</h2>
      <button
        onClick={onViewAll}
        className="text-[11px] text-muted-foreground flex items-center gap-0.5 active:opacity-60"
      >
        전체 보기 <ChevronRight size={12} />
      </button>
    </div>
  )
}

export function MyPageScreen({
  onViewAllReservations,
  onViewAllPurchases,
  onViewPurchaseDetail,
  onGoPopupStoreManagement,
}: MyPageScreenProps) {
  const [reservations, setReservations] = useState<MyReservationResponse[]>([])
  const [reservationsError, setReservationsError] = useState<string | null>(null)
  const [purchases, setPurchases] = useState<GoodsOrderSummary[]>([])
  const [purchaseTotal, setPurchaseTotal] = useState(0)
  const [purchasesError, setPurchasesError] = useState<string | null>(null)

  const loadReservations = useCallback(async () => {
    try {
      const response = await reservationApi.getMyReservations()
      setReservations(response.content ?? [])
      setReservationsError(null)
    } catch (e) {
      setReservations([])
      setReservationsError(e instanceof Error ? e.message : '예약 내역을 불러오지 못했습니다.')
    }
  }, [])

  const loadPurchases = useCallback(async () => {
    try {
      const response = await goodsApi.getMyGoodsOrders()
      setPurchases(response.content ?? [])
      setPurchaseTotal(response.totalElements ?? response.content?.length ?? 0)
      setPurchasesError(null)
    } catch (e) {
      setPurchases([])
      setPurchaseTotal(0)
      setPurchasesError(e instanceof Error ? e.message : '구매 내역을 불러오지 못했습니다.')
    }
  }, [])

  useEffect(() => {
    void loadReservations()
    void loadPurchases()
  }, [loadPurchases, loadReservations])

  const recentReservations = reservations.slice(0, 3)
  const recentPurchases = purchases.slice(0, 2)

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">

        <div className="bg-card px-4 pt-5 pb-5 border-b border-border">
          <div>
            <h1 className="text-base font-black text-foreground">마이페이지</h1>
            <p className="text-[12px] text-muted-foreground mt-1">내 예약과 구매 내역을 확인하세요</p>
          </div>

          <div className="grid grid-cols-2 gap-0 mt-4 border border-border rounded-xl overflow-hidden">
            {[
              { label: '예약', value: reservations.length, Icon: CalendarCheck },
              { label: '구매', value: purchaseTotal, Icon: ShoppingBag },
            ].map(({ label, value, Icon }, i) => (
              <div
                key={label}
                className={cn('flex flex-col items-center py-3.5 gap-1', i === 0 && 'border-r border-border')}
              >
                <Icon size={16} strokeWidth={1.8} className="text-muted-foreground" />
                <span className="text-lg font-black text-foreground">{value}</span>
                <span className="text-[10px] text-muted-foreground font-medium">{label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Recent Reservations */}
        <SectionHeader title="최근 예약" onViewAll={onViewAllReservations} />
        <div className="space-y-2 px-4">
          {reservationsError && (
            <div className="flex items-center justify-center py-6 bg-secondary rounded-xl">
              <p className="text-[13px] text-muted-foreground">{reservationsError}</p>
            </div>
          )}
          {!reservationsError && recentReservations.length === 0 && (
            <div className="flex items-center justify-center py-6 bg-secondary rounded-xl">
              <p className="text-[13px] text-muted-foreground">예약 내역이 없습니다.</p>
            </div>
          )}
          {recentReservations.map((r) => (
            <div key={r.reservationId} className="flex items-center gap-3 bg-card rounded-xl border border-border p-3">
              <div className="w-12 h-12 rounded-lg bg-secondary flex items-center justify-center shrink-0">
                <CalendarCheck size={20} className="text-muted-foreground" />
              </div>
              <div className="flex-1 min-w-0">
                <p className="text-[12px] font-semibold text-foreground line-clamp-1">{r.popupName}</p>
                <p className="text-[11px] text-muted-foreground mt-0.5">
                  {formatDate(r.reservationDate)} · {formatTime(r.reservationTime)}
                </p>
              </div>
              <div className="text-right shrink-0 space-y-1">
                <StatusBadge status={toHistoryStatus(r.status)} size="sm" />
                {r.price > 0 && (
                  <p className="text-[11px] font-bold text-foreground">{r.price.toLocaleString()}원</p>
                )}
                {r.price === 0 && (
                  <p className="text-[11px] text-muted-foreground">무료</p>
                )}
              </div>
            </div>
          ))}
        </div>

        {/* Recent Purchases */}
        <SectionHeader title="최근 구매" onViewAll={onViewAllPurchases} />
        <div className="space-y-2 px-4">
          {purchasesError && (
            <div className="flex items-center justify-center py-6 bg-secondary rounded-xl">
              <p className="text-[13px] text-muted-foreground">{purchasesError}</p>
            </div>
          )}
          {!purchasesError && recentPurchases.length === 0 && (
            <div className="flex items-center justify-center py-6 bg-secondary rounded-xl">
              <p className="text-[13px] text-muted-foreground">구매 내역이 없습니다.</p>
            </div>
          )}
          {recentPurchases.map((order) => {
            const rep = order.items[0]
            const extra = order.items.length - 1
            return (
              <button
                key={order.goodsOrderId}
                onClick={() => onViewPurchaseDetail(String(order.goodsOrderId))}
                className="w-full flex items-center gap-3 bg-card rounded-xl border border-border p-3 text-left active:opacity-70 transition-opacity"
              >
                <img
                  src="/placeholder.png"
                  alt={rep?.goodsName ?? '구매 상품'}
                  className="w-12 h-12 rounded-lg object-cover shrink-0"
                />
                <div className="flex-1 min-w-0">
                  <p className="text-[11px] text-muted-foreground">#{order.goodsOrderId}</p>
                  <p className="text-[12px] font-semibold text-foreground line-clamp-1 mt-0.5">
                    {rep?.goodsName ?? '굿즈 주문'}{extra > 0 && <span className="text-muted-foreground font-normal"> 외 {extra}개</span>}
                  </p>
                  <p className="text-[11px] text-muted-foreground mt-0.5">{formatOrderedAt(order.orderedAt)}</p>
                </div>
                <div className="text-right shrink-0 space-y-1">
                  <p className="text-[12px] font-black text-foreground">{order.finalAmount.toLocaleString()}원</p>
                  <span className="inline-flex text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-secondary text-muted-foreground">
                    {ORDER_STATUS_LABEL[order.status]}
                  </span>
                </div>
              </button>
            )
          })}
        </div>

        {/* Organizer section */}
        {IS_ORGANIZER && (
          <div className="px-4 pt-5 pb-1">
            <div className="border border-border rounded-2xl overflow-hidden">
              {/* Section header */}
              <div className="flex items-center gap-2 px-4 py-3 bg-foreground">
                <Store size={14} strokeWidth={2} className="text-background" />
                <p className="text-[12px] font-bold text-background tracking-wide">주최자 관리</p>
              </div>

              {/* Menu items */}
              {[
                {
                  Icon: Store,
                  label: '팝업스토어 관리',
                  description: '내 팝업스토어 목록 및 예약 관리',
                  onClick: onGoPopupStoreManagement,
                },
              ].map(({ Icon, label, description, onClick }, i, arr) => (
                <button
                  key={label}
                  onClick={onClick}
                  className={cn(
                    'w-full flex items-center gap-3.5 px-4 py-3.5 text-left active:bg-secondary transition-colors',
                    i < arr.length - 1 && 'border-b border-border',
                  )}
                >
                  <div className="w-9 h-9 rounded-xl bg-secondary flex items-center justify-center shrink-0">
                    <Icon size={16} strokeWidth={1.8} className="text-foreground" />
                  </div>
                  <div className="flex-1 min-w-0">
                    <p className="text-[13px] font-semibold text-foreground">{label}</p>
                    <p className="text-[11px] text-muted-foreground mt-0.5">{description}</p>
                  </div>
                  <ChevronRight size={16} className="text-muted-foreground shrink-0" />
                </button>
              ))}
            </div>
          </div>
        )}

      </div>
    </div>
  )
}
