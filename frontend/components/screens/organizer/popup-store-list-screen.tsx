'use client'

import { useEffect, useState } from 'react'
import { ArrowLeft, Plus, Pencil, Trash2, Store, ShoppingBag, Tag } from 'lucide-react'
import { cn } from '@/lib/utils'
import { StatusBadge } from '@/components/ui/status-badge'
import type { OrgPopupStore } from '@/lib/data'
import { getPopups, toOrgPopupStore, deletePopup } from '@/lib/popup-api'

interface PopupStoreListScreenProps {
  onBack: () => void
  onCreate: () => void
  onEdit: (storeId: string) => void
  onGoGoods: (storeId: string, storeName: string) => void
  onGoCoupons: (storeId: string, storeName: string) => void
}

function DeleteConfirmModal({
  store,
  onCancel,
  onConfirm,
}: {
  store: OrgPopupStore
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl p-6 pb-8 space-y-4">
        <h3 className="text-base font-bold text-foreground">팝업스토어를 삭제하시겠습니까?</h3>
        <div className="flex items-center gap-3 bg-secondary rounded-xl p-3">
          <img
            src={store.image}
            alt={store.name}
            crossOrigin="anonymous"
            className="w-12 h-12 rounded-lg object-cover shrink-0"
          />
          <div className="flex-1 min-w-0">
            <p className="text-[13px] font-semibold text-foreground line-clamp-1">{store.name}</p>
            <p className="text-[11px] text-muted-foreground mt-0.5">{store.location}</p>
          </div>
        </div>
        <p className="text-[13px] text-muted-foreground">
          삭제하면 모든 예약 슬롯과 데이터가 영구적으로 제거됩니다. 이 작업은 되돌릴 수 없습니다.
        </p>
        <div className="flex gap-2 pt-1">
          <button
            onClick={onCancel}
            className="flex-1 py-3.5 rounded-xl bg-secondary text-foreground font-semibold text-sm"
          >
            취소
          </button>
          <button
            onClick={onConfirm}
            className="flex-1 py-3.5 rounded-xl bg-[oklch(0.62_0.24_25)] text-white font-semibold text-sm"
          >
            삭제
          </button>
        </div>
      </div>
    </div>
  )
}

export function PopupStoreListScreen({ onBack, onCreate, onEdit, onGoGoods, onGoCoupons }: PopupStoreListScreenProps) {
  const [stores, setStores] = useState<OrgPopupStore[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deletingStore, setDeletingStore] = useState<OrgPopupStore | null>(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const res = await getPopups()
        if (!cancelled) setStores(res.content.map(toOrgPopupStore))
      } catch (e) {
        if (!cancelled) setError(e instanceof Error ? e.message : '목록을 불러오지 못했습니다.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  async function handleDeleteConfirm() {
    if (!deletingStore || deleting) return
    setDeleting(true)
    try {
      await deletePopup(deletingStore.id)
      setStores((prev) => prev.filter((s) => s.id !== deletingStore.id))
      setDeletingStore(null)
    } catch (e) {
      alert(e instanceof Error ? e.message : '삭제에 실패했습니다.')
    } finally {
      setDeleting(false)
    }
  }

  const statusLabel: Record<string, string> = {
    '오픈예정': '오픈 예정',
    '운영중': '운영중',
    '접수마감': '접수 마감',
    '예약마감': '예약 마감',
  }

  return (
    <div className="relative flex flex-col h-full overflow-hidden">
      {/* Header */}
      <header className="flex items-center gap-3 px-4 py-3 bg-card border-b border-border shrink-0">
        <button
          onClick={onBack}
          aria-label="뒤로 가기"
          className="flex items-center justify-center w-8 h-8 -ml-1 rounded-full hover:bg-secondary transition-colors"
        >
          <ArrowLeft size={20} strokeWidth={2} />
        </button>
        <h1 className="text-base font-bold text-foreground flex-1">내 팝업스토어</h1>
        <button
          onClick={onCreate}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-foreground text-background text-[12px] font-bold rounded-lg"
        >
          <Plus size={13} strokeWidth={2.5} />
          만들기
        </button>
      </header>

      {/* List */}
      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <p className="text-[13px] text-muted-foreground">불러오는 중...</p>
          </div>
        ) : error ? (
          <div className="flex flex-col items-center justify-center h-full gap-3 px-8 text-center">
            <div className="w-16 h-16 rounded-full bg-secondary flex items-center justify-center">
              <Store size={28} strokeWidth={1.4} className="text-muted-foreground" />
            </div>
            <p className="text-[13px] text-muted-foreground leading-relaxed">{error}</p>
          </div>
        ) : stores.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-4 px-8 text-center">
            <div className="w-16 h-16 rounded-full bg-secondary flex items-center justify-center">
              <Store size={28} strokeWidth={1.4} className="text-muted-foreground" />
            </div>
            <p className="text-[14px] font-semibold text-foreground">아직 등록된 팝업스토어가 없습니다.</p>
            <p className="text-[12px] text-muted-foreground leading-relaxed">
              첫 팝업스토어를 만들어 예약을 받아보세요.
            </p>
            <button
              onClick={onCreate}
              className="mt-1 px-6 py-3 bg-foreground text-background text-[13px] font-bold rounded-xl flex items-center gap-2"
            >
              <Plus size={15} strokeWidth={2.5} />
              팝업스토어 만들기
            </button>
          </div>
        ) : (
          <div className="space-y-3 px-4 pt-4">
            {stores.map((store) => {
              const ratio = store.capacity > 0 ? store.reservations / store.capacity : 0
              return (
              <div
                key={store.id}
                className="bg-card border border-border rounded-2xl overflow-hidden"
              >
                {/* Card image + main info (display only) */}
                <div className="flex items-start gap-3 p-3.5">
                  <img
                    src={store.image}
                    alt={store.name}
                    crossOrigin="anonymous"
                    className="w-[72px] h-[72px] rounded-xl object-cover shrink-0"
                  />
                  <div className="flex-1 min-w-0 space-y-1">
                    <div className="flex items-center gap-1.5 flex-wrap">
                      <StatusBadge status={store.status} size="sm" />
                      <span className="text-[10px] text-muted-foreground">
                        {statusLabel[store.status]}
                      </span>
                    </div>
                    <p className="text-[13px] font-bold text-foreground leading-snug line-clamp-2">
                      {store.name}
                    </p>
                    <p className="text-[11px] text-muted-foreground">{store.location}</p>
                    <p className="text-[11px] text-muted-foreground">
                      {store.operationStart} ~ {store.operationEnd}
                    </p>
                  </div>
                </div>

                {/* Reservation stats */}
                <div className="px-3.5 pb-2.5 flex items-center gap-3">
                  <div className="flex-1">
                    <span className="text-[11px] text-muted-foreground">예약 현황 </span>
                    <span className="text-[12px] font-black text-foreground">{store.reservations}</span>
                    <span className="text-[11px] text-muted-foreground"> / {store.capacity}</span>
                    <div className="mt-1.5 h-1 w-24 bg-secondary rounded-full overflow-hidden">
                      <div
                        className={cn(
                          'h-full rounded-full',
                          ratio >= 1
                            ? 'bg-[oklch(0.5_0_0)]'
                            : ratio >= 0.8
                            ? 'bg-[oklch(0.62_0.24_25)]'
                            : 'bg-foreground',
                        )}
                        style={{ width: `${Math.min(ratio * 100, 100)}%` }}
                      />
                    </div>
                  </div>
                </div>

                {/* 4-button action row */}
                <div className="border-t border-border grid grid-cols-4 divide-x divide-border">
                  <button
                    onClick={() => onGoGoods(store.id, store.name)}
                    className="flex flex-col items-center gap-1 py-2.5 active:bg-secondary transition-colors"
                  >
                    <ShoppingBag size={14} strokeWidth={2} className="text-foreground" />
                    <span className="text-[10px] font-semibold text-foreground">굿즈 관리</span>
                  </button>
                  <button
                    onClick={() => onGoCoupons(store.id, store.name)}
                    className="flex flex-col items-center gap-1 py-2.5 active:bg-secondary transition-colors"
                  >
                    <Tag size={14} strokeWidth={2} className="text-foreground" />
                    <span className="text-[10px] font-semibold text-foreground">쿠폰 관리</span>
                  </button>
                  <button
                    onClick={() => onEdit(store.id)}
                    className="flex flex-col items-center gap-1 py-2.5 active:bg-secondary transition-colors"
                  >
                    <Pencil size={14} strokeWidth={2} className="text-foreground" />
                    <span className="text-[10px] font-semibold text-foreground">수정</span>
                  </button>
                  <button
                    onClick={() => setDeletingStore(store)}
                    className="flex flex-col items-center gap-1 py-2.5 active:bg-secondary transition-colors"
                  >
                    <Trash2 size={14} strokeWidth={2} className="text-[oklch(0.62_0.24_25)]" />
                    <span className="text-[10px] font-semibold text-[oklch(0.62_0.24_25)]">삭제</span>
                  </button>
                </div>
              </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Delete modal */}
      {deletingStore && (
        <DeleteConfirmModal
          store={deletingStore}
          onCancel={() => setDeletingStore(null)}
          onConfirm={handleDeleteConfirm}
        />
      )}
    </div>
  )
}
