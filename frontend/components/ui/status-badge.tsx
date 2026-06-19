'use client'

import { cn } from '@/lib/utils'
import type { ReservationStatus, StockStatus, CouponStatus, ReservationHistoryStatus, OrgStoreStatus, OrgCouponStatus, GoodsSalesStatus } from '@/lib/data'

type BadgeVariant = ReservationStatus | StockStatus | CouponStatus | ReservationHistoryStatus | OrgStoreStatus | OrgCouponStatus | GoodsSalesStatus

const variantStyles: Record<string, string> = {
  // Reservation slot / store status
  '예약 가능':  'bg-[oklch(0.94_0.06_145)] text-[oklch(0.3_0.1_145)]',
  '마감 임박':  'bg-[oklch(0.97_0.06_58)] text-[oklch(0.45_0.15_58)]',
  '마감':       'bg-[oklch(0.93_0_0)] text-[oklch(0.5_0_0)]',
  '오픈예정':   'bg-[oklch(0.94_0.04_250)] text-[oklch(0.35_0.1_250)]',
  // Goods
  '판매중':     'bg-[oklch(0.94_0.06_145)] text-[oklch(0.3_0.1_145)]',
  '품절':       'bg-[oklch(0.93_0_0)] text-[oklch(0.5_0_0)]',
  // Coupons
  '발급 가능':  'bg-[oklch(0.94_0.06_145)] text-[oklch(0.3_0.1_145)]',
  '소진':       'bg-[oklch(0.93_0_0)] text-[oklch(0.5_0_0)]',
  // Reservation history
  '예약 완료':  'bg-[oklch(0.94_0.06_145)] text-[oklch(0.3_0.1_145)]',
  '방문 완료':  'bg-[oklch(0.93_0_0)] text-[oklch(0.5_0_0)]',
  '예약 취소':  'bg-[oklch(0.97_0.06_25)] text-[oklch(0.45_0.15_25)]',
  // Organizer popup store status
  '운영중':     'bg-[oklch(0.94_0.06_145)] text-[oklch(0.3_0.1_145)]',
  '접수마감':   'bg-[oklch(0.97_0.06_58)] text-[oklch(0.45_0.15_58)]',
  '예약마감':   'bg-[oklch(0.93_0_0)] text-[oklch(0.5_0_0)]',
  // Organizer coupon status
  'ACTIVE':     'bg-[oklch(0.94_0.06_145)] text-[oklch(0.3_0.1_145)]',
  'SOLDOUT':    'bg-[oklch(0.97_0.06_58)] text-[oklch(0.45_0.15_58)]',
  'EXPIRED':    'bg-[oklch(0.93_0_0)] text-[oklch(0.5_0_0)]',
  // Organizer goods sales status
  'READY':      'bg-[oklch(0.94_0.04_250)] text-[oklch(0.35_0.1_250)]',
  'ON_SALE':    'bg-[oklch(0.94_0.06_145)] text-[oklch(0.3_0.1_145)]',
  'SOLD_OUT':   'bg-[oklch(0.93_0_0)] text-[oklch(0.5_0_0)]',
}

interface StatusBadgeProps {
  status: BadgeVariant | string
  className?: string
  size?: 'sm' | 'md'
}

export function StatusBadge({ status, className, size = 'sm' }: StatusBadgeProps) {
  const baseStyle = variantStyles[status] ?? 'bg-secondary text-secondary-foreground'
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full font-semibold tracking-tight',
        size === 'sm' ? 'px-2 py-0.5 text-[10px]' : 'px-2.5 py-1 text-xs',
        baseStyle,
        className,
      )}
    >
      {status}
    </span>
  )
}
