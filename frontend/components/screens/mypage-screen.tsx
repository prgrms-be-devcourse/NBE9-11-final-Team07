'use client'

import { ChevronRight, CalendarCheck, ShoppingBag, Ticket, Settings, Store } from 'lucide-react'
import { cn } from '@/lib/utils'
import { StatusBadge } from '@/components/ui/status-badge'
import { userProfile, reservationHistory, purchasedGoods, claimedCoupons } from '@/lib/data'

interface MyPageScreenProps {
  onViewAllReservations: () => void
  onViewAllPurchases: () => void
  onViewPurchaseDetail: (orderId: string) => void
  onViewAllCoupons: () => void
  onGoPopupStoreManagement: () => void
}

// Treat the logged-in user as an organizer for demo purposes
const IS_ORGANIZER = true

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
  onViewAllCoupons,
  onGoPopupStoreManagement,
}: MyPageScreenProps) {
  const recentReservations = reservationHistory.slice(0, 3)
  const recentPurchases = purchasedGoods.slice(0, 2)
  const recentCoupons = claimedCoupons.filter((c) => !c.isUsed).slice(0, 3)

  const activeReservation = reservationHistory.find((r) => r.status === '예약 완료')

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">

        {/* Profile Header */}
        <div className="bg-card px-4 pt-6 pb-5 border-b border-border">
          <div className="flex items-center gap-4">
            <div className="w-14 h-14 rounded-full bg-foreground flex items-center justify-center text-background text-xl font-black shrink-0">
              {userProfile.avatarInitials}
            </div>
            <div className="flex-1">
              <p className="text-base font-bold text-foreground">{userProfile.name}</p>
              <p className="text-xs text-muted-foreground mt-0.5">{userProfile.handle}</p>
            </div>
            <button className="flex items-center justify-center w-9 h-9 rounded-full bg-secondary">
              <Settings size={16} strokeWidth={1.8} className="text-muted-foreground" />
            </button>
          </div>

          {/* Stats */}
          <div className="grid grid-cols-3 gap-0 mt-5 border border-border rounded-xl overflow-hidden">
            {[
              { label: '예약', value: userProfile.reservations, Icon: CalendarCheck },
              { label: '구매', value: userProfile.purchases, Icon: ShoppingBag },
              { label: '쿠폰', value: userProfile.coupons, Icon: Ticket },
            ].map(({ label, value, Icon }, i) => (
              <div
                key={label}
                className={cn('flex flex-col items-center py-3.5 gap-0.5', i < 2 && 'border-r border-border')}
              >
                <span className="text-lg font-black text-foreground">{value}</span>
                <span className="text-[10px] text-muted-foreground font-medium">{label}</span>
              </div>
            ))}
          </div>
        </div>

        {/* Upcoming Reservation (dark card) */}
        {activeReservation && (
          <div className="px-4 pt-5">
            <h2 className="text-[13px] font-bold text-foreground mb-2.5">다가오는 예약</h2>
            <div className="bg-foreground rounded-2xl p-4 text-background">
              <div className="flex items-start gap-3">
                <img
                  src={activeReservation.storeImage}
                  alt={activeReservation.storeName}
                  className="w-14 h-14 rounded-xl object-cover shrink-0"
                />
                <div className="flex-1 min-w-0">
                  <p className="text-[13px] font-bold leading-snug line-clamp-2 text-background">
                    {activeReservation.storeName}
                  </p>
                  <p className="text-[11px] text-background/70 mt-1">
                    {activeReservation.date} · {activeReservation.timeSlot}
                  </p>
                  <p className="text-[11px] text-background/60 mt-0.5">
                    {activeReservation.reservationNumber}
                  </p>
                  <div className="flex items-center gap-2 mt-1.5">
                    <span className="text-[11px] bg-white/20 text-background font-semibold px-2 py-0.5 rounded-full">
                      {activeReservation.timeSlot} 입장
                    </span>
                    {activeReservation.price > 0 && (
                      <span className="text-[11px] bg-white/20 text-background font-semibold px-2 py-0.5 rounded-full">
                        {activeReservation.price.toLocaleString()}원
                      </span>
                    )}
                  </div>
                </div>
              </div>
              <div className="mt-3 pt-3 border-t border-white/20 flex gap-2">
                <button className="flex-1 py-2 bg-white/10 rounded-lg text-[12px] font-semibold text-background">
                  예약 취소
                </button>
                <button className="flex-1 py-2 bg-white rounded-lg text-[12px] font-semibold text-foreground">
                  QR 코드 보기
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Recent Reservations */}
        <SectionHeader title="최근 예약" onViewAll={onViewAllReservations} />
        <div className="space-y-2 px-4">
          {recentReservations.map((r) => (
            <div key={r.id} className="flex items-center gap-3 bg-card rounded-xl border border-border p-3">
              <img
                src={r.storeImage}
                alt={r.storeName}
                className="w-12 h-12 rounded-lg object-cover shrink-0"
              />
              <div className="flex-1 min-w-0">
                <p className="text-[12px] font-semibold text-foreground line-clamp-1">{r.storeName}</p>
                <p className="text-[11px] text-muted-foreground mt-0.5">
                  {r.date} · {r.timeSlot}
                </p>
              </div>
              <div className="text-right shrink-0 space-y-1">
                <StatusBadge status={r.status} size="sm" />
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
          {recentPurchases.map((order) => {
            const rep = order.items[0]
            const extra = order.items.length - 1
            return (
              <button
                key={order.id}
                onClick={() => onViewPurchaseDetail(order.id)}
                className="w-full flex items-center gap-3 bg-card rounded-xl border border-border p-3 text-left active:opacity-70 transition-opacity"
              >
                <img
                  src={rep.image}
                  alt={rep.name}
                  className="w-12 h-12 rounded-lg object-cover shrink-0"
                />
                <div className="flex-1 min-w-0">
                  <p className="text-[11px] text-muted-foreground">{order.orderNumber}</p>
                  <p className="text-[12px] font-semibold text-foreground line-clamp-1 mt-0.5">
                    {rep.name}{extra > 0 && <span className="text-muted-foreground font-normal"> 외 {extra}개</span>}
                  </p>
                  <p className="text-[11px] text-muted-foreground mt-0.5">{order.paidAt}</p>
                </div>
                <div className="text-right shrink-0 space-y-1">
                  <p className="text-[12px] font-black text-foreground">{order.finalAmount.toLocaleString()}원</p>
                  <span className="inline-flex text-[10px] font-semibold px-1.5 py-0.5 rounded-full bg-secondary text-muted-foreground">
                    {order.orderStatus}
                  </span>
                </div>
              </button>
            )
          })}
        </div>

        {/* Recent Coupons */}
        <SectionHeader title="보유 쿠폰" onViewAll={onViewAllCoupons} />
        {recentCoupons.length === 0 ? (
          <div className="px-4">
            <div className="flex items-center justify-center py-6 bg-secondary rounded-xl">
              <p className="text-[13px] text-muted-foreground">보유한 쿠폰이 없습니다.</p>
            </div>
          </div>
        ) : (
          <div className="space-y-2 px-4">
            {recentCoupons.map((coupon) => (
              <div
                key={coupon.id}
                className="flex items-center gap-3 bg-card rounded-xl border-2 border-foreground/15 p-3.5"
              >
                <div className="w-9 h-9 rounded-lg flex items-center justify-center shrink-0 bg-[oklch(0.94_0.04_145)]">
                  <Ticket size={16} className="text-[oklch(0.4_0.1_145)]" />
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-[12px] font-semibold text-foreground line-clamp-1">{coupon.title}</p>
                  <p className="text-[11px] text-muted-foreground mt-0.5">{coupon.discount}</p>
                </div>
                <div className="text-right shrink-0">
                  <p className="text-[13px] font-black text-[oklch(0.62_0.24_25)]">{coupon.discount}</p>
                  <p className="text-[10px] text-muted-foreground mt-0.5">~{coupon.expiresAt}</p>
                </div>
              </div>
            ))}
          </div>
        )}

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
