'use client'

import { useState, useEffect } from 'react'
import { ArrowLeft, Plus, Pencil, Trash2, ShoppingBag } from 'lucide-react'
import { StatusBadge } from '@/components/ui/status-badge'
import { hostGoodsApi, deleteHostGoods } from '@/lib/goods-api'
import type { HostGoodsListResponse } from '@/lib/goods-api'

function goodsStatusFromStock(stock: number): 'ON_SALE' | 'SOLD_OUT' {
  return stock === 0 ? 'SOLD_OUT' : 'ON_SALE'
}

// ─── Delete Modal ─────────────────────────────────────────────────────────────

function DeleteGoodsModal({
  goods,
  onCancel,
  onConfirm,
  isDeleting,
  error,
}: {
  goods: HostGoodsListResponse
  onCancel: () => void
  onConfirm: () => void
  isDeleting: boolean
  error: string | null
}) {
  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl p-6 pb-8 space-y-4">
        <h3 className="text-base font-bold text-foreground">굿즈를 삭제하시겠습니까?</h3>
        <div className="flex items-center gap-3 bg-secondary rounded-xl p-3">
          <img
            src={goods.productImageUrl ?? undefined}
            alt={goods.name}
            crossOrigin="anonymous"
            className="w-12 h-12 rounded-lg object-cover shrink-0"
          />
          <div className="flex-1 min-w-0">
            <p className="text-[13px] font-semibold text-foreground line-clamp-1">{goods.name}</p>
            <p className="text-[11px] text-muted-foreground mt-0.5">
              {goods.price.toLocaleString()}원 · 재고 {goods.stock}개
            </p>
          </div>
        </div>
        <p className="text-[13px] text-muted-foreground leading-relaxed">
          삭제된 굿즈는 복구할 수 없습니다.
        </p>
        {error && (
          <p className="text-[12px] text-[oklch(0.62_0.24_25)] leading-snug">{error}</p>
        )}
        <div className="flex gap-2 pt-1">
          <button
            onClick={onCancel}
            disabled={isDeleting}
            className="flex-1 py-3.5 rounded-xl bg-secondary text-foreground font-semibold text-sm disabled:opacity-50"
          >
            취소
          </button>
          <button
            onClick={onConfirm}
            disabled={isDeleting}
            className="flex-1 py-3.5 rounded-xl bg-[oklch(0.62_0.24_25)] text-white font-semibold text-sm disabled:opacity-60"
          >
            {isDeleting ? '삭제 중...' : '삭제'}
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Goods Card ───────────────────────────────────────────────────────────────

function GoodsCard({
  goods,
  onEdit,
  onDelete,
}: {
  goods: HostGoodsListResponse
  onEdit: () => void
  onDelete: () => void
}) {
  const derivedStatus = goodsStatusFromStock(goods.stock)
  const statusLabel: Record<string, string> = {
    ON_SALE: '판매중',
    SOLD_OUT: '품절',
  }

  return (
    <div className="bg-card border border-border rounded-2xl overflow-hidden">
      <div className="flex items-start gap-3 p-3.5">
        <img
          src={goods.productImageUrl ?? undefined}
          alt={goods.name}
          crossOrigin="anonymous"
          className="w-[72px] h-[72px] rounded-xl object-cover shrink-0"
        />
        <div className="flex-1 min-w-0 space-y-1">
          <div className="flex items-center gap-1.5">
            <StatusBadge status={derivedStatus} size="sm" />
            <span className="text-[10px] text-muted-foreground">{statusLabel[derivedStatus]}</span>
          </div>
          <p className="text-[13px] font-bold text-foreground leading-snug line-clamp-2">
            {goods.name}
          </p>
          <p className="text-[12px] font-semibold text-foreground">
            {goods.price.toLocaleString()}원
          </p>
          <p className="text-[11px] text-muted-foreground">재고 {goods.stock}개</p>
        </div>
      </div>
      {/* Action row */}
      <div className="border-t border-border grid grid-cols-2 divide-x divide-border">
        <button
          onClick={onEdit}
          className="flex items-center justify-center gap-1.5 py-2.5 active:bg-secondary transition-colors"
        >
          <Pencil size={13} strokeWidth={2} className="text-foreground" />
          <span className="text-[11px] font-semibold text-foreground">수정</span>
        </button>
        <button
          onClick={onDelete}
          className="flex items-center justify-center gap-1.5 py-2.5 active:bg-secondary transition-colors"
        >
          <Trash2 size={13} strokeWidth={2} className="text-[oklch(0.62_0.24_25)]" />
          <span className="text-[11px] font-semibold text-[oklch(0.62_0.24_25)]">삭제</span>
        </button>
      </div>
    </div>
  )
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

interface GoodsListScreenProps {
  storeId: string
  storeName?: string
  onBack: () => void
  onAdd: () => void
  onEdit: (goodsId: string) => void
}

export function GoodsListScreen({
  storeId,
  storeName,
  onBack,
  onAdd,
  onEdit,
}: GoodsListScreenProps) {
  const [goodsList, setGoodsList] = useState<HostGoodsListResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [deletingGoods, setDeletingGoods] = useState<HostGoodsListResponse | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    hostGoodsApi.getGoods(storeId)
      .then((data) => {
        if (!cancelled) {
          setGoodsList(data)
          setLoading(false)
        }
      })
      .catch((err) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : '굿즈 목록을 불러오지 못했습니다.')
          setLoading(false)
        }
      })
    return () => { cancelled = true }
  }, [storeId])

  async function handleDeleteConfirm() {
    if (!deletingGoods) return
    setIsDeleting(true)
    setDeleteError(null)
    try {
      await deleteHostGoods(String(deletingGoods.id))
      setGoodsList((prev) => prev.filter((g) => g.id !== deletingGoods.id))
      setDeletingGoods(null)
    } catch (err) {
      setDeleteError(err instanceof Error ? err.message : '삭제에 실패했습니다. 다시 시도해 주세요.')
    } finally {
      setIsDeleting(false)
    }
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
        <div className="flex-1 min-w-0">
          <h1 className="text-[15px] font-bold text-foreground leading-tight">굿즈 관리</h1>
          {storeName && (
            <p className="text-[11px] text-muted-foreground truncate mt-0.5">{storeName}</p>
          )}
        </div>
        <button
          onClick={onAdd}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-foreground text-background text-[12px] font-bold rounded-lg shrink-0"
        >
          <Plus size={13} strokeWidth={2.5} />
          굿즈 추가
        </button>
      </header>

      {/* List */}
      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <p className="text-[13px] text-muted-foreground">불러오는 중...</p>
          </div>
        ) : error ? (
          <div className="flex items-center justify-center h-full px-8 text-center">
            <p className="text-[13px] text-muted-foreground">{error}</p>
          </div>
        ) : goodsList.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-4 px-8 text-center">
            <div className="w-16 h-16 rounded-full bg-secondary flex items-center justify-center">
              <ShoppingBag size={28} strokeWidth={1.4} className="text-muted-foreground" />
            </div>
            <p className="text-[14px] font-semibold text-foreground">등록된 굿즈가 없습니다.</p>
            <p className="text-[12px] text-muted-foreground leading-relaxed">
              팝업스토어에서 판매할 굿즈를 추가해 보세요.
            </p>
            <button
              onClick={onAdd}
              className="mt-1 px-6 py-3 bg-foreground text-background text-[13px] font-bold rounded-xl flex items-center gap-2"
            >
              <Plus size={15} strokeWidth={2.5} />
              굿즈 추가
            </button>
          </div>
        ) : (
          <div className="space-y-3 px-4 pt-4">
            {goodsList.map((goods) => (
              <GoodsCard
                key={goods.id}
                goods={goods}
                onEdit={() => onEdit(String(goods.id))}
                onDelete={() => setDeletingGoods(goods)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Delete modal */}
      {deletingGoods && (
        <DeleteGoodsModal
          goods={deletingGoods}
          onCancel={() => { setDeletingGoods(null); setDeleteError(null) }}
          onConfirm={handleDeleteConfirm}
          isDeleting={isDeleting}
          error={deleteError}
        />
      )}
    </div>
  )
}
