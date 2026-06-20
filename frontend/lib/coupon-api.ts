import { apiRequest } from '@/lib/api'

export type DiscountType = 'AMOUNT' | 'PERCENT'
export type CouponStatus = 'ACTIVE' | 'SOLDOUT' | 'EXPIRED'
export type UserCouponStatus = 'ISSUED' | 'USED' | 'EXPIRED'

export interface CouponResponse {
  id: number
  popupStoreId: number
  popupStoreTitle: string
  name: string
  discountType: DiscountType
  discountValue: number
  maxDiscountAmount: number | null
  minOrderAmount: number | null
  totalQuantity: number
  issuedQuantity: number
  remainingQuantity: number
  status: CouponStatus
  startedAt: string
  expiredAt: string
  createdAt: string
}

export interface UserCouponResponse {
  id: number
  couponId: number
  popupStoreId: number
  popupStoreTitle: string
  name: string
  discountType: DiscountType
  discountValue: number
  maxDiscountAmount: number | null
  minOrderAmount: number | null
  status: UserCouponStatus
  expiredAt: string
  usedAt: string | null
  issuedAt: string
}

export interface CouponCreateRequest {
  name: string
  discountType: DiscountType
  discountValue: number
  maxDiscountAmount: number | null
  minOrderAmount: number | null
  totalQuantity: number
  startedAt: string
  expiredAt: string
}

export const couponApi = {
  getPublicCoupons: (popupStoreId: string) =>
    apiRequest<CouponResponse[]>(`/popups/${popupStoreId}/coupons`),

  issueCoupon: (couponId: string) =>
    apiRequest<UserCouponResponse>(`/coupons/${couponId}/issue`, { method: 'POST' }),

  getMyCoupons: () => apiRequest<UserCouponResponse[]>('/me/coupons'),

  getHostCoupons: (popupStoreId: string) =>
    apiRequest<CouponResponse[]>(`/host/popups/${popupStoreId}/coupons`),

  createHostCoupon: (popupStoreId: string, request: CouponCreateRequest) =>
    apiRequest<CouponResponse>(`/host/popups/${popupStoreId}/coupons`, {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  deleteHostCoupon: (popupStoreId: string, couponId: number) =>
    apiRequest<null>(`/host/popups/${popupStoreId}/coupons/${couponId}`, {
      method: 'DELETE',
    }),
}

export function formatDiscount(coupon: Pick<CouponResponse, 'discountType' | 'discountValue' | 'maxDiscountAmount'>) {
  if (coupon.discountType === 'AMOUNT') {
    return `${coupon.discountValue.toLocaleString()}원 할인`
  }
  const maximum = coupon.maxDiscountAmount
    ? ` (최대 ${coupon.maxDiscountAmount.toLocaleString()}원)`
    : ''
  return `${coupon.discountValue}% 할인${maximum}`
}
