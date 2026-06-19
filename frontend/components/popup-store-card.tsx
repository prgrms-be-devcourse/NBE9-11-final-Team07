'use client'

import { MapPin, Clock, ShoppingBag, TicketIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { StatusBadge } from '@/components/ui/status-badge'
import type { PopupStore } from '@/lib/data'

interface PopupStoreCardProps {
  store: PopupStore
  onClick?: () => void
}

export function PopupStoreCard({ store, onClick }: PopupStoreCardProps) {
  const isClosed = store.reservationStatus === '마감'
  const isUrgent = store.reservationStatus === '마감 임박'

  return (
    <button
      onClick={onClick}
      className="w-full text-left bg-card rounded-xl overflow-hidden border border-border active:scale-[0.98] transition-transform"
    >
      {/* Cover Image */}
      <div className="relative w-full aspect-[4/3] overflow-hidden bg-secondary">
        <img
          src={store.image}
          alt={store.name}
          className={cn('w-full h-full object-cover', isClosed && 'grayscale opacity-70')}
        />
        {/* Urgency badge */}
        {isUrgent && (
          <div className="absolute top-2.5 left-2.5">
            <span className="bg-[oklch(0.62_0.24_25)] text-white text-[10px] font-bold px-2 py-0.5 rounded-full tracking-wide">
              마감 임박
            </span>
          </div>
        )}
        {isClosed && (
          <div className="absolute inset-0 flex items-center justify-center">
            <span className="bg-black/70 text-white text-sm font-bold px-4 py-2 rounded-full tracking-widest">
              마감
            </span>
          </div>
        )}
        {/* Feature badges */}
        <div className="absolute bottom-2.5 left-2.5 flex items-center gap-1">
          {store.hasGoods && (
            <span className="flex items-center gap-0.5 bg-black/60 text-white text-[9px] font-semibold px-1.5 py-0.5 rounded-full backdrop-blur-sm">
              <ShoppingBag size={9} />
              굿즈
            </span>
          )}
          {store.hasCoupon && (
            <span className="flex items-center gap-0.5 bg-black/60 text-white text-[9px] font-semibold px-1.5 py-0.5 rounded-full backdrop-blur-sm">
              <TicketIcon size={9} />
              쿠폰
            </span>
          )}
        </div>
      </div>

      {/* Content */}
      <div className="p-3 space-y-2">
        <div className="flex items-start justify-between gap-2">
          <h3 className={cn('text-sm font-bold leading-snug text-balance', isClosed && 'text-muted-foreground')}>
            {store.name}
          </h3>
          <StatusBadge status={store.reservationStatus} className="shrink-0 mt-0.5" />
        </div>
        <div className="space-y-1">
          <div className="flex items-center gap-1 text-muted-foreground">
            <MapPin size={11} strokeWidth={1.8} />
            <span className="text-[11px]">{store.location}</span>
          </div>
          <div className="flex items-center gap-1 text-muted-foreground">
            <Clock size={11} strokeWidth={1.8} />
            <span className="text-[11px]">{store.period}</span>
          </div>
        </div>
      </div>
    </button>
  )
}
