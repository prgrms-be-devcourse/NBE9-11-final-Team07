'use client'

import { useState, useRef, useEffect, useCallback } from 'react'
import { ChevronRight, ChevronLeft, TicketIcon } from 'lucide-react'
import { cn } from '@/lib/utils'
import { AppHeader } from '@/components/app-header'
import { PopupStoreCard } from '@/components/popup-store-card'
import { promoBanners } from '@/lib/data'
import type { PopupStore } from '@/lib/data'
import { getPopups, toPopupStoreFromList } from '@/lib/popup-api'

const categories = ['전체', '진행중', '오픈예정', '마감임박', '굿즈판매'] as const
type Category = (typeof categories)[number]

function filterStores(stores: PopupStore[], category: Category): PopupStore[] {
  if (category === '전체') return stores
  return stores.filter((s) => s.category.includes(category))
}

interface HomeScreenProps {
  onStoreSelect: (storeId: string) => void
  onCouponBannerSelect: (storeId: string) => void
}

export function HomeScreen({ onStoreSelect, onCouponBannerSelect }: HomeScreenProps) {
  const [activeCategory, setActiveCategory] = useState<Category>('전체')
  const [currentSlide, setCurrentSlide] = useState(0)
  const [stores, setStores] = useState<PopupStore[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  useEffect(() => {
    let active = true
    setLoading(true)
    getPopups()
      .then((res) => {
        if (!active) return
        const list = res.content ?? []
        setStores(list.map(toPopupStoreFromList))
        setError(null)
      })
      .catch((e) => {
        if (!active) return
        setError(e instanceof Error ? e.message : '팝업 목록을 불러오지 못했습니다.')
      })
      .finally(() => {
        if (active) setLoading(false)
      })
    return () => {
      active = false
    }
  }, [])

  const filtered = filterStores(stores, activeCategory)

  const startTimer = useCallback(() => {
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = setInterval(() => {
      setCurrentSlide((prev) => (prev + 1) % promoBanners.length)
    }, 3500)
  }, [])

  useEffect(() => {
    startTimer()
    return () => {
      if (timerRef.current) clearInterval(timerRef.current)
    }
  }, [startTimer])

  function handleSlideChange(idx: number) {
    setCurrentSlide(idx)
    startTimer()
  }

  function handlePrev() {
    setCurrentSlide((prev) => (prev - 1 + promoBanners.length) % promoBanners.length)
    startTimer()
  }

  function handleNext() {
    setCurrentSlide((prev) => (prev + 1) % promoBanners.length)
    startTimer()
  }

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <AppHeader />

      <div className="flex-1 overflow-y-auto scrollbar-hide">
        {/* Coupon Promo Carousel */}
        <div className="px-4 pt-3 pb-4">
          <div className="relative w-full rounded-2xl overflow-hidden aspect-[2/1]">
            {/* Slides */}
            {promoBanners.map((banner, idx) => (
              <button
                key={banner.id}
                onClick={() => onCouponBannerSelect(banner.storeId)}
                className={cn(
                  'absolute inset-0 w-full h-full text-left transition-opacity duration-500',
                  idx === currentSlide ? 'opacity-100 z-10' : 'opacity-0 z-0',
                )}
                aria-label={`${banner.storeName} 쿠폰 보기`}
              >
                <img
                  src={banner.image}
                  alt={banner.storeName}
                  className="w-full h-full object-cover"
                />
                {/* Overlay */}
                <div className="absolute inset-0 bg-gradient-to-t from-black/85 via-black/30 to-transparent" />
                {/* Coupon badge */}
                <div className="absolute top-3 left-3">
                  <span className="flex items-center gap-1 bg-[oklch(0.62_0.24_25)] text-white text-[10px] font-bold px-2.5 py-1 rounded-full">
                    <TicketIcon size={10} />
                    쿠폰 혜택
                  </span>
                </div>
                {/* Content */}
                <div className="absolute bottom-0 left-0 right-0 p-4">
                  <p className="text-white/70 text-[11px] font-medium mb-0.5">{banner.storeName}</p>
                  <h2 className="text-white font-bold text-[15px] leading-snug text-balance mb-1">
                    {banner.couponTitle}
                  </h2>
                  <p className="text-white/60 text-[11px] mb-2.5">{banner.discountInfo}</p>
                  <div className="flex items-center gap-1">
                    <span className="text-[oklch(0.62_0.24_25)] text-[12px] font-bold">
                      {banner.cta}
                    </span>
                    <ChevronRight size={13} className="text-[oklch(0.62_0.24_25)]" />
                  </div>
                </div>
              </button>
            ))}

            {/* Dot indicators */}
            <div className="absolute bottom-3 right-3 z-20 flex items-center gap-1">
              {promoBanners.map((_, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSlideChange(idx)}
                  aria-label={`슬라이드 ${idx + 1}`}
                  className={cn(
                    'rounded-full transition-all duration-300',
                    idx === currentSlide
                      ? 'w-4 h-1.5 bg-white'
                      : 'w-1.5 h-1.5 bg-white/50',
                  )}
                />
              ))}
            </div>

            {/* Slide counter */}
            <div className="absolute top-3 right-3 z-20">
              <span className="bg-black/40 text-white/80 text-[10px] font-semibold px-2 py-0.5 rounded-full backdrop-blur-sm">
                {currentSlide + 1} / {promoBanners.length}
              </span>
            </div>

            {/* Prev / Next arrows */}
            <button
              onClick={handlePrev}
              aria-label="이전 슬라이드"
              className="absolute left-2 top-1/2 -translate-y-1/2 z-20 flex items-center justify-center w-7 h-7 rounded-full bg-black/40 backdrop-blur-sm text-white hover:bg-black/60 transition-colors"
            >
              <ChevronLeft size={15} strokeWidth={2.5} />
            </button>
            <button
              onClick={handleNext}
              aria-label="다음 슬라이드"
              className="absolute right-2 top-1/2 -translate-y-1/2 z-20 flex items-center justify-center w-7 h-7 rounded-full bg-black/40 backdrop-blur-sm text-white hover:bg-black/60 transition-colors"
            >
              <ChevronRight size={15} strokeWidth={2.5} />
            </button>
          </div>
        </div>

        {/* Category Tabs */}
        <div className="px-4 pb-3">
          <div className="flex gap-2 overflow-x-auto scrollbar-hide pb-0.5">
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => setActiveCategory(cat)}
                className={cn(
                  'flex-shrink-0 px-4 py-2 rounded-full text-[13px] font-semibold transition-colors',
                  activeCategory === cat
                    ? 'bg-foreground text-background'
                    : 'bg-secondary text-muted-foreground hover:text-foreground',
                )}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>

        {/* Count */}
        <div className="px-4 pb-2">
          <span className="text-[12px] text-muted-foreground font-medium">
            {filtered.length}개의 팝업스토어
          </span>
        </div>

        {/* Store Grid */}
        <div className="px-4 pb-6 grid grid-cols-2 gap-3">
          {filtered.map((store) => (
            <PopupStoreCard
              key={store.id}
              store={store}
              onClick={() => onStoreSelect(store.id)}
            />
          ))}
          {loading && (
            <div className="col-span-2 flex flex-col items-center justify-center py-16 text-muted-foreground gap-2">
              <p className="text-sm font-medium">불러오는 중...</p>
            </div>
          )}
          {!loading && error && (
            <div className="col-span-2 flex flex-col items-center justify-center py-16 text-muted-foreground gap-2">
              <p className="text-sm font-medium">{error}</p>
            </div>
          )}
          {!loading && !error && filtered.length === 0 && (
            <div className="col-span-2 flex flex-col items-center justify-center py-16 text-muted-foreground gap-2">
              <p className="text-sm font-medium">해당 팝업스토어가 없습니다</p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
