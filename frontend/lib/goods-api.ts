import { apiRequest } from '@/lib/api'

export type GoodsStatus = 'ON_SALE' | 'ENDED'
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
