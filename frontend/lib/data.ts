export type ReservationStatus = '예약 가능' | '마감 임박' | '마감' | '오픈예정'
export type StockStatus = '판매중' | '품절'
export type CouponStatus = '발급 가능' | '소진'
export type ReservationHistoryStatus = '예약 완료' | '방문 완료' | '예약 취소'

export interface PopupStore {
  id: string
  name: string
  location: string
  period: string
  startDate: string // YYYY-MM-DD
  endDate: string   // YYYY-MM-DD
  reservationStatus: ReservationStatus
  hasGoods: boolean
  hasCoupon: boolean
  image: string
  description: string
  category: string[]
  tags: string[]
  timeSlots: TimeSlot[]
  goods: GoodsItem[]
  coupons: CouponItem[]
  // 0 = free reservation
  ticketPrice: number
}

export interface ReservationPayload {
  storeId: string
  slotId: number
  date: string
  time: string
}

export interface CartItem {
  goodsId: string
  quantity: number
}

export interface GoodsOrderPayload {
  storeId: string
  cart: CartItem[]
  appliedCouponId: string | null
}

export interface TimeSlot {
  time: string
  status: ReservationStatus
}

export interface GoodsItem {
  id: string
  name: string
  price: number
  status: StockStatus
  image: string
  stock?: number
  description?: string
  detailImages?: string[]
}

export interface CouponItem {
  id: string
  title: string
  discount: string
  minOrder: string
  expiresAt: string
  status: CouponStatus
}

export interface UserReservation {
  id: string
  reservationNumber: string
  storeName: string
  location: string
  date: string
  timeSlot: string
  price: number
  status: ReservationHistoryStatus
  storeImage: string
}

export interface OrderItem {
  goodsId: string
  name: string
  image: string
  price: number
  quantity: number
}

export interface ShippingInfo {
  name: string
  phone: string
  address: string
}

export type OrderStatus = '결제 완료' | '배송 준비중' | '배송중' | '배송 완료' | '주문 취소'

export interface PurchasedGoods {
  id: string
  orderNumber: string
  storeName: string
  storeImage: string
  items: OrderItem[]
  orderStatus: OrderStatus
  subtotal: number
  couponDiscount: number
  finalAmount: number
  shippingInfo: ShippingInfo
  paymentStatus: string
  paidAt: string
}

export interface ClaimedCoupon {
  id: string
  title: string
  discount: string
  storeName: string
  claimedAt: string
  expiresAt: string
  isUsed: boolean
}

// ---------- Sample Data ----------

export const popupStores: PopupStore[] = [
  {
    id: '1',
    name: '성수 빈티지 토이 팝업',
    location: '서울 성동구 성수동',
    period: '2025.06.14 – 06.22',
    startDate: '2025-06-14',
    endDate: '2025-06-22',
    reservationStatus: '마감 임박',
    hasGoods: true,
    hasCoupon: true,
    image: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80',
    description:
      '90년대 빈티지 피규어부터 희귀 토이까지. 성수동 한복판에서 만나는 레트로 감성의 토이 팝업스토어. 한정 굿즈와 선착순 쿠폰을 놓치지 마세요.',
    category: ['전체', '마감임박', '굿즈판매'],
    tags: ['빈티지', '피규어', '레트로'],
    ticketPrice: 5000,
    timeSlots: [
      { time: '11:00', status: '마감' },
      { time: '13:00', status: '마감 임박' },
      { time: '15:00', status: '예약 가능' },
      { time: '17:00', status: '예약 가능' },
    ],
    goods: [
      { id: 'g1', name: '한정판 레트로 피규어 세트', price: 48000, status: '판매중', image: 'https://images.unsplash.com/photo-1608889175157-4e9f9cbdce3f?w=400&q=80', stock: 20, description: '90년대 감성의 레트로 피규어 5종 세트. 성수 팝업 한정 수량으로만 판매합니다. 각 피규어는 개별 케이스에 포장되어 있으며 컬렉터블 에디션 박스로 제공됩니다.', detailImages: ['https://images.unsplash.com/photo-1608889175157-4e9f9cbdce3f?w=800&q=80', 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80'] },
      { id: 'g2', name: '시그니처 키링', price: 12000, status: '판매중', image: 'https://images.unsplash.com/photo-1614632537197-38a17061c2bd?w=400&q=80', stock: 80, description: '팝업 전용 시그니처 로고 키링. 투명 아크릴 소재로 제작되어 빛에 따라 달라지는 색감이 특징입니다.', detailImages: ['https://images.unsplash.com/photo-1614632537197-38a17061c2bd?w=800&q=80'] },
      { id: 'g3', name: '포토카드 세트 (5종)', price: 18000, status: '품절', image: 'https://images.unsplash.com/photo-1636466497217-26a8cbeaf0aa?w=400&q=80', stock: 0, description: '팝업 전용 포토카드 5종 패키지. 이미 완판되었습니다.', detailImages: ['https://images.unsplash.com/photo-1636466497217-26a8cbeaf0aa?w=800&q=80'] },
      { id: 'g4', name: '로고 에코백', price: 24000, status: '판매중', image: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&q=80', stock: 35, description: '두꺼운 면 소재의 로고 에코백. 일상에서 가볍게 들기 좋은 사이즈로 팝업 전용 디자인이 인쇄되어 있습니다.', detailImages: ['https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=800&q=80'] },
    ],
    coupons: [
      { id: 'c1', title: '현장 결제 10% 할인 쿠폰', discount: '10% 할인', minOrder: '10,000원 이상', expiresAt: '2025.06.22', status: '발급 가능' },
      { id: 'c2', title: '굿즈 구매 3,000원 할인', discount: '3,000원 할인', minOrder: '20,000원 이상', expiresAt: '2025.06.22', status: '소진' },
      { id: 'c3', title: '음료 무료 교환 쿠폰', discount: '음료 1잔 무료', minOrder: '없음', expiresAt: '2025.06.22', status: '발급 가능' },
    ],
  },
  {
    id: '2',
    name: '홍대 스트릿 패션 팝업',
    location: '서울 마포구 홍대입구',
    period: '2025.06.18 – 06.28',
    startDate: '2025-06-18',
    endDate: '2025-06-28',
    reservationStatus: '예약 가능',
    hasGoods: true,
    hasCoupon: false,
    image: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800&q=80',
    description:
      '국내 인디 브랜드 15개가 모인 스트릿 패션 팝업. 한정판 그래픽 티셔츠와 컬래버 아이템을 오직 이곳에서만 만날 수 있습니다.',
    category: ['전체', '진행중', '굿즈판매'],
    tags: ['스트릿', '패션', '한정판'],
    ticketPrice: 0,
    timeSlots: [
      { time: '11:00', status: '예약 가능' },
      { time: '13:00', status: '예약 가능' },
      { time: '15:00', status: '마감 임박' },
      { time: '17:00', status: '예약 가능' },
    ],
    goods: [
      { id: 'g5', name: '한정판 그래픽 티셔츠', price: 55000, status: '판매중', image: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=400&q=80', stock: 40, description: '홍대 팝업 독점 그래픽 티셔츠. 국내 유명 아티스트와의 컬래버 디자인으로 팝업 기간 중에만 구매 가능합니다. 소재: 코튼 100%, 유니섹스 핏.', detailImages: ['https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&q=80', 'https://images.unsplash.com/photo-1556821840-3a63f15732ce?w=800&q=80'] },
      { id: 'g6', name: '컬래버 후드티', price: 89000, status: '판매중', image: 'https://images.unsplash.com/photo-1556821840-3a63f15732ce?w=400&q=80', stock: 15, description: '스트릿 브랜드와의 한정 컬래버 후드티. 넉넉한 오버사이즈 핏으로 제작되었으며 자수 로고가 특징입니다.', detailImages: ['https://images.unsplash.com/photo-1556821840-3a63f15732ce?w=800&q=80'] },
      { id: 'g7', name: '로고 캡 모자', price: 38000, status: '품절', image: 'https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=400&q=80', stock: 0, description: '팝업 전용 로고 캡. 이미 완판되었습니다.', detailImages: ['https://images.unsplash.com/photo-1588850561407-ed78c282e89b?w=800&q=80'] },
    ],
    coupons: [],
  },
  {
    id: '3',
    name: '더현대 한정 굿즈 팝업',
    location: '서울 영등포구 여의도',
    period: '2025.06.25 – 07.06',
    startDate: '2025-06-25',
    endDate: '2025-07-06',
    reservationStatus: '마감',
    hasGoods: true,
    hasCoupon: true,
    image: 'https://images.unsplash.com/photo-1555529669-e69e7aa0ba9a?w=800&q=80',
    description:
      '더현대서울과 유명 IP의 콜라보 팝업. 국내 최초 공개 아이템을 포함한 100여 종의 한정 굿즈를 만나보세요.',
    category: ['전체', '오픈예정', '굿즈판매'],
    tags: ['콜라보', 'IP', '한정'],
    ticketPrice: 3000,
    timeSlots: [
      { time: '11:00', status: '마감' },
      { time: '13:00', status: '마감' },
      { time: '15:00', status: '마감' },
      { time: '17:00', status: '마감' },
    ],
    goods: [
      { id: 'g8', name: '한정판 그래픽 티셔츠', price: 62000, status: '품절', image: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=400&q=80' },
      { id: 'g9', name: '포토카드 세트 (10종)', price: 32000, status: '판매중', image: 'https://images.unsplash.com/photo-1636466497217-26a8cbeaf0aa?w=400&q=80' },
    ],
    coupons: [
      { id: 'c4', title: '현장 결제 10% 할인 쿠폰', discount: '10% 할인', minOrder: '10,000원 이상', expiresAt: '2025.07.06', status: '소진' },
    ],
  },
  {
    id: '4',
    name: '잠실 캐릭터 페어 팝업',
    location: '����울 송파구 잠실',
    period: '2025.07.05 – 07.20',
    startDate: '2025-07-05',
    endDate: '2025-07-20',
    reservationStatus: '오픈예정',
    hasGoods: true,
    hasCoupon: true,
    image: 'https://images.unsplash.com/photo-1591085686350-798c0f9faa7f?w=800&q=80',
    description:
      '30개 이상의 국내외 인기 캐릭터 브랜드가 한자리에. 잠실 롯데월드몰에서 펼쳐지는 역대 최대 규모의 캐릭터 페어.',
    category: ['전체', '오픈예정', '굿즈판매'],
    tags: ['캐릭터', '피규어', '굿즈'],
    ticketPrice: 0,
    timeSlots: [
      { time: '11:00', status: '예약 가능' },
      { time: '13:00', status: '예약 가능' },
      { time: '15:00', status: '예약 가능' },
      { time: '17:00', status: '예약 가능' },
    ],
    goods: [
      { id: 'g10', name: '시그니처 키링', price: 14000, status: '판매중', image: 'https://images.unsplash.com/photo-1614632537197-38a17061c2bd?w=400&q=80' },
      { id: 'g11', name: '캐릭터 에코백', price: 28000, status: '판매중', image: 'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=400&q=80' },
      { id: 'g12', name: '포토카드 세트 (8종)', price: 22000, status: '판매중', image: 'https://images.unsplash.com/photo-1636466497217-26a8cbeaf0aa?w=400&q=80' },
    ],
    coupons: [
      { id: 'c5', title: '굿즈 구매 3,000원 할인', discount: '3,000원 할인', minOrder: '15,000원 이상', expiresAt: '2025.07.20', status: '발급 가능' },
      { id: 'c6', title: '음료 무료 교환 쿠폰', discount: '음료 1잔 무료', minOrder: '없음', expiresAt: '2025.07.20', status: '발급 가능' },
    ],
  },
]

export interface CouponIssuancePayload {
  storeId: string
  couponId: string
}

export const userProfile = {
  name: '김지수',
  handle: '@jisoo_kr',
  avatarInitials: '김',
  reservations: 3,
  purchases: 7,
  coupons: 4,
}

export const reservationHistory: UserReservation[] = [
  {
    id: 'r1',
    reservationNumber: 'RSV-20250618-001',
    storeName: '성수 빈티지 토이 팝업',
    location: '서울 성동구 성수동',
    date: '2025.06.18 (수)',
    timeSlot: '15:00',
    price: 5000,
    status: '예약 완료',
    storeImage: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400&q=80',
  },
  {
    id: 'r2',
    reservationNumber: 'RSV-20250610-004',
    storeName: '홍대 스트릿 패션 팝업',
    location: '서울 마포구 홍대입구',
    date: '2025.06.10 (화)',
    timeSlot: '13:00',
    price: 0,
    status: '방문 완료',
    storeImage: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=400&q=80',
  },
  {
    id: 'r3',
    reservationNumber: 'RSV-20250605-009',
    storeName: '더현대 한정 굿즈 팝업',
    location: '서울 영등포구 여의도',
    date: '2025.06.05 (목)',
    timeSlot: '11:00',
    price: 3000,
    status: '예약 취소',
    storeImage: 'https://images.unsplash.com/photo-1555529669-e69e7aa0ba9a?w=400&q=80',
  },
]

export const purchasedGoods: PurchasedGoods[] = [
  {
    id: 'p1',
    orderNumber: 'ORD-20250608-0012',
    storeName: '성수 빈티지 토이 팝업',
    storeImage: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=400&q=80',
    items: [
      {
        goodsId: 'g1',
        name: '한정판 레트로 피규어 세트',
        image: 'https://images.unsplash.com/photo-1608889175157-4e9f9cbdce3f?w=400&q=80',
        price: 48000,
        quantity: 1,
      },
      {
        goodsId: 'g2',
        name: '시그니처 키링',
        image: 'https://images.unsplash.com/photo-1614632537197-38a17061c2bd?w=400&q=80',
        price: 12000,
        quantity: 2,
      },
    ],
    orderStatus: '배송 완료',
    subtotal: 72000,
    couponDiscount: 3000,
    finalAmount: 69000,
    shippingInfo: {
      name: '김지수',
      phone: '010-1234-5678',
      address: '서울 마포구 와우산로 123, 101동 201호',
    },
    paymentStatus: '결제 완료',
    paidAt: '2025.06.08 14:32',
  },
  {
    id: 'p2',
    orderNumber: 'ORD-20250610-0028',
    storeName: '홍대 스트릿 패션 팝업',
    storeImage: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=400&q=80',
    items: [
      {
        goodsId: 'g5',
        name: '한정판 그래픽 티셔츠',
        image: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=400&q=80',
        price: 55000,
        quantity: 1,
      },
    ],
    orderStatus: '배송중',
    subtotal: 55000,
    couponDiscount: 0,
    finalAmount: 55000,
    shippingInfo: {
      name: '김지수',
      phone: '010-1234-5678',
      address: '서울 마포구 와우산로 123, 101동 201호',
    },
    paymentStatus: '결제 완료',
    paidAt: '2025.06.10 17:05',
  },
]

export const claimedCoupons: ClaimedCoupon[] = [
  {
    id: 'cc1',
    title: '현장 결제 10% 할인 쿠폰',
    discount: '10% 할인',
    storeName: '성수 빈티지 토이 팝업',
    claimedAt: '2025.06.07',
    expiresAt: '2025.06.22',
    isUsed: false,
  },
  {
    id: 'cc2',
    title: '음료 무료 교환 쿠폰',
    discount: '음료 1잔 무료',
    storeName: '홍대 스트릿 패션 팝업',
    claimedAt: '2025.06.10',
    expiresAt: '2025.06.28',
    isUsed: true,
  },
  {
    id: 'cc3',
    title: '굿즈 구매 3,000원 할인',
    discount: '3,000원 할인',
    storeName: '잠실 캐릭터 페어 팝업',
    claimedAt: '2025.06.12',
    expiresAt: '2025.07.20',
    isUsed: false,
  },
]

// ---------- Organizer Types ----------

export type OrgStoreStatus = '오픈예정' | '운영중' | '접수마감' | '예약마감'
export type OrgCouponStatus = 'ACTIVE' | 'SOLDOUT' | 'EXPIRED'
export type DiscountType = 'AMOUNT' | 'PERCENT'
export type GoodsSalesStatus = 'READY' | 'ON_SALE' | 'SOLD_OUT'

export interface OrgGoods {
  id: string
  storeId: string
  name: string
  price: number
  stock: number
  status: GoodsSalesStatus
  thumbnail: string
  detailImages: string[]
  description: string
}

export interface OrgReservationSlot {
  id: string
  date: string   // YYYY-MM-DD
  time: string   // HH:mm
  capacity: number
}

export interface OrgPopupStore {
  id: string
  name: string
  location: string
  status: OrgStoreStatus
  operationStart: string  // YYYY-MM-DD
  operationEnd: string    // YYYY-MM-DD
  registrationStart: string
  registrationEnd: string
  description: string
  image: string
  reservations: number
  capacity: number
  slots: OrgReservationSlot[]
}

export interface OrgCoupon {
  id: string
  name: string
  discountType: DiscountType
  discountValue: number
  maxDiscount?: number       // PERCENT only
  minOrder?: number          // AMOUNT only
  totalQuantity: number
  issuedQuantity: number
  issuanceStart: string      // YYYY-MM-DD HH:mm
  expiresAt: string          // YYYY-MM-DD
  status: OrgCouponStatus
}

// ---------- Organizer Sample Data ----------

export const orgPopupStores: OrgPopupStore[] = [
  {
    id: 'op1',
    name: '성수 빈티지 토이 팝업',
    location: '서울 성동구 성수동',
    status: '운영중',
    operationStart: '2025-06-14',
    operationEnd: '2025-06-22',
    registrationStart: '2025-06-07',
    registrationEnd: '2025-06-21',
    description: '90년대 빈티지 피규어부터 희귀 토이까지. 성수동 한복판에서 만나는 레트로 감성의 토이 팝업스토어.',
    image: 'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80',
    reservations: 23,
    capacity: 50,
    slots: [
      { id: 's1', date: '2025-06-18', time: '11:00', capacity: 10 },
      { id: 's2', date: '2025-06-18', time: '13:00', capacity: 10 },
      { id: 's3', date: '2025-06-19', time: '11:00', capacity: 10 },
      { id: 's4', date: '2025-06-19', time: '15:00', capacity: 10 },
      { id: 's5', date: '2025-06-20', time: '11:00', capacity: 10 },
    ],
  },
  {
    id: 'op2',
    name: '홍대 스트릿 패션 팝업',
    location: '서울 마포구 홍대입구',
    status: '오픈예정',
    operationStart: '2025-06-18',
    operationEnd: '2025-06-28',
    registrationStart: '2025-06-10',
    registrationEnd: '2025-06-27',
    description: '국내 인디 브랜드 15개가 모인 스트릿 패션 팝업. 한정판 그래픽 티셔츠와 컬래버 아이템.',
    image: 'https://images.unsplash.com/photo-1540575467063-178a50c2df87?w=800&q=80',
    reservations: 8,
    capacity: 80,
    slots: [
      { id: 's6', date: '2025-06-18', time: '13:00', capacity: 20 },
      { id: 's7', date: '2025-06-18', time: '15:00', capacity: 20 },
    ],
  },
  {
    id: 'op3',
    name: '잠실 캐릭터 페어 팝업',
    location: '서울 송파구 잠실',
    status: '예약마감',
    operationStart: '2025-07-05',
    operationEnd: '2025-07-20',
    registrationStart: '2025-06-20',
    registrationEnd: '2025-07-04',
    description: '30개 이상의 국내외 인기 캐릭터 브랜드가 한자리에.',
    image: 'https://images.unsplash.com/photo-1591085686350-798c0f9faa7f?w=800&q=80',
    reservations: 50,
    capacity: 50,
    slots: [
      { id: 's8', date: '2025-07-05', time: '11:00', capacity: 25 },
      { id: 's9', date: '2025-07-05', time: '14:00', capacity: 25 },
    ],
  },
]

export const orgCoupons: OrgCoupon[] = [
  {
    id: 'oc1',
    name: '현장 결제 10% 할인 쿠폰',
    discountType: 'PERCENT',
    discountValue: 10,
    maxDiscount: 5000,
    totalQuantity: 100,
    issuedQuantity: 67,
    issuanceStart: '2025-06-07 10:00',
    expiresAt: '2025-06-22',
    status: 'ACTIVE',
  },
  {
    id: 'oc2',
    name: '굿즈 구매 3,000원 할인',
    discountType: 'AMOUNT',
    discountValue: 3000,
    minOrder: 20000,
    totalQuantity: 50,
    issuedQuantity: 50,
    issuanceStart: '2025-06-07 10:00',
    expiresAt: '2025-06-22',
    status: 'SOLDOUT',
  },
  {
    id: 'oc3',
    name: '신규 방문객 음료 무료',
    discountType: 'AMOUNT',
    discountValue: 4500,
    minOrder: 0,
    totalQuantity: 30,
    issuedQuantity: 30,
    issuanceStart: '2025-05-01 09:00',
    expiresAt: '2025-05-31',
    status: 'EXPIRED',
  },
]

export const orgGoods: OrgGoods[] = [
  // op1 — 성수 빈티지 토이 팝업
  {
    id: 'og1',
    storeId: 'op1',
    name: '한정판 레트로 피규어 세트',
    price: 48000,
    stock: 20,
    status: 'ON_SALE',
    thumbnail: 'https://images.unsplash.com/photo-1608889175157-4e9f9cbdce3f?w=400&q=80',
    detailImages: [
      'https://images.unsplash.com/photo-1608889175157-4e9f9cbdce3f?w=800&q=80',
      'https://images.unsplash.com/photo-1558618666-fcd25c85cd64?w=800&q=80',
    ],
    description: '90년대 감성의 레트로 피규어 5종 세트. 성수 팝업 한정 수량.',
  },
  {
    id: 'og2',
    storeId: 'op1',
    name: '시그니처 키링',
    price: 12000,
    stock: 50,
    status: 'ON_SALE',
    thumbnail: 'https://images.unsplash.com/photo-1614632537197-38a17061c2bd?w=400&q=80',
    detailImages: ['https://images.unsplash.com/photo-1614632537197-38a17061c2bd?w=800&q=80'],
    description: '팝업 로고 시그니처 키링. 아크릴 소재.',
  },
  {
    id: 'og3',
    storeId: 'op1',
    name: '포토카드 세트 (5종)',
    price: 18000,
    stock: 0,
    status: 'SOLD_OUT',
    thumbnail: 'https://images.unsplash.com/photo-1636466497217-26a8cbeaf0aa?w=400&q=80',
    detailImages: ['https://images.unsplash.com/photo-1636466497217-26a8cbeaf0aa?w=800&q=80'],
    description: '팝업 전용 포토카드 5종 패키지.',
  },
  // op2 — 홍대 스트릿 패션 팝업
  {
    id: 'og4',
    storeId: 'op2',
    name: '한정판 그래픽 티셔츠',
    price: 55000,
    stock: 30,
    status: 'READY',
    thumbnail: 'https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=400&q=80',
    detailImages: ['https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?w=800&q=80'],
    description: '홍대 팝업 독점 그래픽 티셔츠. 오픈 당일 판매 시작.',
  },
  // op3 — 잠실 캐릭터 페어 팝업 (no goods yet)
]

// Helper: generate date list between two YYYY-MM-DD strings
export function getOperatingDates(startDate: string, endDate: string): string[] {
  const dates: string[] = []
  const start = new Date(startDate)
  const end = new Date(endDate)
  const current = new Date(start)
  while (current <= end) {
    dates.push(current.toISOString().slice(0, 10))
    current.setDate(current.getDate() + 1)
  }
  return dates
}

export function formatDateKorean(dateStr: string): string {
  const d = new Date(dateStr)
  const days = ['일', '월', '화', '수', '목', '금', '토']
  const month = d.getMonth() + 1
  const day = d.getDate()
  const dow = days[d.getDay()]
  return `${month}월 ${day}일 (${dow})`
}
