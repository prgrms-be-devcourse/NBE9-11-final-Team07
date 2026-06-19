'use client'

import { useState } from 'react'
import { ArrowLeft, Plus, Pencil, Trash2, ShoppingBag } from 'lucide-react'
import { StatusBadge } from '@/components/ui/status-badge'
import { orgGoods } from '@/lib/data'
import type { OrgGoods } from '@/lib/data'

// ─── Delete Modal ─────────────────────────────────────────────────────────────

function DeleteGoodsModal({
  goods,
  onCancel,
  onConfirm,
}: {
  goods: OrgGoods
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl p-6 pb-8 space-y-4">
        <h3 className="text-base font-bold text-foreground">굿즈를 삭제하시겠습니까?</h3>
        <div className="flex items-center gap-3 bg-secondary rounded-xl p-3">
          <img
            src={goods.thumbnail}
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

// ─── Goods Card ───────────────────────────────────────────────────────────────

function GoodsCard({
  goods,
  onEdit,
  onDelete,
}: {
  goods: OrgGoods
  onEdit: () => void
  onDelete: () => void
}) {
  const statusLabel: Record<string, string> = {
    READY: '판매 준비',
    ON_SALE: '판매중',
    SOLD_OUT: '품절',
  }

  return (
    <div className="bg-card border border-border rounded-2xl overflow-hidden">
      <div className="flex items-start gap-3 p-3.5">
        <img
          src={goods.thumbnail}
          alt={goods.name}
          crossOrigin="anonymous"
          className="w-[72px] h-[72px] rounded-xl object-cover shrink-0"
        />
        <div className="flex-1 min-w-0 space-y-1">
          <div className="flex items-center gap-1.5">
            <StatusBadge status={goods.status} size="sm" />
            <span className="text-[10px] text-muted-foreground">{statusLabel[goods.status]}</span>
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
  storeName: string
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
  const [goodsList, setGoodsList] = useState<OrgGoods[]>(() =>
    orgGoods.filter((g) => g.storeId === storeId),
  )
  const [deletingGoods, setDeletingGoods] = useState<OrgGoods | null>(null)

  function handleDeleteConfirm() {
    if (deletingGoods) {
      setGoodsList((prev) => prev.filter((g) => g.id !== deletingGoods.id))
      setDeletingGoods(null)
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
          <p className="text-[10px] text-muted-foreground truncate">{storeName}</p>
          <h1 className="text-[15px] font-bold text-foreground leading-tight">굿즈 관리</h1>
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
        {goodsList.length === 0 ? (
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
                onEdit={() => onEdit(goods.id)}
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
          onCancel={() => setDeletingGoods(null)}
          onConfirm={handleDeleteConfirm}
        />
      )}
    </div>
  )
}
