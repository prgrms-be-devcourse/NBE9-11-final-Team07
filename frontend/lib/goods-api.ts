import { apiRequest } from '@/lib/api'
import type { GoodsItem } from '@/lib/data'

export type GoodsStatus = 'ON_SALE' | 'ENDED'
export type GoodsOrderStatus = 'PENDING' | 'PAID' | 'REFUNDED' | 'EXPIRED'

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
}

export interface GoodsSummaryResponse {
  goodsId: number
  name: string
  price: number
  thumbnailImageUrl: string | null
  stock: number
  status: GoodsStatus
}

export interface GoodsDetail {
  goodsId: number
  name: string
  description: string | null
  price: number
  stock: number
  status: GoodsStatus
  images: string[]
  popupStoreId: number
  popupStoreTitle: string
}

export interface GoodsOrderCreateRequest {
  items: Array<{ goodsId: number; quantity: number }>
  couponId: number | null
  idempotencyKey: string
  receiverName: string
  receiverPhone: string
  postalCode: string
  address: string
  addressDetail: string | null
}

export interface GoodsOrderItem {
  goodsId: number
  name: string
  price: number
  quantity: number
  thumbnailImageUrl: string | null
}

export interface GoodsOrderSummaryItem {
  goodsId: number
  goodsName: string
  quantity: number
  itemAmount: number
}

export interface GoodsOrderDetailItem {
  goodsId: number
  goodsName: string
  thumbnailImageUrl: string | null
  quantity: number
  unitPrice: number
  itemAmount: number
}

export interface GoodsOrderCreateResponse {
  goodsOrderId: number
  storeName: string
  items: GoodsOrderItem[]
  totalAmount: number
  discountAmount: number | null
  finalAmount: number
  status: GoodsOrderStatus
  orderId: string
  orderName: string
  amount: number
}

export interface GoodsOrderSummary {
  goodsOrderId: number
  items: GoodsOrderSummaryItem[]
  finalAmount: number
  status: GoodsOrderStatus
  orderedAt: string
}

export interface GoodsOrderDetail {
  goodsOrderId: number
  storeName?: string
  items: GoodsOrderDetailItem[]
  totalAmount: number
  discountAmount: number | null
  finalAmount: number
  status: GoodsOrderStatus
  orderedAt: string
  receiverName: string
  receiverPhone: string
  postalCode: string
  address: string
  addressDetail: string | null
}

export const GOODS_STATUS_LABEL: Record<GoodsStatus, string> = {
  ON_SALE: '판매중',
  ENDED: '판매종료',
}

export const ORDER_STATUS_LABEL: Record<GoodsOrderStatus, string> = {
  PENDING: '결제 대기',
  PAID: '결제 완료',
  REFUNDED: '환불 완료',
  EXPIRED: '주문 만료',
}

export const goodsApi = {
  getByPopup: (popupStoreId: string) =>
    apiRequest<PageResponse<GoodsSummaryResponse>>(
      `/api/v1/popups/${popupStoreId}/goods?size=100`,
    ),

  getGoodsDetail: (goodsId: number) =>
    apiRequest<GoodsDetail>(`/api/v1/goods/${goodsId}`),

  createOrder: (request: GoodsOrderCreateRequest) =>
    apiRequest<GoodsOrderCreateResponse>('/api/v1/goods-orders', {
      method: 'POST',
      body: JSON.stringify(request),
    }),

  getMyGoodsOrders: (status?: GoodsOrderStatus, page = 0) =>
    apiRequest<PageResponse<GoodsOrderSummary>>(
      `/api/v1/me/goods-orders?page=${page}${status ? `&status=${status}` : ''}`,
    ),

  getGoodsOrderDetail: (goodsOrderId: number) =>
    apiRequest<GoodsOrderDetail>(`/api/v1/goods-orders/${goodsOrderId}`),

  requestRefund: (goodsOrderId: number) =>
    apiRequest<void>(`/api/v1/goods-orders/${goodsOrderId}/refund`, { method: 'POST' }),
}

export function toGoodsItem(dto: GoodsSummaryResponse): GoodsItem {
  return {
    id: String(dto.goodsId),
    name: dto.name,
    price: dto.price,
    image: dto.thumbnailImageUrl ?? '/placeholder.png',
    status: dto.status === 'ON_SALE' && dto.stock > 0 ? '판매중' : '품절',
    stock: dto.stock,
  }
}

export function calculateCouponDiscount(
  coupon: {
    discountType: 'AMOUNT' | 'PERCENT'
    discountValue: number
    maxDiscountAmount: number | null
    minOrderAmount: number | null
  } | null | undefined,
  subtotal: number,
): number {
  if (!coupon || coupon.discountValue == null) return 0
  if (coupon.discountType !== 'AMOUNT' && coupon.discountType !== 'PERCENT') return 0
  if (coupon.minOrderAmount != null && subtotal < coupon.minOrderAmount) return 0
  if (coupon.discountType === 'AMOUNT') return Math.min(coupon.discountValue, subtotal)
  const pct = Math.round((subtotal * coupon.discountValue) / 100)
  return coupon.maxDiscountAmount != null ? Math.min(pct, coupon.maxDiscountAmount) : pct
}
