import { apiRequest } from '@/lib/api'

export type PopupFeeType = 'FREE' | 'PAID'
export type PopupStatus = 'UPCOMING' | 'OPEN' | 'CLOSED'
export type ReservationStatus = 'HELD' | 'CONFIRMED' | 'CANCELED' | 'EXPIRED'

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

export interface ReservationCreateResponse {
  reservationId: number
  status: ReservationStatus
  heldUntil: string
  slotId: number
  slotDate: string
  startTime: string
}

export interface ReservationPaymentResponse {
  reservationId: number | null
  status: ReservationStatus | null
  popupName: string | null
  location: string | null
  reservationDate: string | null
  reservationTime: string | null
  orderId: string | null
  orderName: string | null
  amount: number | null
}

export const reservationApi = {
  getPopupDetail: (popupStoreId: string) =>
    apiRequest<PopupStoreDetailResponse>(`/popups/${popupStoreId}`),

  getSlots: (popupStoreId: string, date: string) =>
    apiRequest<ReservationSlotResponse[]>(
      `/popups/${popupStoreId}/slots?date=${encodeURIComponent(date)}`,
    ),

  createReservation: (slotId: number) =>
    apiRequest<ReservationCreateResponse>('/reservations', {
      method: 'POST',
      body: JSON.stringify({ slotId }),
    }),

  startPayment: (
    reservationId: number,
    request: { name: string; phone: string; idempotencyKey: string },
  ) =>
    apiRequest<ReservationPaymentResponse>(`/reservations/${reservationId}/payments`, {
      method: 'POST',
      body: JSON.stringify(request),
    }),
}
