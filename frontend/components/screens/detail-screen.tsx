'use client'

import { useEffect, useState } from 'react'
import {
  ArrowLeft,
  MapPin,
  Calendar,
  Ticket,
  ShoppingBag,
  TicketIcon,
  CheckCircle,
  Share2,
  Heart,
  Minus,
  Plus,
  X,
} from 'lucide-react'
import { cn } from '@/lib/utils'
import { StatusBadge } from '@/components/ui/status-badge'
import { popupStores, getOperatingDates, formatDateKorean } from '@/lib/data'
import type { TimeSlot, GoodsItem, CouponItem, ReservationPayload, GoodsOrderPayload, CouponIssuancePayload } from '@/lib/data'
import { couponApi, formatDiscount } from '@/lib/coupon-api'
import { reservationApi } from '@/lib/reservation-api'
import type { PopupStoreDetailResponse, ReservationSlotResponse } from '@/lib/reservation-api'

interface DetailScreenProps {
  storeId: string
  onBack: () => void
  onReserve: (payload: ReservationPayload) => void
  onOrderGoods: (payload: GoodsOrderPayload) => void
  onIssueCoupon: (payload: CouponIssuancePayload) => void
}

function SectionTitle({ children }: { children: React.ReactNode }) {
  return <h2 className="text-base font-bold text-foreground">{children}</h2>
}

function TimeSlotButton({
  slot,
  selected,
  onClick,
}: {
  slot: TimeSlot
  selected: boolean
  onClick: () => void
}) {
  const isClosed = slot.status === '마감'
  const isUrgent = slot.status === '마감 임박'

  return (
    <button
      onClick={isClosed ? undefined : onClick}
      disabled={isClosed}
      className={cn(
        'flex flex-col items-center justify-center rounded-xl px-3 py-3 border-2 transition-all text-center',
        isClosed && 'opacity-50 cursor-not-allowed border-border bg-secondary',
        !isClosed && !selected && 'border-border bg-card hover:border-foreground/40',
        !isClosed && selected && 'border-foreground bg-foreground text-background',
      )}
    >
      <span
        className={cn(
          'text-[13px] font-bold',
          selected && !isClosed ? 'text-background' : 'text-foreground',
          isClosed && 'text-muted-foreground',
        )}
      >
        {slot.time}
      </span>
      {isUrgent && !selected && (
        <span className="text-[9px] text-[oklch(0.62_0.24_25)] font-semibold mt-0.5">마감 임박</span>
      )}
      {selected && !isClosed && <CheckCircle size={12} className="text-background mt-0.5" />}
      {isClosed && <span className="text-[9px] text-muted-foreground mt-0.5">마감</span>}
    </button>
  )
}

function GoodsCard({
  item,
  quantity,
  onAdd,
  onRemove,
  onOpenDetail,
}: {
  item: GoodsItem
  quantity: number
  onAdd: () => void
  onRemove: () => void
  onOpenDetail: () => void
}) {
  const isSoldOut = item.status === '품절'
  const isSelected = quantity > 0

  return (
    <div
      className={cn(
        'bg-card rounded-xl overflow-hidden border-2 transition-all',
        isSoldOut && 'opacity-60',
        isSelected && !isSoldOut ? 'border-foreground' : 'border-border',
      )}
    >
      {/* Tappable image area opens detail sheet */}
      <button
        onClick={onOpenDetail}
        className="w-full text-left"
        aria-label={`${item.name} 상세 보기`}
      >
        <div className="relative aspect-square overflow-hidden bg-secondary">
          <img
            src={item.image}
            alt={item.name}
            className={cn('w-full h-full object-cover', isSoldOut && 'grayscale')}
          />
          {isSoldOut && (
            <div className="absolute inset-0 flex items-center justify-center bg-black/40">
              <span className="text-white text-xs font-bold tracking-wider">SOLD OUT</span>
            </div>
          )}
          {isSelected && !isSoldOut && (
            <div className="absolute top-2 right-2 w-5 h-5 bg-foreground rounded-full flex items-center justify-center">
              <CheckCircle size={12} className="text-background" />
            </div>
          )}
        </div>
        <div className="px-2.5 pt-2.5 pb-0 space-y-1">
          <p className="text-[12px] font-semibold text-foreground leading-snug line-clamp-2">{item.name}</p>
          <div className="flex items-center justify-between gap-1">
            <span className={cn('text-[13px] font-bold', isSoldOut ? 'text-muted-foreground' : 'text-foreground')}>
              {item.price.toLocaleString()}원
            </span>
            <StatusBadge status={item.status} size="sm" />
          </div>
        </div>
      </button>

      {/* Quantity selector stays on card, separate from tappable area */}
      <div className="p-2.5 pt-2">
        {isSoldOut ? (
          <div className="w-full py-1.5 rounded-lg bg-secondary flex items-center justify-center">
            <span className="text-[11px] font-semibold text-muted-foreground">품절</span>
          </div>
        ) : (
          <div className="flex items-center justify-between rounded-lg border border-border overflow-hidden">
            <button
              onClick={onRemove}
              disabled={quantity === 0}
              className={cn(
                'flex items-center justify-center w-8 h-8 transition-colors',
                quantity === 0
                  ? 'text-muted-foreground/40 cursor-not-allowed'
                  : 'text-foreground hover:bg-secondary active:bg-secondary',
              )}
              aria-label="수량 감소"
            >
              <Minus size={13} strokeWidth={2.5} />
            </button>
            <span className="text-[13px] font-bold text-foreground tabular-nums w-6 text-center">
              {quantity}
            </span>
            <button
              onClick={onAdd}
              className="flex items-center justify-center w-8 h-8 text-foreground hover:bg-secondary active:bg-secondary transition-colors"
              aria-label="수량 증가"
            >
              <Plus size={13} strokeWidth={2.5} />
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

function GoodsDetailSheet({
  item,
  quantity,
  onClose,
  onAdd,
  onRemove,
}: {
  item: GoodsItem
  quantity: number
  onClose: () => void
  onAdd: () => void
  onRemove: () => void
}) {
  const isSoldOut = item.status === '품절'
  // Local quantity for the sheet — starts from current cart quantity
  const [localQty, setLocalQty] = useState<number>(quantity > 0 ? quantity : 1)

  function handleConfirm() {
    // Bring cart quantity to match localQty
    const diff = localQty - quantity
    if (diff > 0) {
      for (let i = 0; i < diff; i++) onAdd()
    } else if (diff < 0) {
      for (let i = 0; i < -diff; i++) onRemove()
    }
    onClose()
  }

  return (
    <>
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/50 z-40"
        onClick={onClose}
        aria-hidden="true"
      />

      {/* Sheet — 85% height */}
      <div
        className="absolute bottom-0 left-0 right-0 z-50 flex flex-col bg-background rounded-t-2xl overflow-hidden"
        style={{ height: '85%' }}
        role="dialog"
        aria-modal="true"
        aria-label={item.name}
      >
        {/* Fixed header */}
        <div className="flex items-center justify-between px-4 py-3 border-b border-border shrink-0">
          <p className="text-[14px] font-bold text-foreground line-clamp-1 flex-1 mr-3">{item.name}</p>
          <button
            onClick={onClose}
            aria-label="닫기"
            className="flex items-center justify-center w-8 h-8 rounded-full bg-secondary text-foreground shrink-0"
          >
            <X size={16} strokeWidth={2.5} />
          </button>
        </div>

        {/* Scrollable content */}
        <div className="flex-1 overflow-y-auto scrollbar-hide">
          {/* Main image */}
          <div className="w-full aspect-square overflow-hidden bg-secondary">
            <img
              src={item.image}
              alt={item.name}
              className={cn('w-full h-full object-cover', isSoldOut && 'grayscale')}
            />
          </div>

          <div className="p-4 space-y-4">
            {/* Name + price */}
            <div>
              <p className="text-[17px] font-black text-foreground leading-snug">{item.name}</p>
              <p className={cn('text-[20px] font-black mt-1', isSoldOut ? 'text-muted-foreground' : 'text-foreground')}>
                {item.price.toLocaleString()}
                <span className="text-[14px] font-bold ml-0.5">원</span>
              </p>
            </div>

            {/* Status + stock */}
            <div className="flex items-center gap-2">
              <StatusBadge status={item.status} size="md" />
              {item.stock !== undefined && (
                <span className="text-[12px] text-muted-foreground">
                  재고 {item.stock.toLocaleString()}개
                </span>
              )}
            </div>

            {/* Description */}
            {item.description && (
              <p className="text-[13px] text-muted-foreground leading-relaxed">{item.description}</p>
            )}

            {/* Detail images */}
            {item.detailImages && item.detailImages.length > 0 && (
              <div className="space-y-2">
                {item.detailImages.map((src, i) => (
                  <img
                    key={i}
                    src={src}
                    alt={`${item.name} 상세 이미지 ${i + 1}`}
                    className="w-full object-cover rounded-lg"
                  />
                ))}
              </div>
            )}

            {/* Bottom spacer so content clears the fixed action area */}
            <div className="h-2" />
          </div>
        </div>

        {/* Fixed bottom action area */}
        <div className="shrink-0 border-t border-border bg-background px-4 py-3 space-y-2.5">
          {isSoldOut ? (
            <div className="w-full py-3.5 rounded-xl bg-secondary flex items-center justify-center">
              <span className="text-[14px] font-bold text-muted-foreground">품절된 상품입니다</span>
            </div>
          ) : (
            <>
              {/* Quantity selector */}
              <div className="flex items-center justify-between">
                <span className="text-[13px] font-semibold text-foreground">수량</span>
                <div className="flex items-center gap-0 rounded-xl border border-border overflow-hidden">
                  <button
                    onClick={() => setLocalQty((q) => Math.max(0, q - 1))}
                    disabled={localQty === 0}
                    className={cn(
                      'flex items-center justify-center w-10 h-10 transition-colors',
                      localQty === 0
                        ? 'text-muted-foreground/40 cursor-not-allowed'
                        : 'text-foreground active:bg-secondary',
                    )}
                    aria-label="수량 감소"
                  >
                    <Minus size={15} strokeWidth={2.5} />
                  </button>
                  <span className="text-[15px] font-black text-foreground tabular-nums w-10 text-center">
                    {localQty}
                  </span>
                  <button
                    onClick={() => setLocalQty((q) => q + 1)}
                    className="flex items-center justify-center w-10 h-10 text-foreground active:bg-secondary transition-colors"
                    aria-label="수량 증가"
                  >
                    <Plus size={15} strokeWidth={2.5} />
                  </button>
                </div>
              </div>

              {/* CTA */}
              <button
                onClick={handleConfirm}
                disabled={localQty === 0}
                className={cn(
                  'w-full py-3.5 rounded-xl font-bold text-sm transition-all',
                  localQty > 0
                    ? 'bg-foreground text-background active:scale-[0.98]'
                    : 'bg-secondary text-muted-foreground cursor-not-allowed',
                )}
              >
                {quantity > 0
                  ? `선택 변경 · ${(item.price * localQty).toLocaleString()}원`
                  : `담기 · ${(item.price * localQty).toLocaleString()}원`}
              </button>
            </>
          )}
        </div>
      </div>
    </>
  )
}

function CouponCard({ coupon, onIssue }: { coupon: CouponItem; onIssue: () => void }) {
  const isAvailable = coupon.status === '발급 가능'
  const isDepleted = coupon.status === '소진'

  return (
    <div
      className={cn(
        'bg-card rounded-xl border-2 p-4 flex items-center gap-4 transition-all',
        isDepleted ? 'border-border opacity-60' : 'border-foreground/20',
      )}
    >
      <div
        className={cn(
          'flex items-center justify-center w-10 h-10 rounded-full shrink-0',
          isDepleted ? 'bg-secondary' : 'bg-[oklch(0.94_0.04_145)]',
        )}
      >
        <TicketIcon
          size={18}
          className={isDepleted ? 'text-muted-foreground' : 'text-[oklch(0.4_0.1_145)]'}
        />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-[13px] font-semibold text-foreground leading-snug">{coupon.title}</p>
        <p className="text-[12px] font-bold text-[oklch(0.62_0.24_25)] mt-0.5">{coupon.discount}</p>
        <div className="mt-1">
          <StatusBadge status={coupon.status} size="sm" />
        </div>
      </div>
      <button
        disabled={isDepleted}
        onClick={isDepleted ? undefined : onIssue}
        className={cn(
          'shrink-0 px-3 py-2 rounded-lg text-[11px] font-bold transition-colors',
          isDepleted
            ? 'bg-secondary text-muted-foreground cursor-not-allowed'
            : 'bg-foreground text-background hover:opacity-90',
        )}
      >
        {isDepleted ? '소진' : '받기'}
      </button>
    </div>
  )
}

export function DetailScreen({ storeId, onBack, onReserve, onOrderGoods, onIssueCoupon }: DetailScreenProps) {
  const store = popupStores.find((s) => s.id === storeId)
  const [selectedDate, setSelectedDate] = useState<string | null>(null)
  const [selectedSlotId, setSelectedSlotId] = useState<number | null>(null)
  const [liked, setLiked] = useState(false)
  const [cart, setCart] = useState<Record<string, number>>({})
  // id of the goods item whose detail sheet is open, null = closed
  const [sheetGoodsId, setSheetGoodsId] = useState<string | null>(null)
  const [coupons, setCoupons] = useState<CouponItem[]>([])
  const [couponError, setCouponError] = useState<string | null>(null)
  const [popupDetail, setPopupDetail] = useState<PopupStoreDetailResponse | null>(null)
  const [slots, setSlots] = useState<ReservationSlotResponse[]>([])
  const [slotsLoading, setSlotsLoading] = useState(false)
  const [slotsError, setSlotsError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    reservationApi.getPopupDetail(storeId)
      .then((response) => {
        if (!cancelled) setPopupDetail(response)
      })
      .catch(() => {
        // Non-reservation UI can continue to use the existing display data.
      })

    return () => {
      cancelled = true
    }
  }, [storeId])

  useEffect(() => {
    let cancelled = false
    setCouponError(null)

    couponApi.getPublicCoupons(storeId)
      .then((response) => {
        if (cancelled) return
        setCoupons(response.map((coupon) => ({
          id: String(coupon.id),
          title: coupon.name,
          discount: formatDiscount(coupon),
          minOrder: coupon.minOrderAmount
            ? `${coupon.minOrderAmount.toLocaleString()}원 이상`
            : '없음',
          expiresAt: coupon.expiredAt.slice(0, 10),
          status: coupon.status === 'ACTIVE' ? '발급 가능' : '소진',
        })))
      })
      .catch((error: Error) => {
        if (!cancelled) setCouponError(error.message)
      })

    return () => {
      cancelled = true
    }
  }, [storeId])

  useEffect(() => {
    if (!selectedDate) {
      setSlots([])
      return
    }

    let cancelled = false
    setSlotsLoading(true)
    setSlotsError(null)
    reservationApi.getSlots(storeId, selectedDate)
      .then((response) => {
        if (!cancelled) setSlots(response)
      })
      .catch((error: Error) => {
        if (!cancelled) {
          setSlots([])
          setSlotsError(error.message)
        }
      })
      .finally(() => {
        if (!cancelled) setSlotsLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [selectedDate, storeId])

  if (!store) return null

  const reservationStatus = popupDetail
    ? popupDetail.status === 'OPEN'
      ? '예약 가능'
      : popupDetail.status === 'UPCOMING'
        ? '오픈예정'
        : '마감'
    : store.reservationStatus
  const isClosed = reservationStatus === '마감'
  const isComingSoon = reservationStatus === '오픈예정'
  const startDate = popupDetail?.openDate.slice(0, 10) ?? store.startDate
  const endDate = popupDetail?.closeDate.slice(0, 10) ?? store.endDate
  const operatingDates = getOperatingDates(startDate, endDate)
  const ticketPrice = popupDetail?.price ?? store.ticketPrice
  const displayName = popupDetail?.title ?? store.name
  const displayLocation = popupDetail?.location ?? store.location
  const displayImage = popupDetail?.imageUrl ?? store.image
  const displayDescription = popupDetail?.description ?? store.description
  const displayPeriod = `${startDate} ~ ${endDate}`

  // Cart calculations
  const totalItems = Object.values(cart).reduce((sum, q) => sum + q, 0)
  const totalAmount = store.goods.reduce((sum, item) => {
    return sum + item.price * (cart[item.id] ?? 0)
  }, 0)
  const hasCartItems = totalItems > 0

  function handleDateSelect(date: string) {
    setSelectedDate(date)
    setSelectedSlotId(null)
  }

  function handleAdd(goodsId: string) {
    setCart((prev) => ({ ...prev, [goodsId]: (prev[goodsId] ?? 0) + 1 }))
  }

  function handleRemove(goodsId: string) {
    setCart((prev) => {
      const next = { ...prev }
      if ((next[goodsId] ?? 0) <= 1) {
        delete next[goodsId]
      } else {
        next[goodsId] = next[goodsId] - 1
      }
      return next
    })
  }

  function handleOrderGoods() {
    const cartItems = Object.entries(cart)
      .filter(([, qty]) => qty > 0)
      .map(([goodsId, quantity]) => ({ goodsId, quantity }))
    onOrderGoods({ storeId, cart: cartItems, appliedCouponId: null })
  }

  return (
    <div className="relative flex flex-col h-full overflow-hidden">
      <div className="flex-1 overflow-y-auto scrollbar-hide">
        {/* Hero Image */}
        <div className="relative w-full aspect-[4/3] bg-secondary overflow-hidden">
          <img
            src={displayImage}
            alt={displayName}
            className={cn('w-full h-full object-cover', isClosed && 'grayscale')}
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-transparent to-black/20" />

          {/* Back + actions */}
          <div className="absolute top-0 left-0 right-0 flex items-center justify-between p-3">
            <button
              onClick={onBack}
              className="flex items-center justify-center w-9 h-9 bg-black/40 backdrop-blur-sm text-white rounded-full"
              aria-label="뒤로 가기"
            >
              <ArrowLeft size={18} />
            </button>
            <div className="flex items-center gap-2">
              <button className="flex items-center justify-center w-9 h-9 bg-black/40 backdrop-blur-sm text-white rounded-full">
                <Share2 size={16} />
              </button>
              <button
                onClick={() => setLiked((l) => !l)}
                className="flex items-center justify-center w-9 h-9 bg-black/40 backdrop-blur-sm text-white rounded-full"
              >
                <Heart size={16} fill={liked ? 'white' : 'none'} />
              </button>
            </div>
          </div>

          {/* Title overlay */}
          <div className="absolute bottom-0 left-0 right-0 p-4">
            <h1 className="text-white text-xl font-black leading-tight text-balance">{displayName}</h1>
            <div className="flex flex-wrap gap-1.5 mt-2">
              {store.tags.map((tag) => (
                <span
                  key={tag}
                  className="bg-white/20 backdrop-blur-sm text-white text-[10px] font-semibold px-2 py-0.5 rounded-full"
                >
                  #{tag}
                </span>
              ))}
            </div>
          </div>

          {isClosed && (
            <div className="absolute inset-0 flex items-center justify-center">
              <span className="bg-black/70 text-white text-lg font-black px-6 py-3 rounded-full tracking-widest">
                마감
              </span>
            </div>
          )}
        </div>

        {/* Body */}
        <div className="p-4 space-y-6">
          {/* Info card */}
          <div className="bg-secondary rounded-xl p-4 space-y-2.5">
            <div className="flex items-start gap-2.5">
              <MapPin size={14} strokeWidth={1.8} className="text-muted-foreground mt-0.5 shrink-0" />
              <span className="text-sm text-foreground">{displayLocation}</span>
            </div>
            <div className="flex items-start gap-2.5">
              <Calendar size={14} strokeWidth={1.8} className="text-muted-foreground mt-0.5 shrink-0" />
              <span className="text-sm text-foreground">{displayPeriod}</span>
            </div>
          </div>

          {/* Description */}
          <p className="text-sm text-muted-foreground leading-relaxed">{displayDescription}</p>

          {/* Reservation Section */}
          <div className="space-y-4">
            <div className="flex items-center justify-between">
              <SectionTitle>입장권 예약</SectionTitle>
              <StatusBadge status={reservationStatus} size="md" />
            </div>

            <div className="bg-[oklch(0.98_0.01_25)] border border-[oklch(0.9_0.03_25)] rounded-xl p-3 flex items-start gap-2">
              <Ticket size={14} strokeWidth={1.8} className="text-[oklch(0.62_0.24_25)] mt-0.5 shrink-0" />
              <p className="text-[12px] text-[oklch(0.35_0.1_25)] font-medium">
                입장권은 선착순으로 발급됩니다. 예약 완료 후 취소 시 재예약이 제한될 수 있습니다.
              </p>
            </div>

            {!isClosed && !isComingSoon && (
              <>
                {/* Step 1: Date Selection */}
                <div className="space-y-2">
                  <p className="text-xs font-bold text-muted-foreground tracking-wide">1단계 · 방문 날짜 선택</p>
                  <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-0.5">
                    {operatingDates.map((date) => {
                      const isSelected = selectedDate === date
                      return (
                        <button
                          key={date}
                          onClick={() => handleDateSelect(date)}
                          className={cn(
                            'flex-shrink-0 flex flex-col items-center justify-center rounded-xl px-3 py-2.5 border-2 transition-all min-w-[60px]',
                            isSelected
                              ? 'border-foreground bg-foreground text-background'
                              : 'border-border bg-card hover:border-foreground/40',
                          )}
                        >
                          <span className={cn('text-[10px] font-semibold', isSelected ? 'text-background/70' : 'text-muted-foreground')}>
                            {new Date(date).toLocaleDateString('ko-KR', { month: 'short' })}
                          </span>
                          <span className={cn('text-[16px] font-black leading-none', isSelected ? 'text-background' : 'text-foreground')}>
                            {new Date(date).getDate()}
                          </span>
                          <span className={cn('text-[10px] font-medium mt-0.5', isSelected ? 'text-background/70' : 'text-muted-foreground')}>
                            {['일', '월', '화', '수', '목', '금', '토'][new Date(date).getDay()]}
                          </span>
                        </button>
                      )
                    })}
                  </div>
                </div>

                {/* Step 2: Time Slot Selection */}
                {selectedDate && (
                  <div className="space-y-2">
                    <div className="flex items-center justify-between">
                      <p className="text-xs font-bold text-muted-foreground tracking-wide">2단계 · 시간대 선택</p>
                      <span className="text-[11px] text-muted-foreground">{formatDateKorean(selectedDate)}</span>
                    </div>
                    <div className="grid grid-cols-4 gap-2">
                      {slots.map((slot) => {
                        const remaining = slot.capacity - slot.reservedCount
                        const slotView: TimeSlot = {
                          time: slot.startTime.slice(0, 5),
                          status: !slot.available
                            ? '마감'
                            : remaining <= 3
                              ? '마감 임박'
                              : '예약 가능',
                        }
                        return (
                        <TimeSlotButton
                          key={slot.slotId}
                          slot={slotView}
                          selected={selectedSlotId === slot.slotId}
                          onClick={() => setSelectedSlotId(slot.slotId)}
                        />
                        )
                      })}
                    </div>
                    {slotsLoading && <p className="text-xs text-muted-foreground">예약 시간을 불러오는 중...</p>}
                    {!slotsLoading && slots.length === 0 && (
                      <p className="text-xs text-muted-foreground">선택한 날짜에 예약 가능한 시간이 없습니다.</p>
                    )}
                    {slotsError && <p className="text-xs text-[oklch(0.62_0.24_25)]">{slotsError}</p>}
                  </div>
                )}
              </>
            )}
          </div>

          {/* Goods Section */}
          {store.goods.length > 0 && (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <ShoppingBag size={16} strokeWidth={2} />
                <SectionTitle>한정 굿즈</SectionTitle>
              </div>
              <div className="grid grid-cols-2 gap-3">
                {store.goods.map((item) => (
                  <GoodsCard
                    key={item.id}
                    item={item}
                    quantity={cart[item.id] ?? 0}
                    onAdd={() => handleAdd(item.id)}
                    onRemove={() => handleRemove(item.id)}
                    onOpenDetail={() => setSheetGoodsId(item.id)}
                  />
                ))}
              </div>
            </div>
          )}

          {/* Coupon Section */}
          {(coupons.length > 0 || couponError) && (
            <div className="space-y-3">
              <div className="flex items-center gap-2">
                <TicketIcon size={16} strokeWidth={2} />
                <SectionTitle>오프라인 쿠폰</SectionTitle>
              </div>
              <div className="space-y-2">
                {coupons.map((coupon) => (
                  <CouponCard
                    key={coupon.id}
                    coupon={coupon}
                    onIssue={() => onIssueCoupon({ storeId, couponId: coupon.id })}
                  />
                ))}
                {couponError && (
                  <p className="text-xs text-[oklch(0.62_0.24_25)]">{couponError}</p>
                )}
              </div>
            </div>
          )}

          <div className="h-4" />
        </div>
      </div>

      {/* Sticky bottom — two CTAs stacked when goods are present */}
      <div className="border-t border-border bg-card">
        {/* Goods CTA */}
        {store.goods.length > 0 && (
          <div className="px-4 pt-3 pb-2">
            {hasCartItems ? (
              <button
                onClick={handleOrderGoods}
                className="w-full py-3.5 rounded-xl bg-foreground text-background font-bold text-sm flex items-center justify-between px-4 active:scale-[0.98] transition-all"
              >
                <span className="flex items-center gap-2">
                  <ShoppingBag size={15} strokeWidth={2.5} />
                  <span>굿즈 주문하기</span>
                  <span className="bg-white/20 text-background text-[11px] font-bold px-1.5 py-0.5 rounded-full">
                    {totalItems}
                  </span>
                </span>
                <span className="font-black">{totalAmount.toLocaleString()}원</span>
              </button>
            ) : (
              <div className="w-full py-3 rounded-xl bg-secondary flex items-center justify-center gap-2">
                <ShoppingBag size={14} className="text-muted-foreground" />
                <span className="text-[13px] text-muted-foreground font-medium">굿즈를 선택해주세요.</span>
              </div>
            )}
          </div>
        )}

        {/* Reservation CTA */}
        <div className="px-4 pt-1 pb-3">
          {isComingSoon ? (
            <button
              disabled
              className="w-full py-3.5 rounded-xl bg-secondary text-muted-foreground font-bold text-sm cursor-not-allowed"
            >
              오픈 예정
            </button>
          ) : isClosed ? (
            <button
              disabled
              className="w-full py-3.5 rounded-xl bg-secondary text-muted-foreground font-bold text-sm cursor-not-allowed"
            >
              예약 마감
            </button>
          ) : (
            <div className="space-y-1.5">
              {(!selectedDate || !selectedSlotId) && (
                <p className="text-center text-xs text-muted-foreground">
                  예약 날짜와 시간을 선택해주세요.
                </p>
              )}
              <button
                disabled={!selectedDate || !selectedSlotId}
                onClick={() => {
                  const selectedSlot = slots.find((slot) => slot.slotId === selectedSlotId)
                  if (selectedDate && selectedSlot) {
                    onReserve({
                      storeId,
                      slotId: selectedSlot.slotId,
                      date: selectedDate,
                      time: selectedSlot.startTime.slice(0, 5),
                    })
                  }
                }}
                className={cn(
                  'w-full py-3.5 rounded-xl font-bold text-sm transition-all',
                  selectedDate && selectedSlotId
                    ? 'bg-foreground text-background active:scale-[0.98]'
                    : 'bg-secondary text-muted-foreground cursor-not-allowed',
                )}
              >
                {!selectedDate || !selectedSlotId
                  ? '지금 예약하기'
                  : ticketPrice > 0
                  ? `결제하기 · ${ticketPrice.toLocaleString()}원`
                  : '무료 예약하기'}
              </button>
            </div>
          )}
        </div>
      </div>

      {/* Goods Detail Bottom Sheet */}
      {sheetGoodsId && (() => {
        const sheetItem = store.goods.find((g) => g.id === sheetGoodsId)
        if (!sheetItem) return null
        return (
          <GoodsDetailSheet
            key={sheetGoodsId}
            item={sheetItem}
            quantity={cart[sheetGoodsId] ?? 0}
            onClose={() => setSheetGoodsId(null)}
            onAdd={() => handleAdd(sheetGoodsId)}
            onRemove={() => handleRemove(sheetGoodsId)}
          />
        )
      })()}
    </div>
  )
}
