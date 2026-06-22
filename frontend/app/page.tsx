'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import { BottomNav } from '@/components/bottom-nav'
import { HomeScreen } from '@/components/screens/home-screen'
import { DetailScreen } from '@/components/screens/detail-screen'
import { ReservationPaymentScreen } from '@/components/screens/reservation-payment-screen'
import { ReservationCompleteScreen } from '@/components/screens/reservation-complete-screen'
import { GoodsOrderScreen } from '@/components/screens/goods-order-screen'
import { GoodsOrderCompleteScreen } from '@/components/screens/goods-order-complete-screen'
import { ReservationsScreen } from '@/components/screens/reservations-screen'
import { CouponsScreen } from '@/components/screens/coupons-screen'
import { MyPageScreen } from '@/components/screens/mypage-screen'
import { PurchasesScreen } from '@/components/screens/purchases-screen'
import { PurchaseDetailScreen } from '@/components/screens/purchase-detail-screen'
import { CouponIssuanceScreen } from '@/components/screens/coupon-issuance-screen'
import { PopupStoreListScreen } from '@/components/screens/organizer/popup-store-list-screen'
import { PopupStoreFormScreen } from '@/components/screens/organizer/popup-store-form-screen'
import { CouponListScreen } from '@/components/screens/organizer/coupon-list-screen'
import { GoodsListScreen } from '@/components/screens/organizer/goods-list-screen'
import { GoodsFormScreen } from '@/components/screens/organizer/goods-form-screen'
import type { TabKey } from '@/components/bottom-nav'
import type { ReservationPayload, GoodsOrderPayload, CouponIssuancePayload } from '@/lib/data'
import { orgPopupStores } from '@/lib/data'

type ViewKey =
  | TabKey
  | 'detail'
  | 'payment'
  | 'complete'
  | 'goods-order'
  | 'goods-complete'
  | 'purchases'
  | 'purchase-detail'
  | 'coupon-issuance'
  | 'org-popup-list'
  | 'org-popup-create'
  | 'org-popup-edit'
  | 'org-coupon-list'
  | 'org-goods-list'
  | 'org-goods-create'
  | 'org-goods-edit'

export default function Page() {
  const router = useRouter()
  const [activeTab, setActiveTab] = useState<TabKey>('home')
  const [currentView, setCurrentView] = useState<ViewKey>('home')
  const [selectedStoreId, setSelectedStoreId] = useState<string | null>(null)
  const [reservationPayload, setReservationPayload] = useState<ReservationPayload | null>(null)
  const [goodsOrderPayload, setGoodsOrderPayload] = useState<GoodsOrderPayload | null>(null)
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null)
  const [couponIssuancePayload, setCouponIssuancePayload] = useState<CouponIssuancePayload | null>(null)
  const [couponIssuancePrevView, setCouponIssuancePrevView] = useState<ViewKey>('home')
  const [orgEditStoreId, setOrgEditStoreId] = useState<string | null>(null)
  const [orgGoodsStoreId, setOrgGoodsStoreId] = useState<string>('')
  const [orgGoodsStoreName, setOrgGoodsStoreName] = useState<string>('')
  const [orgCouponStoreId, setOrgCouponStoreId] = useState<string>('')
  const [orgCouponStoreName, setOrgCouponStoreName] = useState<string>('')
  const [orgEditGoodsId, setOrgEditGoodsId] = useState<string | null>(null)

  useEffect(() => {
    const requestedView = new URLSearchParams(window.location.search).get('view')
    if (requestedView === 'reservations') {
      setActiveTab('reservations')
      setCurrentView('reservations')
      window.history.replaceState(null, '', '/')
    }
  }, [])

  // ----- Navigation helpers -----

  function handleStoreSelect(storeId: string) {
    setSelectedStoreId(storeId)
    setCurrentView('detail')
  }

  function handleTabChange(tab: TabKey) {
    setActiveTab(tab)
    setCurrentView(tab)
    setSelectedStoreId(null)
    setReservationPayload(null)
    setGoodsOrderPayload(null)
    setSelectedOrderId(null)
  }

  function handleBack() {
    if (currentView === 'payment' || currentView === 'goods-order') {
      setCurrentView('detail')
    } else if (currentView === 'coupon-issuance') {
      setCurrentView(couponIssuancePrevView)
    } else if (currentView === 'purchase-detail') {
      setCurrentView('purchases')
    } else if (currentView === 'purchases') {
      setCurrentView('mypage')
    } else if (currentView === 'org-popup-list') {
      setCurrentView('mypage')
    } else if (currentView === 'org-coupon-list' || currentView === 'org-goods-list') {
      setCurrentView('org-popup-list')
    } else if (currentView === 'org-popup-create') {
      setCurrentView('org-popup-list')
    } else if (currentView === 'org-popup-edit') {
      setCurrentView('org-popup-list')
    } else if (currentView === 'org-goods-create' || currentView === 'org-goods-edit') {
      setCurrentView('org-goods-list')
    } else {
      setCurrentView(activeTab)
      setSelectedStoreId(null)
      setReservationPayload(null)
      setGoodsOrderPayload(null)
      setSelectedOrderId(null)
    }
  }

  function handleReserve(payload: ReservationPayload) {
    setReservationPayload(payload)
    setCurrentView('payment')
  }

  function handlePaymentComplete() {
    setCurrentView('complete')
  }

  function handleOrderGoods(payload: GoodsOrderPayload) {
    setGoodsOrderPayload(payload)
    setCurrentView('goods-order')
  }

  function handleGoHome() {
    setActiveTab('home')
    setCurrentView('home')
    setSelectedStoreId(null)
    setReservationPayload(null)
    setGoodsOrderPayload(null)
    setSelectedOrderId(null)
  }

  function handleGoReservations() {
    setActiveTab('reservations')
    setCurrentView('reservations')
    setSelectedStoreId(null)
    setReservationPayload(null)
    setGoodsOrderPayload(null)
  }

  function handleGoMyPage() {
    setActiveTab('mypage')
    setCurrentView('mypage')
    setSelectedStoreId(null)
    setGoodsOrderPayload(null)
  }

  function handleViewAllPurchases() {
    setCurrentView('purchases')
  }

  function handleViewPurchaseDetail(orderId: string) {
    setSelectedOrderId(orderId)
    setCurrentView('purchase-detail')
  }

  function handleCouponBannerSelect(payload: CouponIssuancePayload) {
    setCouponIssuancePayload(payload)
    setCouponIssuancePrevView(currentView)
    setCurrentView('coupon-issuance')
  }

  function handleIssueCoupon(payload: CouponIssuancePayload) {
    setCouponIssuancePayload(payload)
    setCouponIssuancePrevView(currentView)
    setCurrentView('coupon-issuance')
  }

  function handleGoMyCoupons() {
    setActiveTab('coupons')
    setCurrentView('coupons')
    setCouponIssuancePayload(null)
  }

  function handleGoPopupStoreManagement() {
    setCurrentView('org-popup-list')
  }

  function handleGoCouponManagement() {
    setCurrentView('org-coupon-list')
  }

  function handleOrgCreateStore() {
    setCurrentView('org-popup-create')
  }

  function handleOrgEditStore(storeId: string) {
    setOrgEditStoreId(storeId)
    setCurrentView('org-popup-edit')
  }

  function handleOrgGoGoods(storeId: string) {
    const store = orgPopupStores.find((s) => s.id === storeId)
    setOrgGoodsStoreId(storeId)
    setOrgGoodsStoreName(store?.name ?? '')
    setCurrentView('org-goods-list')
  }

  function handleOrgGoCoupons(storeId: string) {
    const store = orgPopupStores.find((s) => s.id === storeId)
    setOrgCouponStoreId(storeId)
    setOrgCouponStoreName(store?.name ?? '')
    setCurrentView('org-coupon-list')
  }

  function handleOrgAddGoods() {
    setOrgEditGoodsId(null)
    setCurrentView('org-goods-create')
  }

  function handleOrgEditGoods(goodsId: string) {
    setOrgEditGoodsId(goodsId)
    setCurrentView('org-goods-edit')
  }

  function handleViewAllCoupons() {
    setActiveTab('coupons')
    setCurrentView('coupons')
  }

  // Bottom nav is hidden for transient screens
  const showBottomNav = (
    currentView !== 'detail' &&
    currentView !== 'payment' &&
    currentView !== 'complete' &&
    currentView !== 'goods-order' &&
    currentView !== 'goods-complete' &&
    currentView !== 'purchases' &&
    currentView !== 'purchase-detail' &&
    currentView !== 'coupon-issuance' &&
    currentView !== 'org-popup-list' &&
    currentView !== 'org-popup-create' &&
    currentView !== 'org-popup-edit' &&
    currentView !== 'org-coupon-list' &&
    currentView !== 'org-goods-list' &&
    currentView !== 'org-goods-create' &&
    currentView !== 'org-goods-edit'
  )

  return (
    <div className="min-h-screen flex items-center justify-center bg-[oklch(0.94_0_0)]">
      <div className="relative w-full max-w-[430px] h-screen sm:h-[812px] flex flex-col bg-background sm:rounded-[2.5rem] sm:overflow-hidden sm:shadow-2xl sm:border sm:border-black/10">
        <div className="flex-1 overflow-hidden flex flex-col">

          {currentView === 'home' && (
            <HomeScreen
              onStoreSelect={handleStoreSelect}
              onCouponBannerSelect={handleCouponBannerSelect}
            />
          )}

          {currentView === 'detail' && selectedStoreId && (
            <DetailScreen
              storeId={selectedStoreId}
              onBack={handleBack}
              onReserve={handleReserve}
              onOrderGoods={handleOrderGoods}
              onIssueCoupon={handleIssueCoupon}
            />
          )}

          {currentView === 'payment' && reservationPayload && (
            <ReservationPaymentScreen
              payload={reservationPayload}
              onBack={handleBack}
              onComplete={handlePaymentComplete}
            />
          )}

          {currentView === 'complete' && reservationPayload && (
            <ReservationCompleteScreen
              payload={reservationPayload}
              onGoHome={handleGoHome}
              onGoReservations={handleGoReservations}
            />
          )}

          {currentView === 'goods-order' && goodsOrderPayload && (
            <GoodsOrderScreen
              payload={goodsOrderPayload}
              onBack={handleBack}
            />
          )}

          {currentView === 'goods-complete' && goodsOrderPayload && (
            <GoodsOrderCompleteScreen
              payload={goodsOrderPayload}
              onGoHome={handleGoHome}
              onGoMyPage={handleGoMyPage}
            />
          )}

          {currentView === 'reservations' && <ReservationsScreen />}

          {currentView === 'coupons' && <CouponsScreen />}

          {currentView === 'mypage' && (
            <MyPageScreen
              onViewAllReservations={handleGoReservations}
              onViewAllPurchases={handleViewAllPurchases}
              onViewPurchaseDetail={handleViewPurchaseDetail}
              onViewAllCoupons={handleViewAllCoupons}
              onGoPopupStoreManagement={handleGoPopupStoreManagement}
            />
          )}

          {currentView === 'purchases' && (
            <PurchasesScreen
              onBack={handleBack}
              onViewDetail={handleViewPurchaseDetail}
            />
          )}

          {currentView === 'purchase-detail' && selectedOrderId && (
            <PurchaseDetailScreen
              orderId={selectedOrderId}
              onBack={handleBack}
            />
          )}

          {currentView === 'coupon-issuance' && couponIssuancePayload && (
            <CouponIssuanceScreen
              payload={couponIssuancePayload}
              onBack={handleBack}
              onGoMyCoupons={handleGoMyCoupons}
            />
          )}

          {currentView === 'org-popup-list' && (
            <PopupStoreListScreen
              onBack={handleBack}
              onCreate={handleOrgCreateStore}
              onEdit={handleOrgEditStore}
              onGoGoods={handleOrgGoGoods}
              onGoCoupons={handleOrgGoCoupons}
            />
          )}

          {currentView === 'org-popup-create' && (
            <PopupStoreFormScreen
              mode="create"
              onBack={handleBack}
              onSaved={handleBack}
            />
          )}

          {currentView === 'org-popup-edit' && orgEditStoreId && (
            <PopupStoreFormScreen
              mode="edit"
              storeId={orgEditStoreId}
              onBack={handleBack}
              onSaved={handleBack}
              onDeleted={handleBack}
            />
          )}

          {currentView === 'org-coupon-list' && (
            <CouponListScreen
              onBack={handleBack}
              storeId={orgCouponStoreId}
              storeName={orgCouponStoreName}
            />
          )}

          {currentView === 'org-goods-list' && (
            <GoodsListScreen
              storeId={orgGoodsStoreId}
              storeName={orgGoodsStoreName}
              onBack={handleBack}
              onAdd={handleOrgAddGoods}
              onEdit={handleOrgEditGoods}
            />
          )}

          {currentView === 'org-goods-create' && (
            <GoodsFormScreen
              mode="create"
              storeId={orgGoodsStoreId}
              storeName={orgGoodsStoreName}
              onBack={handleBack}
              onSaved={handleBack}
            />
          )}

          {currentView === 'org-goods-edit' && orgEditGoodsId && (
            <GoodsFormScreen
              mode="edit"
              storeId={orgGoodsStoreId}
              storeName={orgGoodsStoreName}
              goodsId={orgEditGoodsId}
              onBack={handleBack}
              onSaved={handleBack}
              onDeleted={handleBack}
            />
          )}

        </div>

        {showBottomNav && (
          <BottomNav
            active={activeTab}
            onChange={handleTabChange}
            onLogin={() => router.push('/login')}
          />
        )}
      </div>
    </div>
  )
}
