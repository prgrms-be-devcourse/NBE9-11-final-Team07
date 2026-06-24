import { apiRequest } from '@/lib/api'
import type { GoodsItem } from '@/lib/data'

export type GoodsStatus = 'ON_SALE' | 'ENDED'
export type GoodsOrderStatus = 'PENDING' | 'PAID' | 'REFUNDED' | 'EXPIRED'

export type GoodsImageType = 'PRODUCT' | 'DETAIL'

export interface GoodsImagePresignRequest {
  imageType: GoodsImageType
  fileNames: string[]
}

export interface GoodsImagePresignResponse {
  imageKey: string
  presignedUrl: string
}

export interface ImageKeyEntry {
  imageKey: string
  imageType: GoodsImageType
}

export async function presignGoodsImage(
  imageType: GoodsImageType,
  fileName: string,
): Promise<GoodsImagePresignResponse> {
  const results = await apiRequest<GoodsImagePresignResponse[]>('/api/v1/host/goods/images', {
    method: 'POST',
    body: JSON.stringify({ imageType, fileNames: [fileName] } satisfies GoodsImagePresignRequest),
  })
  return results[0]
}

export async function uploadGoodsImageToS3(presignedUrl: string, file: File): Promise<void> {
  const res = await fetch(presignedUrl, { method: 'PUT', body: file })
  if (!res.ok) {
    const body = await res.text().catch(() => '')
    const code = body.match(/<Code>(.*?)<\/Code>/)?.[1]
    throw new Error(`이미지 업로드 실패 (${res.status}${code ? `: ${code}` : ''})`)
  }
}

export async function uploadGoodsImage(imageType: GoodsImageType, file: File): Promise<string> {
  const { imageKey, presignedUrl } = await presignGoodsImage(imageType, file.name)
  await uploadGoodsImageToS3(presignedUrl, file)
  return imageKey
}

export interface HostGoodsListResponse {
  id: number
  name: string
  price: number
  stock: number
  productImageUrl: string | null
  detailImageUrl: string | null
}

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

export interface GoodsRegisterRequest {
  name: string
  price: number
  stock: number
  description?: string
  imageKeys: ImageKeyEntry[]
}

export interface GoodsRegisterResponse {
  id: number
  popupStoreId: number
  name: string
  price: number
  stock: number
  description: string | null
}

export const registerGoods = (popupStoreId: string, request: GoodsRegisterRequest) =>
  apiRequest<GoodsRegisterResponse>(`/api/v1/host/popups/${popupStoreId}/goods`, {
    method: 'POST',
    body: JSON.stringify(request),
  })

export interface HostGoodsDetailResponse {
  id: number
  name: string
  price: number
  stock: number
  description: string | null
  productImageUrl: string | null
  detailImageUrl: string | null
}

export interface GoodsUpdateRequest {
  name?: string
  price?: number
  stock?: number
  description?: string | null
  imageKeys?: ImageKeyEntry[]
}

export interface GoodsUpdateResponse {
  id: number
  name: string
  price: number
  stock: number
  description: string | null
}

export const getHostGoodsDetail = (goodsId: string) =>
  apiRequest<HostGoodsDetailResponse>(`/api/v1/host/goods/${goodsId}`)

export const updateHostGoods = (goodsId: string, request: GoodsUpdateRequest) =>
  apiRequest<GoodsUpdateResponse>(`/api/v1/host/goods/${goodsId}`, {
    method: 'PATCH',
    body: JSON.stringify(request),
  })

export const deleteHostGoods = (goodsId: string) =>
  apiRequest<void>(`/api/v1/host/goods/${goodsId}`, { method: 'DELETE' })

export const hostGoodsApi = {
  getGoods: (popupStoreId: string) =>
    apiRequest<HostGoodsListResponse[]>(`/api/v1/host/popups/${popupStoreId}/goods`),
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
