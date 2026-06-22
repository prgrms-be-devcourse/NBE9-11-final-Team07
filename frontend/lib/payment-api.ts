import { ApiError } from '@/lib/api'
import type { ApiResponse } from '@/lib/api'

export type PaymentType = 'POPUP' | 'GOODS'
export type PaymentStatus = 'READY' | 'PAID' | 'FAILED' | 'CANCELED'

export interface PaymentConfirmRequest {
  paymentKey: string
  orderId: string
  amount: number
}

export interface PaymentConfirmResponse {
  paymentId: number
  paymentType: PaymentType
  orderId: string
  paymentKey: string
  orderName: string
  amount: number
  status: PaymentStatus
  approvedAt: string
}

export interface PendingPayment {
  orderId: string
  orderName: string
  amount: number
  customerName?: string
  customerEmail?: string
}

const PENDING_PAYMENT_KEY = 'pendingPayment'
const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080'

export function savePendingPayment(payment: PendingPayment) {
  if (typeof window === 'undefined') return
  window.sessionStorage.setItem(PENDING_PAYMENT_KEY, JSON.stringify(payment))
}

export function getPendingPayment(): PendingPayment | null {
  if (typeof window === 'undefined') return null
  const value = window.sessionStorage.getItem(PENDING_PAYMENT_KEY)
  if (!value) return null

  try {
    const payment = JSON.parse(value) as Partial<PendingPayment>
    if (
      typeof payment.orderId !== 'string' ||
      typeof payment.orderName !== 'string' ||
      typeof payment.amount !== 'number' ||
      (payment.customerName !== undefined && typeof payment.customerName !== 'string') ||
      (payment.customerEmail !== undefined && typeof payment.customerEmail !== 'string')
    ) {
      return null
    }
    return payment as PendingPayment
  } catch {
    return null
  }
}

export function clearPendingPayment() {
  if (typeof window === 'undefined') return
  window.sessionStorage.removeItem(PENDING_PAYMENT_KEY)
}

export async function confirmPayment(request: PaymentConfirmRequest) {
  const headers = new Headers({ 'Content-Type': 'application/json' })

  const response = await fetch(`${API_BASE_URL}/api/payments/confirm`, {
    method: 'POST',
    headers,
    credentials: 'include',
    body: JSON.stringify(request),
  })
  const payload = await response.json().catch(() => null) as
    | PaymentConfirmResponse
    | ApiResponse<PaymentConfirmResponse>
    | null

  if (!response.ok) {
    const errorPayload = payload as ApiResponse<unknown> | null
    throw new ApiError(
      errorPayload?.message ?? '결제 승인에 실패했습니다.',
      response.status,
      errorPayload?.code,
    )
  }
  if (!payload) {
    throw new ApiError('서버 응답을 읽을 수 없습니다.', response.status)
  }

  return 'data' in payload ? payload.data : payload
}
