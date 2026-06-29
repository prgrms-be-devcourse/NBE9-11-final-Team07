'use client'

import { useEffect, useState } from 'react'
import { ArrowLeft, Loader2, Plus, Trash2, Ticket } from 'lucide-react'
import { cn } from '@/lib/utils'
import { StatusBadge } from '@/components/ui/status-badge'
import type { OrgCoupon, DiscountType } from '@/lib/data'
import { couponApi } from '@/lib/coupon-api'
import type { CouponResponse } from '@/lib/coupon-api'

// ─── Create Coupon Modal ──────────────────────────────────────────────────────

function CreateCouponModal({
  onCancel,
  onSave,
}: {
  onCancel: () => void
  onSave: (coupon: Omit<OrgCoupon, 'id' | 'issuedQuantity' | 'status'>) => void
}) {
  const [name, setName] = useState('')
  const [discountType, setDiscountType] = useState<DiscountType>('PERCENT')
  const [discountValue, setDiscountValue] = useState('')
  const [maxDiscount, setMaxDiscount] = useState('')
  const [minOrder, setMinOrder] = useState('')
  const [totalQuantity, setTotalQuantity] = useState('')
  const [issuanceStart, setIssuanceStart] = useState('')
  const [expiresAt, setExpiresAt] = useState('')

  const canSave =
    name.trim() &&
    Number(discountValue) > 0 &&
    Number(totalQuantity) > 0 &&
    issuanceStart &&
    expiresAt

  function handleSave() {
    if (!canSave) return
    onSave({
      name: name.trim(),
      discountType,
      discountValue: Number(discountValue),
      maxDiscount: discountType === 'PERCENT' && maxDiscount ? Number(maxDiscount) : undefined,
      minOrder: discountType === 'AMOUNT' && minOrder ? Number(minOrder) : undefined,
      totalQuantity: Number(totalQuantity),
      issuanceStart,
      expiresAt,
    })
  }

  const inputClass =
    'w-full px-3.5 py-3 bg-[oklch(0.96_0_0)] dark:bg-secondary border border-transparent rounded-xl text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-foreground/30 transition-colors'

  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl flex flex-col max-h-[90%]">
        <div className="px-5 pt-5 pb-3 border-b border-border shrink-0">
          <h3 className="text-base font-bold text-foreground">쿠폰 만들기</h3>
        </div>

        <div className="flex-1 overflow-y-auto scrollbar-hide px-5 py-4 space-y-4">

          {/* Name */}
          <div className="space-y-1.5">
            <p className="text-[12px] font-bold text-foreground">쿠폰 이름</p>
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="예: 신규 방문객 10% 할인"
              className={inputClass}
            />
          </div>

          {/* Discount type toggle */}
          <div className="space-y-1.5">
            <p className="text-[12px] font-bold text-foreground">할인 유형</p>
            <div className="flex gap-2">
              {(['PERCENT', 'AMOUNT'] as DiscountType[]).map((type) => (
                <button
                  key={type}
                  onClick={() => setDiscountType(type)}
                  className={cn(
                    'flex-1 py-2.5 rounded-xl text-[13px] font-bold border-2 transition-colors',
                    discountType === type
                      ? 'bg-foreground text-background border-foreground'
                      : 'bg-card text-foreground border-border',
                  )}
                >
                  {type === 'PERCENT' ? '% 할인' : '금액 할인'}
                </button>
              ))}
            </div>
          </div>

          {/* Discount value */}
          <div className="space-y-1.5">
            <p className="text-[12px] font-bold text-foreground">
              할인 값 {discountType === 'PERCENT' ? '(%)' : '(원)'}
            </p>
            <input
              type="number"
              min={1}
              value={discountValue}
              onChange={(e) => setDiscountValue(e.target.value)}
              placeholder={discountType === 'PERCENT' ? '예: 10' : '예: 3000'}
              className={inputClass}
            />
          </div>

          {/* Conditional: max discount (PERCENT) or min order (AMOUNT) */}
          {discountType === 'PERCENT' && (
            <div className="space-y-1.5">
              <p className="text-[12px] font-bold text-foreground">최대 할인 금액 (원)</p>
              <input
                type="number"
                min={0}
                value={maxDiscount}
                onChange={(e) => setMaxDiscount(e.target.value)}
                placeholder="예: 5000"
                className={inputClass}
              />
            </div>
          )}
          {discountType === 'AMOUNT' && (
            <div className="space-y-1.5">
              <p className="text-[12px] font-bold text-foreground">최소 주문 금액 (원)</p>
              <input
                type="number"
                min={0}
                value={minOrder}
                onChange={(e) => setMinOrder(e.target.value)}
                placeholder="예: 20000"
                className={inputClass}
              />
            </div>
          )}

          {/* Total quantity */}
          <div className="space-y-1.5">
            <p className="text-[12px] font-bold text-foreground">총 발급 수량</p>
            <input
              type="number"
              min={1}
              value={totalQuantity}
              onChange={(e) => setTotalQuantity(e.target.value)}
              placeholder="예: 100"
              className={inputClass}
            />
          </div>

          {/* Issuance start */}
          <div className="space-y-1.5">
            <p className="text-[12px] font-bold text-foreground">발급 시작 시간</p>
            <input
              type="datetime-local"
              value={issuanceStart}
              onChange={(e) => setIssuanceStart(e.target.value)}
              className={inputClass}
            />
          </div>

          {/* Expires at */}
          <div className="space-y-1.5">
            <p className="text-[12px] font-bold text-foreground">쿠폰 만료일</p>
            <input
              type="date"
              value={expiresAt}
              onChange={(e) => setExpiresAt(e.target.value)}
              className={inputClass}
            />
          </div>
        </div>

        <div className="px-5 pb-6 pt-3 border-t border-border flex gap-2 shrink-0">
          <button
            onClick={onCancel}
            className="flex-1 py-3.5 rounded-xl bg-secondary text-foreground font-semibold text-sm"
          >
            취소
          </button>
          <button
            disabled={!canSave}
            onClick={handleSave}
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
      </div>
    </div>
  )
}

// ─── Delete Coupon Modal ──────────────────────────────────────────────────────

function DeleteCouponModal({
  couponName,
  onCancel,
  onConfirm,
}: {
  couponName: string
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl p-6 pb-8 space-y-4">
        <h3 className="text-base font-bold text-foreground">쿠폰을 삭제하시겠습니까?</h3>
        <p className="text-[13px] font-semibold text-foreground bg-secondary rounded-xl px-4 py-3">
          {couponName}
        </p>
        <p className="text-[13px] text-muted-foreground leading-relaxed">
          삭제된 쿠폰은 복구할 수 없습니다. 이미 발급된 쿠폰은 삭제할 수 없습니다.
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

// ─── Coupon Card ──────────────────────────────────────────────────────────────

function CouponCard({
  coupon,
  onDelete,
}: {
  coupon: OrgCoupon
  onDelete: () => void
}) {
  const discountLabel =
    coupon.discountType === 'PERCENT'
      ? `${coupon.discountValue}% 할인${coupon.maxDiscount ? ` (최대 ${coupon.maxDiscount.toLocaleString()}원)` : ''}`
      : `${coupon.discountValue.toLocaleString()}원 할인`

  const conditionLabel =
    coupon.discountType === 'PERCENT'
      ? coupon.maxDiscount
        ? `최대 ${coupon.maxDiscount.toLocaleString()}원 할인`
        : '제한 없음'
      : coupon.minOrder
      ? `${coupon.minOrder.toLocaleString()}원 이상 주문 시`
      : '제한 없음'

  const issuanceRate = Math.min((coupon.issuedQuantity / coupon.totalQuantity) * 100, 100)

  return (
    <div className="bg-card border border-border rounded-2xl overflow-hidden">
      <div className="p-3.5 space-y-2">
        <div className="flex items-start gap-3">
          <div
            className={cn(
              'w-10 h-10 rounded-xl flex items-center justify-center shrink-0',
              coupon.status === 'ACTIVE'
                ? 'bg-[oklch(0.94_0.04_145)]'
                : 'bg-secondary',
            )}
          >
            <Ticket
              size={18}
              strokeWidth={1.8}
              className={coupon.status === 'ACTIVE' ? 'text-[oklch(0.4_0.1_145)]' : 'text-muted-foreground'}
            />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <p className="text-[13px] font-bold text-foreground line-clamp-1">{coupon.name}</p>
              <StatusBadge status={coupon.status} size="sm" />
            </div>
            <p className="text-[12px] text-muted-foreground mt-0.5">{discountLabel}</p>
            <p className="text-[11px] text-muted-foreground mt-0.5">{conditionLabel}</p>
          </div>
          {coupon.status === 'ACTIVE' && (
            <button
              onClick={onDelete}
              className="p-1.5 rounded-lg bg-secondary hover:bg-border transition-colors shrink-0"
            >
              <Trash2 size={14} strokeWidth={2} className="text-[oklch(0.62_0.24_25)]" />
            </button>
          )}
        </div>

        {/* Issuance progress */}
        <div className="space-y-1">
          <div className="flex items-center justify-between">
            <span className="text-[10px] text-muted-foreground">발급 현황</span>
            <span className="text-[11px] font-bold text-foreground">
              {coupon.issuedQuantity} / {coupon.totalQuantity}
            </span>
          </div>
          <div className="h-1 w-full bg-secondary rounded-full overflow-hidden">
            <div
              className={cn(
                'h-full rounded-full transition-all',
                issuanceRate >= 100
                  ? 'bg-[oklch(0.5_0_0)]'
                  : issuanceRate >= 80
                  ? 'bg-[oklch(0.62_0.24_25)]'
                  : 'bg-foreground',
              )}
              style={{ width: `${issuanceRate}%` }}
            />
          </div>
        </div>

        <div className="flex items-center justify-between pt-0.5">
          <p className="text-[10px] text-muted-foreground">발급 시작: {coupon.issuanceStart}</p>
          <p className="text-[10px] text-muted-foreground">만료: {coupon.expiresAt}</p>
        </div>
      </div>
    </div>
  )
}

// ─── Main Screen ──────────────────────────────────────────────────────────────

interface CouponListScreenProps {
  onBack: () => void
  storeId: string
  storeName: string
}

export function CouponListScreen({ onBack, storeId, storeName }: CouponListScreenProps) {
  const apiStoreId = storeId.replace(/^op/, '')
  const [coupons, setCoupons] = useState<OrgCoupon[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [showCreate, setShowCreate] = useState(false)
  const [deletingCoupon, setDeletingCoupon] = useState<OrgCoupon | null>(null)

  function toOrgCoupon(coupon: CouponResponse): OrgCoupon {
    return {
      id: String(coupon.id),
      name: coupon.name,
      discountType: coupon.discountType,
      discountValue: coupon.discountValue,
      maxDiscount: coupon.maxDiscountAmount ?? undefined,
      minOrder: coupon.minOrderAmount ?? undefined,
      totalQuantity: coupon.totalQuantity,
      issuedQuantity: coupon.issuedQuantity,
      issuanceStart: coupon.startedAt.replace('T', ' ').slice(0, 16),
      expiresAt: coupon.expiredAt.slice(0, 10),
      status: coupon.status,
    }
  }

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    couponApi.getHostCoupons(apiStoreId)
      .then((response) => {
        if (!cancelled) setCoupons(response.map(toOrgCoupon))
      })
      .catch((loadError: Error) => {
        if (!cancelled) setError(loadError.message)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [apiStoreId])

  async function handleCreate(data: Omit<OrgCoupon, 'id' | 'issuedQuantity' | 'status'>) {
    setError(null)
    try {
      const created = await couponApi.createHostCoupon(apiStoreId, {
        name: data.name,
        discountType: data.discountType,
        discountValue: data.discountValue,
        maxDiscountAmount: data.maxDiscount ?? null,
        minOrderAmount: data.minOrder ?? null,
        totalQuantity: data.totalQuantity,
        startedAt: data.issuanceStart,
        expiredAt: `${data.expiresAt}T23:59:59`,
      })
      setCoupons((prev) => [toOrgCoupon(created), ...prev])
      setShowCreate(false)
    } catch (createError) {
      setError(createError instanceof Error ? createError.message : '쿠폰 생성에 실패했습니다.')
    }
  }

  async function handleDeleteConfirm() {
    if (deletingCoupon) {
      setError(null)
      try {
        await couponApi.deleteHostCoupon(apiStoreId, Number(deletingCoupon.id))
        setCoupons((prev) => prev.filter((c) => c.id !== deletingCoupon.id))
        setDeletingCoupon(null)
      } catch (deleteError) {
        setError(deleteError instanceof Error ? deleteError.message : '쿠폰 삭제에 실패했습니다.')
      }
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
          <h1 className="text-[15px] font-bold text-foreground leading-tight">쿠폰 관리</h1>
        </div>
        <button
          onClick={() => setShowCreate(true)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-foreground text-background text-[12px] font-bold rounded-lg shrink-0"
        >
          <Plus size={13} strokeWidth={2.5} />
          쿠폰 만들기
        </button>
      </header>

      {/* List */}
      <div className="flex-1 overflow-y-auto scrollbar-hide pb-6">
        {error && (
          <p className="mx-4 mt-3 text-xs text-center text-[oklch(0.62_0.24_25)]">{error}</p>
        )}
        {loading ? (
          <div className="flex items-center justify-center h-full">
            <Loader2 size={24} className="animate-spin text-muted-foreground" />
          </div>
        ) : coupons.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full gap-4 px-8 text-center">
            <div className="w-16 h-16 rounded-full bg-secondary flex items-center justify-center">
              <Ticket size={28} strokeWidth={1.4} className="text-muted-foreground" />
            </div>
            <p className="text-[14px] font-semibold text-foreground">등록된 쿠폰이 없습니다.</p>
            <p className="text-[12px] text-muted-foreground leading-relaxed">
              쿠폰을 만들어 방문객에게 혜택을 제공하세요.
            </p>
            <button
              onClick={() => setShowCreate(true)}
              className="mt-1 px-6 py-3 bg-foreground text-background text-[13px] font-bold rounded-xl flex items-center gap-2"
            >
              <Plus size={15} strokeWidth={2.5} />
              쿠폰 만들기
            </button>
          </div>
        ) : (
          <div className="space-y-3 px-4 pt-4">
            {coupons.map((coupon) => (
              <CouponCard
                key={coupon.id}
                coupon={coupon}
                onDelete={() => setDeletingCoupon(coupon)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Modals */}
      {showCreate && (
        <CreateCouponModal
          onCancel={() => setShowCreate(false)}
          onSave={handleCreate}
        />
      )}
      {deletingCoupon && (
        <DeleteCouponModal
          couponName={deletingCoupon.name}
          onCancel={() => setDeletingCoupon(null)}
          onConfirm={handleDeleteConfirm}
        />
      )}
    </div>
  )
}
