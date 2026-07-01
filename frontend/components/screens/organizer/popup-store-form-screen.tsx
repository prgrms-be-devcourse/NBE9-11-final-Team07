'use client'

import { useEffect, useRef, useState } from 'react'
import { ArrowLeft, ImagePlus, Plus, Trash2, X, CalendarDays, Clock, Loader2 } from 'lucide-react'
import { cn } from '@/lib/utils'
import { getOperatingDates, formatDateKorean } from '@/lib/data'
import type { OrgReservationSlot } from '@/lib/data'
import { getPopupDetail, getPopupSlots, uploadPopupImage, createPopup, updatePopup, deletePopup, createSlot, deleteSlot } from '@/lib/popup-api'
import type { ReservationSlotResponse } from '@/lib/popup-api'

// ─── Add Slot Modal ──────────────────────────────────────────────────────────

const TIME_OPTIONS = ['10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00']

function AddSlotModal({
  operationStart,
  operationEnd,
  onCancel,
  onConfirm,
}: {
  operationStart: string
  operationEnd: string
  onCancel: () => void
  onConfirm: (slots: Omit<OrgReservationSlot, 'id'>[]) => void
}) {
  const allDates = operationStart && operationEnd ? getOperatingDates(operationStart, operationEnd) : []
  const [selectedDates, setSelectedDates] = useState<string[]>([])
  const [selectedTimes, setSelectedTimes] = useState<string[]>([])
  const [capacity, setCapacity] = useState('')

  function toggleDate(d: string) {
    setSelectedDates((prev) => prev.includes(d) ? prev.filter((x) => x !== d) : [...prev, d])
  }
  function toggleTime(t: string) {
    setSelectedTimes((prev) => prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t])
  }

  const canConfirm = selectedDates.length > 0 && selectedTimes.length > 0 && Number(capacity) > 0

  function handleConfirm() {
    const slots: Omit<OrgReservationSlot, 'id'>[] = []
    for (const date of selectedDates) {
      for (const time of selectedTimes) {
        slots.push({ date, time, capacity: Number(capacity) })
      }
    }
    onConfirm(slots)
  }

  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl flex flex-col max-h-[85%]">
        <div className="px-5 pt-5 pb-3 border-b border-border shrink-0">
          <h3 className="text-base font-bold text-foreground">예약 슬롯 추가</h3>
        </div>

        <div className="flex-1 overflow-y-auto scrollbar-hide px-5 py-4 space-y-5">
          {/* Dates */}
          <div>
            <p className="text-[12px] font-bold text-foreground mb-2">운영 날짜 선택</p>
            {allDates.length === 0 ? (
              <p className="text-[12px] text-muted-foreground">운영 기간을 먼저 설정해주세요.</p>
            ) : (
              <div className="flex flex-wrap gap-2">
                {allDates.map((d) => {
                  const selected = selectedDates.includes(d)
                  const dObj = new Date(d)
                  const days = ['일', '월', '화', '수', '목', '금', '토']
                  const label = `${dObj.getMonth() + 1}/${dObj.getDate()} (${days[dObj.getDay()]})`
                  return (
                    <button
                      key={d}
                      onClick={() => toggleDate(d)}
                      className={cn(
                        'px-3 py-1.5 rounded-lg text-[12px] font-semibold border transition-colors',
                        selected
                          ? 'bg-foreground text-background border-foreground'
                          : 'bg-card text-foreground border-border',
                      )}
                    >
                      {label}
                    </button>
                  )
                })}
              </div>
            )}
          </div>

          {/* Times */}
          <div>
            <p className="text-[12px] font-bold text-foreground mb-2">운영 시간 선택</p>
            <div className="flex flex-wrap gap-2">
              {TIME_OPTIONS.map((t) => {
                const selected = selectedTimes.includes(t)
                return (
                  <button
                    key={t}
                    onClick={() => toggleTime(t)}
                    className={cn(
                      'px-3 py-1.5 rounded-lg text-[12px] font-semibold border transition-colors',
                      selected
                        ? 'bg-foreground text-background border-foreground'
                        : 'bg-card text-foreground border-border',
                    )}
                  >
                    {t}
                  </button>
                )
              })}
            </div>
          </div>

          {/* Capacity */}
          <div>
            <p className="text-[12px] font-bold text-foreground mb-2">슬롯당 정원</p>
            <input
              type="number"
              min={1}
              value={capacity}
              onChange={(e) => setCapacity(e.target.value)}
              placeholder="예: 20"
              className="w-full px-3.5 py-3 bg-secondary border border-transparent rounded-xl text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-foreground/30 transition-colors"
            />
          </div>

          {/* Helper text */}
          {selectedDates.length > 0 && selectedTimes.length > 0 && (
            <p className="text-[11px] text-muted-foreground bg-secondary rounded-xl px-3.5 py-2.5 leading-relaxed">
              선택된 날짜({selectedDates.length}개) × 시간({selectedTimes.length}개)의 조합으로{' '}
              <strong className="text-foreground">{selectedDates.length * selectedTimes.length}개</strong>의 슬롯이 생성됩니다.
            </p>
          )}
        </div>

        <div className="px-5 pb-6 pt-3 border-t border-border flex gap-2 shrink-0">
          <button
            onClick={onCancel}
            className="flex-1 py-3.5 rounded-xl bg-secondary text-foreground font-semibold text-sm"
          >
            취소
          </button>
          <button
            disabled={!canConfirm}
            onClick={handleConfirm}
            className={cn(
              'flex-1 py-3.5 rounded-xl font-semibold text-sm transition-colors',
              canConfirm
                ? 'bg-foreground text-background'
                : 'bg-secondary text-muted-foreground cursor-not-allowed',
            )}
          >
            슬롯 생성
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Delete All Slots Modal ───────────────────────────────────────────────────

function DeleteAllSlotsModal({ onCancel, onConfirm }: { onCancel: () => void; onConfirm: () => void }) {
  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl p-6 pb-8 space-y-4">
        <h3 className="text-base font-bold text-foreground">예약 슬롯을 전체 삭제하시겠습니까?</h3>
        <p className="text-[13px] text-muted-foreground leading-relaxed">
          삭제된 슬롯은 복구할 수 없습니다. 기존에 접수된 예약도 함께 취소됩니다.
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
            전체 삭제
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Delete Store Modal ───────────────────────────────────────────────────────

function DeleteStoreModal({
  storeName,
  onCancel,
  onConfirm,
}: {
  storeName: string
  onCancel: () => void
  onConfirm: () => void
}) {
  return (
    <div className="absolute inset-0 z-50 flex items-end justify-center bg-black/50 backdrop-blur-sm">
      <div className="w-full bg-card rounded-t-3xl p-6 pb-8 space-y-4">
        <h3 className="text-base font-bold text-foreground">팝업스토어를 삭제하시겠습니까?</h3>
        <p className="text-[13px] font-semibold text-foreground bg-secondary rounded-xl px-4 py-3">
          {storeName}
        </p>
        <p className="text-[13px] text-muted-foreground leading-relaxed">
          삭제하면 모든 예약 슬롯과 데이터가 영구적으로 제거됩니다. 이 작업은 되돌릴 수 없습니다.
        </p>
        <div className="flex gap-2 pt-1">
          <button onClick={onCancel} className="flex-1 py-3.5 rounded-xl bg-secondary text-foreground font-semibold text-sm">
            취소
          </button>
          <button onClick={onConfirm} className="flex-1 py-3.5 rounded-xl bg-[oklch(0.62_0.24_25)] text-white font-semibold text-sm">
            삭제
          </button>
        </div>
      </div>
    </div>
  )
}

// ─── Field component ──────────────────────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1.5">
      <p className="text-[12px] font-bold text-foreground">{label}</p>
      {children}
    </div>
  )
}

function TextInput({
  value,
  onChange,
  placeholder,
  type = 'text',
}: {
  value: string
  onChange: (v: string) => void
  placeholder?: string
  type?: string
}) {
  return (
    <input
      type={type}
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      className="w-full px-3.5 py-3 bg-secondary border border-transparent rounded-xl text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-foreground/30 transition-colors"
    />
  )
}

function TextArea({
  value,
  onChange,
  placeholder,
  rows = 4,
}: {
  value: string
  onChange: (v: string) => void
  placeholder?: string
  rows?: number
}) {
  return (
    <textarea
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      rows={rows}
      className="w-full px-3.5 py-3 bg-secondary border border-transparent rounded-xl text-sm text-foreground placeholder:text-muted-foreground focus:outline-none focus:border-foreground/30 transition-colors resize-none leading-relaxed"
    />
  )
}

// ─── Main Form ────────────────────────────────────────────────────────────────

interface PopupStoreFormScreenProps {
  mode: 'create' | 'edit'
  storeId?: string
  onBack: () => void
  onSaved: () => void
  onDeleted?: () => void
}

export function PopupStoreFormScreen({
  mode,
  storeId,
  onBack,
  onSaved,
  onDeleted,
}: PopupStoreFormScreenProps) {
  const [name, setName] = useState('')
  const [location, setLocation] = useState('')
  const [feeType, setFeeType] = useState<'FREE' | 'PAID'>('FREE')
  const [price, setPrice] = useState('')
  const [opStart, setOpStart] = useState('')
  const [opEnd, setOpEnd] = useState('')
  const [regStart, setRegStart] = useState('')
  const [regEnd, setRegEnd] = useState('')
  const [description, setDescription] = useState('')
  const [slots, setSlots] = useState<OrgReservationSlot[]>([])
  const [originalSlots, setOriginalSlots] = useState<OrgReservationSlot[]>([])
  const [image, setImage] = useState<string | undefined>(undefined) // 미리보기용 (기존 presigned URL 또는 선택 파일)
  const [imageKey, setImageKey] = useState<string | null>(null)      // 업로드된 tempKey (없으면 변경 안 함)
  const [uploading, setUploading] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // 수정 모드: 상세(getPopupDetail) + 날짜별 슬롯(getPopupSlots)을 조회해 폼을 채운다.
  useEffect(() => {
    if (mode !== 'edit' || !storeId) return
    let cancelled = false
    ;(async () => {
      try {
        const detailRes = await getPopupDetail(storeId)
        if (cancelled) return
        const d = detailRes
        const start = d.openDate ? d.openDate.slice(0, 10) : ''
        const end = d.closeDate ? d.closeDate.slice(0, 10) : ''

        setName(d.title)
        setLocation(d.location)
        setFeeType(d.feeType)
        setPrice(d.price != null ? String(d.price) : '')
        setDescription(d.description ?? '')
        setOpStart(start)
        setOpEnd(end)
        setRegStart(d.reservationStartAt ? d.reservationStartAt.slice(0, 10) : '')
        setRegEnd(d.reservationEndAt ? d.reservationEndAt.slice(0, 10) : '')
        setImage(d.imageUrl ?? undefined)

        // 슬롯은 날짜별 조회이므로 운영 기간의 각 날짜에 대해 호출 후 합친다.
        const dates = start && end ? getOperatingDates(start, end) : []
        const slotLists = await Promise.all(
          dates.map((date) =>
            getPopupSlots(storeId, date)
              .catch(() => [] as ReservationSlotResponse[]),
          ),
        )
        if (cancelled) return
        const loadedSlots: OrgReservationSlot[] = slotLists.flat().map((s) => ({
          id: String(s.slotId),
          date: s.slotDate,
          time: s.startTime ? s.startTime.slice(0, 5) : s.startTime,
          capacity: s.capacity,
        }))
        setSlots(loadedSlots)
        setOriginalSlots(loadedSlots)
      } catch {
        // 조회 실패 시 빈 폼으로 진행
      }
    })()
    return () => {
      cancelled = true
    }
  }, [mode, storeId])

  const [showAddSlot, setShowAddSlot] = useState(false)
  const [showDeleteAll, setShowDeleteAll] = useState(false)
  const [showDeleteStore, setShowDeleteStore] = useState(false)
  const [saving, setSaving] = useState(false)

  let slotIdCounter = slots.length + 1

  // 'YYYY-MM-DD' + 'HH:mm:ss' → 백엔드 LocalDateTime ISO 문자열
  function toDateTime(date: string, time: string): string {
    return `${date}T${time}`
  }

  // 'HH:mm' → 'HH:mm:ss' (백엔드 LocalTime)
  function toLocalTime(time: string): string {
    return time.length === 5 ? `${time}:00` : time
  }

  async function createSlotsFor(targetStoreId: string, targetSlots: OrgReservationSlot[]) {
    for (const slot of targetSlots) {
      await createSlot(targetStoreId, {
        slotDate: slot.date,
        startTime: toLocalTime(slot.time),
        capacity: slot.capacity,
      })
    }
  }

  async function deleteSlotsFor(targetStoreId: string, targetSlots: OrgReservationSlot[]) {
    for (const slot of targetSlots) {
      await deleteSlot(targetStoreId, slot.id)
    }
  }

  // 파일 선택 → S3 presigned PUT 업로드 → imageKey(tempKey) 저장 + 미리보기
  async function handleImageSelect(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    e.target.value = '' // 같은 파일 재선택 허용
    if (!file) return
    if (uploading) return

    const previewUrl = URL.createObjectURL(file)
    setImage(previewUrl)
    setUploading(true)
    try {
      const tempKey = await uploadPopupImage(file)
      setImageKey(tempKey)
    } catch (err) {
      alert(err instanceof Error ? err.message : '이미지 업로드에 실패했습니다.')
      setImage(undefined)
    } finally {
      setUploading(false)
    }
  }

  async function handleSave() {
    if (!canSave || saving) return
    if (mode === 'create' && !imageKey) {
      alert('포스터 이미지를 등록해주세요.')
      return
    }
    setSaving(true)
    try {
      if (mode === 'create') {
        const res = await createPopup({
          title: name,
          location,
          feeType,
          price: feeType === 'PAID' ? Number(price) : null,
          reservationStartAt: toDateTime(regStart || opStart, '00:00:00'),
          reservationEndAt: toDateTime(regEnd || opEnd, '23:59:59'),
          openDate: toDateTime(opStart, '00:00:00'),
          closeDate: toDateTime(opEnd, '23:59:59'),
          imageKey, // 업로드된 tempKey
          description,
        })
        await createSlotsFor(String(res), slots)
      } else if (storeId) {
        await updatePopup(storeId, {
          title: name,
          location,
          feeType,
          price: feeType === 'PAID' ? Number(price) : null,
          reservationStartAt: toDateTime(regStart || opStart, '00:00:00'),
          reservationEndAt: toDateTime(regEnd || opEnd, '23:59:59'),
          openDate: toDateTime(opStart, '00:00:00'),
          closeDate: toDateTime(opEnd, '23:59:59'),
          description,
          // 새 이미지를 골랐을 때만 imageKey 전송 (없으면 기존 이미지 유지)
          ...(imageKey ? { imageKey } : {}),
        })
        const deletedSlots = originalSlots.filter(
          (original) => !slots.some((slot) => slot.id === original.id),
        )
        // 삭제 후 같은 일시로 재생성하는 경우 중복 슬롯 검증을 피하기 위해 삭제를 먼저 처리한다.
        await deleteSlotsFor(storeId, deletedSlots)
        // 신규 추가된 슬롯(id가 'new-'로 시작)만 생성
        await createSlotsFor(storeId, slots.filter((s) => s.id.startsWith('new-')))
      }
      onSaved()
    } catch (e) {
      alert(e instanceof Error ? e.message : '저장에 실패했습니다.')
    } finally {
      setSaving(false)
    }
  }

  async function handleDeleteStore() {
    if (!storeId) {
      setShowDeleteStore(false)
      onDeleted?.()
      return
    }
    try {
      await deletePopup(storeId)
      setShowDeleteStore(false)
      onDeleted?.()
    } catch (e) {
      alert(e instanceof Error ? e.message : '삭제에 실패했습니다.')
      setShowDeleteStore(false)
    }
  }

  function handleAddSlots(newSlots: Omit<OrgReservationSlot, 'id'>[]) {
    const created: OrgReservationSlot[] = newSlots.map((s) => ({
      ...s,
      id: `new-${slotIdCounter++}`,
    }))
    setSlots((prev) => [...prev, ...created])
    setShowAddSlot(false)
  }

  function handleDeleteSlot(id: string) {
    setSlots((prev) => prev.filter((s) => s.id !== id))
  }

  function handleDeleteAll() {
    setSlots([])
    setShowDeleteAll(false)
  }

  const sortedSlots = [...slots].sort((a, b) => {
    if (a.date !== b.date) return a.date.localeCompare(b.date)
    return a.time.localeCompare(b.time)
  })

  const canSave = name.trim() && location.trim() && opStart && opEnd
    && (feeType === 'FREE' || Number(price) > 0)

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
        <h1 className="text-base font-bold text-foreground flex-1">
          {mode === 'create' ? '팝업스토어 만들기' : '팝업스토어 수정'}
        </h1>
      </header>

      {/* Scrollable body */}
      <div className="flex-1 overflow-y-auto scrollbar-hide pb-32">
        <div className="px-4 py-5 space-y-5">

          {/* Poster image upload */}
          <Field label="포스터 이미지">
            <input
              ref={fileInputRef}
              type="file"
              accept="image/*"
              onChange={handleImageSelect}
              className="hidden"
            />
            <button
              type="button"
              onClick={() => fileInputRef.current?.click()}
              disabled={uploading}
              className="w-full h-36 border-2 border-dashed border-border rounded-2xl flex flex-col items-center justify-center gap-2 bg-secondary/60 active:opacity-70 transition-opacity overflow-hidden relative disabled:opacity-60"
            >
              {image ? (
                <>
                  <img
                    src={image}
                    alt="포스터"
                    crossOrigin="anonymous"
                    className="absolute inset-0 w-full h-full object-cover opacity-40"
                  />
                  <div className="relative z-10 flex flex-col items-center gap-1">
                    {uploading ? (
                      <Loader2 size={22} strokeWidth={1.8} className="text-foreground animate-spin" />
                    ) : (
                      <ImagePlus size={22} strokeWidth={1.6} className="text-foreground" />
                    )}
                    <span className="text-[11px] font-semibold text-foreground">
                      {uploading ? '업로드 중...' : '이미지 변경'}
                    </span>
                  </div>
                </>
              ) : uploading ? (
                <>
                  <Loader2 size={22} strokeWidth={1.8} className="text-muted-foreground animate-spin" />
                  <span className="text-[11px] text-muted-foreground">업로드 중...</span>
                </>
              ) : (
                <>
                  <ImagePlus size={22} strokeWidth={1.6} className="text-muted-foreground" />
                  <span className="text-[11px] text-muted-foreground">이미지 업로드</span>
                  <span className="text-[10px] text-muted-foreground/70">JPG, PNG (최대 10MB)</span>
                </>
              )}
            </button>
          </Field>

          {/* Basic info */}
          <Field label="팝업스토어 이름">
            <TextInput value={name} onChange={setName} placeholder="예: 성수 빈티지 토이 팝업" />
          </Field>

          <Field label="위치">
            <TextInput value={location} onChange={setLocation} placeholder="예: 서울 성동구 성수동" />
          </Field>

          {/* 요금 유형 */}
          <Field label="요금 유형">
            <div className="flex gap-2">
              <button
                type="button"
                onClick={() => setFeeType('FREE')}
                className={cn(
                  'flex-1 py-3 rounded-xl text-sm font-semibold border transition-colors',
                  feeType === 'FREE'
                    ? 'bg-foreground text-background border-foreground'
                    : 'bg-card text-foreground border-border',
                )}
              >
                무료
              </button>
              <button
                type="button"
                onClick={() => setFeeType('PAID')}
                className={cn(
                  'flex-1 py-3 rounded-xl text-sm font-semibold border transition-colors',
                  feeType === 'PAID'
                    ? 'bg-foreground text-background border-foreground'
                    : 'bg-card text-foreground border-border',
                )}
              >
                유료
              </button>
            </div>
          </Field>

          {feeType === 'PAID' && (
            <Field label="가격">
              <TextInput value={price} onChange={setPrice} placeholder="예: 10000" type="number" />
            </Field>
          )}

          <Field label="운영 기간">
            <div className="flex items-center gap-2">
              <div className="relative flex-1">
                <CalendarDays size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                <input
                  type="date"
                  value={opStart}
                  onChange={(e) => setOpStart(e.target.value)}
                  className="w-full pl-8 pr-3 py-3 bg-secondary border border-transparent rounded-xl text-sm text-foreground focus:outline-none focus:border-foreground/30 transition-colors"
                />
              </div>
              <span className="text-muted-foreground text-sm shrink-0">~</span>
              <div className="relative flex-1">
                <CalendarDays size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                <input
                  type="date"
                  value={opEnd}
                  onChange={(e) => setOpEnd(e.target.value)}
                  className="w-full pl-8 pr-3 py-3 bg-secondary border border-transparent rounded-xl text-sm text-foreground focus:outline-none focus:border-foreground/30 transition-colors"
                />
              </div>
            </div>
          </Field>

          <Field label="접수 기간">
            <div className="flex items-center gap-2">
              <div className="relative flex-1">
                <CalendarDays size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                <input
                  type="date"
                  value={regStart}
                  onChange={(e) => setRegStart(e.target.value)}
                  className="w-full pl-8 pr-3 py-3 bg-secondary border border-transparent rounded-xl text-sm text-foreground focus:outline-none focus:border-foreground/30 transition-colors"
                />
              </div>
              <span className="text-muted-foreground text-sm shrink-0">~</span>
              <div className="relative flex-1">
                <CalendarDays size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-muted-foreground pointer-events-none" />
                <input
                  type="date"
                  value={regEnd}
                  onChange={(e) => setRegEnd(e.target.value)}
                  className="w-full pl-8 pr-3 py-3 bg-secondary border border-transparent rounded-xl text-sm text-foreground focus:outline-none focus:border-foreground/30 transition-colors"
                />
              </div>
            </div>
          </Field>

          <Field label="팝업 소개">
            <TextArea value={description} onChange={setDescription} placeholder="팝업스토어를 소개해주세요." />
          </Field>

          {/* Reservation slots */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <p className="text-[12px] font-bold text-foreground">예약 슬롯</p>
              <button
                onClick={() => setShowDeleteAll(true)}
                disabled={slots.length === 0}
                className={cn(
                  'text-[11px] font-semibold transition-colors',
                  slots.length > 0
                    ? 'text-[oklch(0.62_0.24_25)]'
                    : 'text-muted-foreground/40 cursor-not-allowed',
                )}
              >
                전체 삭제
              </button>
            </div>

            {sortedSlots.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-6 bg-secondary rounded-2xl gap-2">
                <Clock size={22} strokeWidth={1.4} className="text-muted-foreground" />
                <p className="text-[12px] text-muted-foreground">등록된 예약 슬롯이 없습니다.</p>
              </div>
            ) : (
              <div className="space-y-1.5">
                {sortedSlots.map((slot) => (
                  <div
                    key={slot.id}
                    className="flex items-center gap-3 bg-secondary rounded-xl px-3.5 py-2.5"
                  >
                    <CalendarDays size={14} className="text-muted-foreground shrink-0" />
                    <span className="text-[12px] font-semibold text-foreground flex-1">
                      {formatDateKorean(slot.date)}
                    </span>
                    <span className="text-[12px] text-muted-foreground">{slot.time}</span>
                    <span className="text-[11px] text-muted-foreground">정원 {slot.capacity}</span>
                    <button
                      onClick={() => handleDeleteSlot(slot.id)}
                      className="p-1 rounded-lg hover:bg-border transition-colors"
                    >
                      <X size={14} strokeWidth={2} className="text-muted-foreground" />
                    </button>
                  </div>
                ))}
              </div>
            )}

            <button
              onClick={() => setShowAddSlot(true)}
              className="w-full py-3 rounded-xl border-2 border-dashed border-border flex items-center justify-center gap-2 text-[13px] font-semibold text-foreground/70 active:opacity-60 transition-opacity"
            >
              <Plus size={15} strokeWidth={2.5} />
              슬롯 추가
            </button>
          </div>
        </div>
      </div>

      {/* Fixed bottom actions */}
      <div className="absolute bottom-0 left-0 right-0 bg-card border-t border-border px-4 py-3 space-y-2">
        {mode === 'edit' && (
          <button
            onClick={() => setShowDeleteStore(true)}
            className="w-full py-3 rounded-xl border border-[oklch(0.62_0.24_25)]/40 text-[oklch(0.62_0.24_25)] text-sm font-semibold"
          >
            팝업스토어 삭제
          </button>
        )}
        <button
          disabled={!canSave || saving || uploading}
          onClick={handleSave}
          className={cn(
            'w-full py-3.5 rounded-xl text-sm font-bold transition-colors',
            canSave && !saving && !uploading
              ? 'bg-foreground text-background active:scale-[0.98]'
              : 'bg-secondary text-muted-foreground cursor-not-allowed',
          )}
        >
          {saving ? '저장 중...' : mode === 'create' ? '팝업스토어 등록' : '변경사항 저장'}
        </button>
      </div>

      {/* Modals */}
      {showAddSlot && (
        <AddSlotModal
          operationStart={opStart}
          operationEnd={opEnd}
          onCancel={() => setShowAddSlot(false)}
          onConfirm={handleAddSlots}
        />
      )}
      {showDeleteAll && (
        <DeleteAllSlotsModal
          onCancel={() => setShowDeleteAll(false)}
          onConfirm={handleDeleteAll}
        />
      )}
      {showDeleteStore && (
        <DeleteStoreModal
          storeName={name}
          onCancel={() => setShowDeleteStore(false)}
          onConfirm={handleDeleteStore}
        />
      )}
    </div>
  )
}