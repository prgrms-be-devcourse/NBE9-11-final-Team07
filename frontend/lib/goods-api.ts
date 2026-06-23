import { apiRequest } from '@/lib/api'

export type GoodsStatus = 'ON_SALE' | 'ENDED'

export interface HostGoodsListResponse {
  id: number
  name: string
  price: number
  stock: number
  productImageUrl: string | null
  detailImageUrl: string | null
}
export type GoodsOrderStatus = 'PENDING' | 'PAID' | 'REFUNDED'

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

export interface GoodsOrderCreateResponse {
  goodsOrderId: number
  items: Array<{
    goodsId: number
    goodsName: string
    quantity: number
    unitPrice: number
    itemAmount: number
  }>
  totalAmount: number
  discountAmount: number
  finalAmount: number
  status: GoodsOrderStatus
  orderId: string
  orderName: string
  amount: number
}

export const hostGoodsApi = {
  getGoods: (popupStoreId: string) =>
    apiRequest<HostGoodsListResponse[]>(`/api/v1/host/popups/${popupStoreId}/goods`),
}

export const goodsApi = {
  getByPopup: (popupStoreId: string) =>
    apiRequest<PageResponse<GoodsSummaryResponse>>(
      `/api/v1/popups/${popupStoreId}/goods?size=100`,
    ),

  createOrder: (request: GoodsOrderCreateRequest) =>
    apiRequest<GoodsOrderCreateResponse>('/api/v1/goods-orders', {
      method: 'POST',
      body: JSON.stringify(request),
    }),
}
