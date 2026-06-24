'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { PopupStoreListScreen } from '@/components/screens/organizer/popup-store-list-screen'
import { PopupStoreFormScreen } from '@/components/screens/organizer/popup-store-form-screen'
import { CouponListScreen } from '@/components/screens/organizer/coupon-list-screen'
import { GoodsListScreen } from '@/components/screens/organizer/goods-list-screen'
import { GoodsFormScreen } from '@/components/screens/organizer/goods-form-screen'
import { orgPopupStores } from '@/lib/data'

type OrgViewKey =
  | 'popup-list'
  | 'popup-create'
  | 'popup-edit'
  | 'coupon-list'
  | 'goods-list'
  | 'goods-create'
  | 'goods-edit'

export default function OrganizerPage() {
  const router = useRouter()
  const [view, setView] = useState<OrgViewKey>('popup-list')
  const [editStoreId, setEditStoreId] = useState<string | null>(null)
  const [goodsStoreId, setGoodsStoreId] = useState<string>('')
  const [goodsStoreName, setGoodsStoreName] = useState<string>('')
  const [couponStoreId, setCouponStoreId] = useState<string>('')
  const [couponStoreName, setCouponStoreName] = useState<string>('')
  const [editGoodsId, setEditGoodsId] = useState<string | null>(null)

  // ----- Navigation helpers -----

  function handleBack() {
    if (view === 'coupon-list' || view === 'goods-list') {
      setView('popup-list')
    } else if (view === 'popup-create' || view === 'popup-edit') {
      setView('popup-list')
    } else if (view === 'goods-create' || view === 'goods-edit') {
      setView('goods-list')
    } else {
      // 루트(팝업 목록)에서 뒤로가기 → 홈으로
      router.push('/')
    }
  }

  function handleCreateStore() {
    setView('popup-create')
  }

  function handleEditStore(storeId: string) {
    setEditStoreId(storeId)
    setView('popup-edit')
  }

  function handleGoGoods(storeId: string) {
    const store = orgPopupStores.find((s) => s.id === storeId)
    setGoodsStoreId(storeId)
    setGoodsStoreName(store?.name ?? '')
    setView('goods-list')
  }

  function handleGoCoupons(storeId: string) {
    const store = orgPopupStores.find((s) => s.id === storeId)
    setCouponStoreId(storeId)
    setCouponStoreName(store?.name ?? '')
    setView('coupon-list')
  }

  function handleAddGoods() {
    setEditGoodsId(null)
    setView('goods-create')
  }

  function handleEditGoods(goodsId: string) {
    setEditGoodsId(goodsId)
    setView('goods-edit')
  }

  return (
    <div className="min-h-screen flex items-center justify-center bg-[oklch(0.94_0_0)]">
      <div className="relative w-full max-w-[430px] h-screen sm:h-[812px] flex flex-col bg-background sm:rounded-[2.5rem] sm:overflow-hidden sm:shadow-2xl sm:border sm:border-black/10">
        <div className="flex-1 overflow-hidden flex flex-col">

          {view === 'popup-list' && (
            <PopupStoreListScreen
              onBack={handleBack}
              onCreate={handleCreateStore}
              onEdit={handleEditStore}
              onGoGoods={handleGoGoods}
              onGoCoupons={handleGoCoupons}
            />
          )}

          {view === 'popup-create' && (
            <PopupStoreFormScreen
              mode="create"
              onBack={handleBack}
              onSaved={handleBack}
            />
          )}

          {view === 'popup-edit' && editStoreId && (
            <PopupStoreFormScreen
              mode="edit"
              storeId={editStoreId}
              onBack={handleBack}
              onSaved={handleBack}
              onDeleted={handleBack}
            />
          )}

          {view === 'coupon-list' && (
            <CouponListScreen
              onBack={handleBack}
              storeId={couponStoreId}
              storeName={couponStoreName}
            />
          )}

          {view === 'goods-list' && (
            <GoodsListScreen
              storeId={goodsStoreId}
              onBack={handleBack}
              onAdd={handleAddGoods}
              onEdit={handleEditGoods}
            />
          )}

          {view === 'goods-create' && (
            <GoodsFormScreen
              mode="create"
              storeId={goodsStoreId}
              storeName={goodsStoreName}
              onBack={handleBack}
              onSaved={handleBack}
            />
          )}

          {view === 'goods-edit' && editGoodsId && (
            <GoodsFormScreen
              mode="edit"
              storeId={goodsStoreId}
              storeName={goodsStoreName}
              goodsId={editGoodsId}
              onBack={handleBack}
              onSaved={handleBack}
            />
          )}

        </div>
      </div>
    </div>
  )
}
