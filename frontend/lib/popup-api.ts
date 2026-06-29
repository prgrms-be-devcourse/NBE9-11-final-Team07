import {apiRequest, apiRequestEnvelope} from './api'
import type {OrgPopupStore, OrgStoreStatus, PopupStore, ReservationStatus, TimeSlot} from './data'

// ── 타입 ──────────────────────────────────────────────────────────────────────

export interface Page<T> {
    content: T[]
    totalElements: number
    totalPages: number
    number: number
    size: number
}

export type PopupStatus = 'UPCOMING' | 'OPEN' | 'CLOSED'
export type PopupFeeType = 'FREE' | 'PAID'

export interface PopupStoreListResponse {
    id: number
    title: string
    location: string
    imageUrl: string | null
    openDate: string
    closeDate: string
    status: PopupStatus
}

export interface PopupStoreDetailResponse {
    id: number
    title: string
    location: string
    imageUrl: string | null
    description: string | null
    feeType: PopupFeeType
    price: number | null
    reservationStartAt: string
    reservationEndAt: string
    openDate: string
    closeDate: string
    status: PopupStatus
}

export interface ReservationSlotResponse {
    slotId: number
    slotDate: string
    startTime: string
    capacity: number
    reservedCount: number
    available: boolean
}

export interface PopupImagePresignResponse {
    tempKey: string
    presignedUrl: string
}

export type PopupDetailEntryResult =
    | { type: 'success'; data: PopupStoreDetailResponse }
    | { type: 'waiting' }

// ── API 함수 ──────────────────────────────────────────────────────────────────

export const getPopups = () =>
    apiRequest<Page<PopupStoreListResponse>>('/popups')

export const getPopupDetail = (popupStoreId: string) =>
    apiRequest<PopupStoreDetailResponse>(`/popups/${popupStoreId}`)

export const getPopupDetailEntry = async (popupStoreId: string): Promise<PopupDetailEntryResult> => {
    const {status, payload} = await apiRequestEnvelope<PopupStoreDetailResponse | null>(`/popups/${popupStoreId}`)
    if (status === 202 && payload.code === 'WAITING') {
        return {type: 'waiting'}
    }
    if (!payload.data) {
        throw new Error(payload.message || '팝업 정보를 불러오지 못했습니다.')
    }
    return {type: 'success', data: payload.data}
}

export const getPopupSlots = (popupStoreId: string, date: string) =>
    apiRequest<ReservationSlotResponse[]>(`/popups/${popupStoreId}/slots?date=${date}`)

export const createPopup = (data: any) =>
    apiRequest<number>('/host/popups', {method: 'POST', body: JSON.stringify(data)})

export const updatePopup = (popupStoreId: string, data: any) =>
    apiRequest<void>(`/host/popups/${popupStoreId}`, {method: 'PATCH', body: JSON.stringify(data)})

export const deletePopup = (popupStoreId: string) =>
    apiRequest<void>(`/host/popups/${popupStoreId}`, {method: 'DELETE'})

export const createSlot = (popupStoreId: string, data: any) =>
    apiRequest<number>(`/host/popups/${popupStoreId}/slots`, {method: 'POST', body: JSON.stringify(data)})

export const deleteSlot = (popupStoreId: string, slotId: string) =>
    apiRequest<void>(`/host/popups/${popupStoreId}/slots/${slotId}`, {method: 'DELETE'})

export const getPopupImagePresignedUrl = (fileName: string) =>
    apiRequest<PopupImagePresignResponse>(
        `/popups/images/presigned-url?fileName=${encodeURIComponent(fileName)}`
    )

export const uploadPopupImage = async (file: File): Promise<string> => {
    const {tempKey, presignedUrl} = await getPopupImagePresignedUrl(file.name)
    const putRes = await fetch(presignedUrl, {method: 'PUT', body: file})
    if (!putRes.ok) {
        const body = await putRes.text().catch(() => '')
        const code = body.match(/<Code>(.*?)<\/Code>/)?.[1]
        throw new Error(`이미지 업로드 실패 (${putRes.status}${code ? `: ${code}` : ''})`)
    }
    return tempKey
}

// ── 매퍼 ──────────────────────────────────────────────────────────────────────

const PLACEHOLDER_IMAGE = 'https://images.unsplash.com/photo-1556742049-0cfed4f6a45d?w=800&q=80'

function dateOnly(iso: string) {
    return iso ? iso.slice(0, 10) : ''
}

function formatPeriod(openDate: string, closeDate: string) {
    const open = dateOnly(openDate).replace(/-/g, '.')
    const closeParts = dateOnly(closeDate).split('-')
    const close = closeParts.length === 3 ? `${closeParts[1]}.${closeParts[2]}` : dateOnly(closeDate)
    return `${open} – ${close}`
}

function statusToReservationStatus(status: PopupStatus): ReservationStatus {
    switch (status) {
        case 'UPCOMING':
            return '오픈예정'
        case 'OPEN':
            return '예약 가능'
        default:
            return '마감'
    }
}

function statusToCategories(status: PopupStatus) {
    const categories = ['전체']
    if (status === 'OPEN') categories.push('진행중')
    if (status === 'UPCOMING') categories.push('오픈예정')
    return categories
}

function statusToOrgStoreStatus(status: PopupStatus): OrgStoreStatus {
    switch (status) {
        case 'UPCOMING':
            return '오픈예정'
        case 'OPEN':
            return '운영중'
        default:
            return '예약마감'
    }
}

export function toPopupStoreFromList(dto: PopupStoreListResponse): PopupStore {
    return {
        id: String(dto.id),
        name: dto.title,
        location: dto.location,
        period: formatPeriod(dto.openDate, dto.closeDate),
        startDate: dateOnly(dto.openDate),
        endDate: dateOnly(dto.closeDate),
        reservationStatus: statusToReservationStatus(dto.status),
        hasGoods: false,
        hasCoupon: false,
        image: dto.imageUrl ?? PLACEHOLDER_IMAGE,
        description: '',
        category: statusToCategories(dto.status),
        tags: [],
        timeSlots: [],
        goods: [],
        coupons: [],
        ticketPrice: 0,
    }
}

export function toPopupStoreFromDetail(dto: PopupStoreDetailResponse): PopupStore {
    return {
        id: String(dto.id),
        name: dto.title,
        location: dto.location,
        period: formatPeriod(dto.openDate, dto.closeDate),
        startDate: dateOnly(dto.openDate),
        endDate: dateOnly(dto.closeDate),
        reservationStatus: statusToReservationStatus(dto.status),
        hasGoods: false,
        hasCoupon: false,
        image: dto.imageUrl ?? PLACEHOLDER_IMAGE,
        description: dto.description ?? '',
        category: statusToCategories(dto.status),
        tags: [],
        timeSlots: [],
        goods: [],
        coupons: [],
        ticketPrice: dto.price ?? 0,
    }
}

export function toOrgPopupStore(dto: PopupStoreListResponse): OrgPopupStore {
    return {
        id: String(dto.id),
        name: dto.title,
        location: dto.location,
        status: statusToOrgStoreStatus(dto.status),
        operationStart: dateOnly(dto.openDate),
        operationEnd: dateOnly(dto.closeDate),
        registrationStart: '',
        registrationEnd: '',
        description: '',
        image: dto.imageUrl ?? PLACEHOLDER_IMAGE,
        reservations: 0,
        capacity: 0,
        slots: [],
    }
}

export function toTimeSlot(dto: ReservationSlotResponse): TimeSlot {
    const remaining = dto.capacity - dto.reservedCount
    let status: ReservationStatus
    if (!dto.available || remaining <= 0) {
        status = '마감'
    } else if (remaining <= Math.max(1, Math.floor(dto.capacity * 0.2))) {
        status = '마감 임박'
    } else {
        status = '예약 가능'
    }
    return {
        time: dto.startTime ? dto.startTime.slice(0, 5) : dto.startTime,
        status,
    }
}
