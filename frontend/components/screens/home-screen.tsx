'use client'

import { useState, useEffect } from 'react'
import { cn } from '@/lib/utils'
import { AppHeader } from '@/components/app-header'
import { PopupStoreCard } from '@/components/popup-store-card'
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
}

export function HomeScreen({ onStoreSelect }: HomeScreenProps) {
  const [activeCategory, setActiveCategory] = useState<Category>('전체')
  const [stores, setStores] = useState<PopupStore[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

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

  return (
    <div className="flex flex-col h-full overflow-hidden">
      <AppHeader />

      <div className="flex-1 overflow-y-auto scrollbar-hide">
        {/* Category Tabs */}
        <div className="px-4 pt-4 pb-3">
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
