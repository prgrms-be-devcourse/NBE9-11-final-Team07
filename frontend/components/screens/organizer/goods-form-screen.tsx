'use client'

import { useState } from 'react'
import { ArrowLeft, ImagePlus, X, Trash2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { orgGoods } from '@/lib/data'
import type { OrgGoods, GoodsSalesStatus } from '@/lib/data'

const SALES_STATUSES: { value: GoodsSalesStatus; label: string }[] = [
  { value: 'READY', label: '판매 준비' },
  { value: 'ON_SALE', label: '판매중' },
  { value: 'SOLD_OUT', label: '품절' },
]

// ─── Delete Confirm Modal ─────────────────────────────────────────────────────

function DeleteGoodsModal({
  goodsName,
  onCancel,
  onConfirm,
}: {
  goodsName: string
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl p-6 pb-8 space-y-4">
        <h3 className="text-base font-bold text-foreground">굿즈를 삭제하시겠습니까?</h3>
        <p className="text-[13px] font-semibold text-foreground bg-secondary rounded-xl px-4 py-3">
          {goodsName}
        </p>
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

// ─── Image Picker Placeholder ─────────────────────────────────────────────────

function ImagePickerSlot({
  src,
  label,
  onRemove,
}: {
  src?: string
  label: string
  onRemove?: () => void
}) {
  if (src) {
    return (
      <div className="relative w-[72px] h-[72px] shrink-0">
        <img
          src={src}
          alt={label}
          crossOrigin="anonymous"
          className="w-full h-full rounded-xl object-cover"
        />
        {onRemove && (
          <button
            onClick={onRemove}
            className="absolute -top-1.5 -right-1.5 w-5 h-5 bg-foreground rounded-full flex items-center justify-center"
          >
            <X size={10} strokeWidth={2.5} className="text-background" />
          </button>
        )}
      </div>
    )
  }
  return (
    <div className="w-[72px] h-[72px] shrink-0 rounded-xl border-2 border-dashed border-border flex flex-col items-center justify-center gap-1 bg-secondary">
      <ImagePlus size={18} strokeWidth={1.8} className="text-muted-foreground" />
      <span className="text-[9px] text-muted-foreground font-medium">추가</span>
    </div>
  )
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

interface GoodsFormScreenProps {
  mode: 'create' | 'edit'
  storeId: string
  storeName: string
  goodsId?: string
  onBack: () => void
  onSaved: () => void
  onDeleted?: () => void
}

export function GoodsFormScreen({
  mode,
  storeId,
  storeName,
  goodsId,
  onBack,
  onSaved,
  onDeleted,
}: GoodsFormScreenProps) {
  const existing = goodsId ? orgGoods.find((g) => g.id === goodsId) : undefined

  const [name, setName] = useState(existing?.name ?? '')
  const [price, setPrice] = useState(existing?.price ? String(existing.price) : '')
  const [stock, setStock] = useState(existing?.stock !== undefined ? String(existing.stock) : '')
  const [description, setDescription] = useState(existing?.description ?? '')
  const [status, setStatus] = useState<GoodsSalesStatus>(existing?.status ?? 'READY')
  const [thumbnail, setThumbnail] = useState<string | undefined>(existing?.thumbnail)
  const [detailImages, setDetailImages] = useState<string[]>(existing?.detailImages ?? [])
  const [showDeleteModal, setShowDeleteModal] = useState(false)

  const isEdit = mode === 'edit'
  const title = isEdit ? '굿즈 수정' : '굿즈 추가'

  const canSave =
    name.trim().length > 0 &&
    Number(price) > 0 &&
    Number(stock) >= 0

  function handleRemoveDetailImage(idx: number) {
    setDetailImages((prev) => prev.filter((_, i) => i !== idx))
  }

  const inputClass =
    'w-full px-3.5 py-3 bg-[oklch(0.96_0_0)] dark:bg-secondary border border-transparent rounded-xl text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-foreground/30 transition-colors'

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
          <h1 className="text-[15px] font-bold text-foreground leading-tight">{title}</h1>
        </div>
      </header>

      {/* Form */}
      <div className="flex-1 overflow-y-auto scrollbar-hide px-4 py-5 space-y-5">

        {/* Thumbnail */}
        <div className="space-y-2">
          <p className="text-[12px] font-bold text-foreground">대표 이미지</p>
          <div className="flex items-center gap-3">
            <ImagePickerSlot
              src={thumbnail}
              label="대표 이미지"
              onRemove={thumbnail ? () => setThumbnail(undefined) : undefined}
            />
            {!thumbnail && (
              <p className="text-[12px] text-muted-foreground">
                대표 이미지를 업로드하세요.
              </p>
            )}
          </div>
        </div>

        {/* Detail images */}
        <div className="space-y-2">
          <p className="text-[12px] font-bold text-foreground">상세 이미지</p>
          <div className="flex flex-wrap gap-2">
            {detailImages.map((src, idx) => (
              <ImagePickerSlot
                key={idx}
                src={src}
                label={`상세 이미지 ${idx + 1}`}
                onRemove={() => handleRemoveDetailImage(idx)}
              />
            ))}
            {/* Add slot */}
            <ImagePickerSlot label="상세 이미지 추가" />
          </div>
        </div>

        {/* Name */}
        <div className="space-y-1.5">
          <p className="text-[12px] font-bold text-foreground">굿즈 이름</p>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="예: 한정판 레트로 피규어 세트"
            className={inputClass}
          />
        </div>

        {/* Price */}
        <div className="space-y-1.5">
          <p className="text-[12px] font-bold text-foreground">가격 (원)</p>
          <input
            type="number"
            min={0}
            value={price}
            onChange={(e) => setPrice(e.target.value)}
            placeholder="예: 48000"
            className={inputClass}
          />
        </div>

        {/* Stock */}
        <div className="space-y-1.5">
          <p className="text-[12px] font-bold text-foreground">재고 수량</p>
          <input
            type="number"
            min={0}
            value={stock}
            onChange={(e) => setStock(e.target.value)}
            placeholder="예: 20"
            className={inputClass}
          />
        </div>

        {/* Description */}
        <div className="space-y-1.5">
          <p className="text-[12px] font-bold text-foreground">굿즈 설명</p>
          <textarea
            rows={3}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="굿즈에 대한 설명을 입력하세요."
            className={cn(inputClass, 'resize-none')}
          />
        </div>

        {/* Sales status */}
        <div className="space-y-1.5">
          <p className="text-[12px] font-bold text-foreground">판매 상태</p>
          <div className="flex gap-2">
            {SALES_STATUSES.map(({ value, label }) => (
              <button
                key={value}
                onClick={() => setStatus(value)}
                className={cn(
                  'flex-1 py-2.5 rounded-xl text-[12px] font-bold border-2 transition-colors',
                  status === value
                    ? 'bg-foreground text-background border-foreground'
                    : 'bg-card text-foreground border-border',
                )}
              >
                {label}
              </button>
            ))}
          </div>
        </div>

        {/* Edit-only: delete section */}
        {isEdit && (
          <div className="pt-2 border-t border-border">
            <button
              onClick={() => setShowDeleteModal(true)}
              className="w-full flex items-center justify-center gap-2 py-3 rounded-xl text-[13px] font-semibold text-[oklch(0.62_0.24_25)] bg-[oklch(0.97_0.06_25)] active:opacity-80 transition-opacity"
            >
              <Trash2 size={14} strokeWidth={2} />
              이 굿즈 삭제
            </button>
          </div>
        )}
      </div>

      {/* Bottom actions */}
      <div className="shrink-0 px-4 pb-6 pt-3 border-t border-border flex gap-2">
        <button
          onClick={onBack}
          className="flex-1 py-3.5 rounded-xl bg-secondary text-foreground font-semibold text-sm"
        >
          취소
        </button>
        <button
          disabled={!canSave}
          onClick={onSaved}
          className={cn(
            'flex-1 py-3.5 rounded-xl font-semibold text-sm transition-colors',
            canSave
              ? 'bg-foreground text-background'
              : 'bg-secondary text-muted-foreground cursor-not-allowed',
          )}
        >
          저장
        </button>
      </div>

      {/* Delete modal */}
      {showDeleteModal && (
        <DeleteGoodsModal
          goodsName={name || '이 굿즈'}
          onCancel={() => setShowDeleteModal(false)}
          onConfirm={() => {
            setShowDeleteModal(false)
            onDeleted?.()
          }}
        />
      )}
    </div>
  )
}
