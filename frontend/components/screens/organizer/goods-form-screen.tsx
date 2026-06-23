'use client'

import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ImagePlus, Loader2, X, Trash2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { uploadGoodsImage, registerGoods, getHostGoodsDetail, updateHostGoods, deleteHostGoods } from '@/lib/goods-api'
import type { ImageKeyEntry } from '@/lib/goods-api'

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

// ─── Image Picker Slot ────────────────────────────────────────────────────────

function ImagePickerSlot({
  src,
  label,
  onRemove,
  onClick,
  uploading = false,
}: {
  src?: string
  label: string
  onRemove?: () => void
  onClick?: () => void
  uploading?: boolean
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

  const inner = uploading ? (
    <Loader2 size={18} strokeWidth={1.8} className="text-muted-foreground animate-spin" />
  ) : (
    <>
      <ImagePlus size={18} strokeWidth={1.8} className="text-muted-foreground" />
      <span className="text-[9px] text-muted-foreground font-medium">추가</span>
    </>
  )

  const baseClass =
    'w-[72px] h-[72px] shrink-0 rounded-xl border-2 border-dashed border-border flex flex-col items-center justify-center gap-1 bg-secondary'

  if (onClick) {
    return (
      <button
        type="button"
        onClick={onClick}
        disabled={uploading}
        className={cn(baseClass, 'disabled:opacity-60')}
      >
        {inner}
      </button>
    )
  }
  return <div className={baseClass}>{inner}</div>
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
  const [loading, setLoading] = useState(mode === 'edit')
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [stock, setStock] = useState('')
  const [description, setDescription] = useState('')

  const [productPreview, setProductPreview] = useState<string | undefined>()
  const [productImageKey, setProductImageKey] = useState<string | null>(null)
  const [uploadingProduct, setUploadingProduct] = useState(false)
  const [detailPreview, setDetailPreview] = useState<string | undefined>()
  const [detailImageKey, setDetailImageKey] = useState<string | null>(null)
  const [uploadingDetail, setUploadingDetail] = useState(false)

  const [saving, setSaving] = useState(false)
  const [showDeleteModal, setShowDeleteModal] = useState(false)

  useEffect(() => {
    if (mode !== 'edit' || !goodsId) return
    getHostGoodsDetail(goodsId)
      .then((data) => {
        setName(data.name)
        setPrice(String(data.price))
        setStock(String(data.stock))
        setDescription(data.description ?? '')
        setProductPreview(data.productImageUrl ?? undefined)
        setDetailPreview(data.detailImageUrl ?? undefined)
      })
      .catch((e) => alert(e instanceof Error ? e.message : '굿즈 정보를 불러오지 못했습니다.'))
      .finally(() => setLoading(false))
  }, [mode, goodsId])

  const productFileInputRef = useRef<HTMLInputElement>(null)
  const detailFileInputRef = useRef<HTMLInputElement>(null)

  const isEdit = mode === 'edit'
  const title = isEdit ? '굿즈 수정' : '굿즈 추가'

  const hasImageChange = !!productImageKey || !!detailImageKey
  const canSave =
    name.trim().length > 0 &&
    Number(price) > 0 &&
    Number(stock) >= 0 &&
    (!isEdit ? (!!productImageKey && !!detailImageKey) : true) &&
    !uploadingProduct &&
    !uploadingDetail

  async function handleProductImageSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || uploadingProduct) return
    setProductPreview(URL.createObjectURL(file))
    setUploadingProduct(true)
    try {
      const key = await uploadGoodsImage('PRODUCT', file)
      setProductImageKey(key)
    } catch (err) {
      alert(err instanceof Error ? err.message : '이미지 업로드에 실패했습니다.')
      setProductPreview(undefined)
    } finally {
      setUploadingProduct(false)
    }
  }

  async function handleDetailImageSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = ''
    if (!file || uploadingDetail) return
    setDetailPreview(URL.createObjectURL(file))
    setUploadingDetail(true)
    try {
      const key = await uploadGoodsImage('DETAIL', file)
      setDetailImageKey(key)
    } catch (err) {
      alert(err instanceof Error ? err.message : '이미지 업로드에 실패했습니다.')
      setDetailPreview(undefined)
    } finally {
      setUploadingDetail(false)
    }
  }

  async function handleSave() {
    if (!canSave || saving) return

    if (mode === 'create') {
      if (!productImageKey || !detailImageKey) {
        alert('대표 이미지와 상세 이미지를 모두 등록해주세요.')
        return
      }
      setSaving(true)
      try {
        const imageKeys: ImageKeyEntry[] = [
          { imageKey: productImageKey, imageType: 'PRODUCT' },
          { imageKey: detailImageKey, imageType: 'DETAIL' },
        ]
        await registerGoods(storeId, {
          name: name.trim(),
          price: Number(price),
          stock: Number(stock),
          description: description.trim() || undefined,
          imageKeys,
        })
        onSaved()
      } catch (e) {
        alert(e instanceof Error ? e.message : '저장에 실패했습니다.')
      } finally {
        setSaving(false)
      }
    } else {
      if (hasImageChange && !(productImageKey && detailImageKey)) {
        alert('이미지를 변경하려면 대표 이미지와 상세 이미지를 모두 업로드해야 합니다.')
        return
      }
      setSaving(true)
      try {
        const imageKeys: ImageKeyEntry[] | undefined =
          productImageKey && detailImageKey
            ? [
                { imageKey: productImageKey, imageType: 'PRODUCT' },
                { imageKey: detailImageKey, imageType: 'DETAIL' },
              ]
            : undefined
        await updateHostGoods(goodsId!, {
          name: name.trim(),
          price: Number(price),
          stock: Number(stock),
          description: description.trim() || null,
          imageKeys,
        })
        onSaved()
      } catch (e) {
        alert(e instanceof Error ? e.message : '저장에 실패했습니다.')
      } finally {
        setSaving(false)
      }
    }
  }

  const inputClass =
    'w-full px-3.5 py-3 bg-[oklch(0.96_0_0)] dark:bg-secondary border border-transparent rounded-xl text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-foreground/30 transition-colors'

  if (loading) {
    return (
      <div className="flex flex-col h-full overflow-hidden">
        <header className="flex items-center gap-3 px-4 py-3 bg-card border-b border-border shrink-0">
          <button onClick={onBack} aria-label="뒤로 가기" className="flex items-center justify-center w-8 h-8 -ml-1 rounded-full hover:bg-secondary transition-colors">
            <ArrowLeft size={20} strokeWidth={2} />
          </button>
          <div className="flex-1 min-w-0">
            <p className="text-[10px] text-muted-foreground truncate">{storeName}</p>
            <h1 className="text-[15px] font-bold text-foreground leading-tight">{title}</h1>
          </div>
        </header>
        <div className="flex-1 flex items-center justify-center">
          <Loader2 size={28} className="animate-spin text-muted-foreground" />
        </div>
      </div>
    )
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
          <h1 className="text-[15px] font-bold text-foreground leading-tight">{title}</h1>
        </div>
      </header>

      {/* Form */}
      <div className="flex-1 overflow-y-auto scrollbar-hide px-4 py-5 space-y-5">

        {/* 대표 이미지 (PRODUCT) */}
        <div className="space-y-2">
          <p className="text-[12px] font-bold text-foreground">대표 이미지</p>
          <div className="flex items-center gap-3">
            <input
              ref={productFileInputRef}
              type="file"
              accept="image/*"
              onChange={handleProductImageSelect}
              className="hidden"
            />
            <ImagePickerSlot
              src={productPreview}
              label="대표 이미지"
              onRemove={
                productPreview
                  ? () => { setProductPreview(undefined); setProductImageKey(null) }
                  : undefined
              }
              onClick={
                !productPreview
                  ? () => productFileInputRef.current?.click()
                  : undefined
              }
              uploading={uploadingProduct}
            />
            {!productPreview && !uploadingProduct && (
              <p className="text-[12px] text-muted-foreground">대표 이미지를 업로드하세요.</p>
            )}
          </div>
        </div>

        {/* 상세 이미지 (DETAIL) */}
        <div className="space-y-2">
          <p className="text-[12px] font-bold text-foreground">상세 이미지</p>
          <div className="flex flex-wrap gap-2">
            <input
              ref={detailFileInputRef}
              type="file"
              accept="image/*"
              onChange={handleDetailImageSelect}
              className="hidden"
            />
            {detailPreview ? (
              <ImagePickerSlot
                src={detailPreview}
                label="상세 이미지"
                onRemove={() => { setDetailPreview(undefined); setDetailImageKey(null) }}
              />
            ) : (
              <ImagePickerSlot
                label="상세 이미지 추가"
                onClick={() => detailFileInputRef.current?.click()}
                uploading={uploadingDetail}
              />
            )}
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
          disabled={!canSave || saving || uploadingProduct || uploadingDetail}
          onClick={handleSave}
          className={cn(
            'flex-1 py-3.5 rounded-xl font-semibold text-sm transition-colors',
            canSave && !saving && !uploadingProduct && !uploadingDetail
              ? 'bg-foreground text-background'
              : 'bg-secondary text-muted-foreground cursor-not-allowed',
          )}
        >
          {saving ? '저장 중...' : '저장'}
        </button>
      </div>

      {/* Delete modal */}
      {showDeleteModal && (
        <DeleteGoodsModal
          goodsName={name || '이 굿즈'}
          onCancel={() => setShowDeleteModal(false)}
          onConfirm={async () => {
            setShowDeleteModal(false)
            try {
              await deleteHostGoods(goodsId!)
              onDeleted?.()
            } catch (e) {
              alert(e instanceof Error ? e.message : '삭제에 실패했습니다.')
            }
          }}
        />
      )}
    </div>
  )
}
