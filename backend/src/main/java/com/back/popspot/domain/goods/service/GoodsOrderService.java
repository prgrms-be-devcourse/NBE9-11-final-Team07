package com.back.popspot.domain.goods.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.back.popspot.domain.goods.dto.GoodsOrderCreateRequest;
import com.back.popspot.domain.goods.dto.GoodsOrderCreateResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderDetailResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderDetailResponse.DetailItem;
import com.back.popspot.domain.goods.dto.GoodsOrderRefundResponse;
import com.back.popspot.domain.goods.dto.GoodsOrderSummaryResponse;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.entity.GoodsOrder;
import com.back.popspot.domain.goods.entity.GoodsOrderItem;
import com.back.popspot.domain.goods.entity.GoodsOrderStatus;
import com.back.popspot.domain.goods.entity.GoodsStatus;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsOrderItemRepository;
import com.back.popspot.domain.goods.repository.GoodsOrderRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.payment.dto.PaymentCancelRequest;
import com.back.popspot.domain.payment.entity.Payment;
import com.back.popspot.domain.payment.entity.PaymentType;
import com.back.popspot.domain.payment.repository.PaymentRepository;
import com.back.popspot.domain.payment.service.PaymentReadyService;
import com.back.popspot.domain.payment.service.PaymentService;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.dto.PageResponse;
import com.back.popspot.global.exception.BusinessException;
import com.back.popspot.global.exception.ErrorCode;
import com.back.popspot.global.s3.S3Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsOrderService {

	private final UserRepository userRepository;
	private final GoodsRepository goodsRepository;
	private final GoodsImageRepository goodsImageRepository;
	private final GoodsOrderRepository goodsOrderRepository;
	private final GoodsOrderItemRepository goodsOrderItemRepository;
	private final PaymentRepository paymentRepository;
	private final PaymentService paymentService;
	private final TransactionTemplate transactionTemplate;
	private final S3Service s3Service;
	private final PaymentReadyService paymentReadyService;

	@Transactional
	public GoodsOrderCreateResponse createOrder(Long userId, GoodsOrderCreateRequest request) {
		User user = userRepository.findById(userId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

		Payment existingIdempotentPayment = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
			.orElse(null);
		if (existingIdempotentPayment != null) {
			if (existingIdempotentPayment.getGoodsOrder() == null
				|| !existingIdempotentPayment.getGoodsOrder().getUser().getId().equals(userId)
				|| existingIdempotentPayment.getPaymentType() != PaymentType.GOODS) {
				throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
			}

			GoodsOrder existingOrder = existingIdempotentPayment.getGoodsOrder();
			List<GoodsOrderItem> existingItems = goodsOrderItemRepository.findByGoodsOrder_Id(existingOrder.getId());
			return GoodsOrderCreateResponse.from(existingOrder, existingItems, existingIdempotentPayment);
		}

		Map<Long, Integer> quantityByGoodsId = request.getItems().stream()
			.collect(Collectors.groupingBy(
				GoodsOrderCreateRequest.OrderItemRequest::getGoodsId,
				Collectors.summingInt(GoodsOrderCreateRequest.OrderItemRequest::getQuantity)
			));

		Map<Long, Goods> goodsMap = new HashMap<>();
		int totalAmount = 0;

		// goodsId 오름차순 정렬 후 차감 — 다중 굿즈 동시 차감 시 데드락 회피
		for (Long goodsId : quantityByGoodsId.keySet().stream().sorted().toList()) {
			int quantity = quantityByGoodsId.get(goodsId);
			Goods goods = goodsRepository.findByIdAndDeletedAtIsNull(goodsId)
				.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
			if (goods.getStatus() != GoodsStatus.ON_SALE) {
				throw new BusinessException(ErrorCode.GOODS_NOT_ON_SALE);
			}
			// 원자적 갱신 — 0 반환 시 재고 부족
			int affected = goodsRepository.decreaseStock(goodsId, quantity);
			if (affected == 0) {
				throw new BusinessException(ErrorCode.GOODS_OUT_OF_STOCK);
			}
			totalAmount += goods.getPrice() * quantity;
			goodsMap.put(goodsId, goods);
		}

		int discountAmount = 0;
		int finalAmount = totalAmount - discountAmount;

		GoodsOrder newOrder = new GoodsOrder(
			user, totalAmount, discountAmount, finalAmount,
			GoodsOrderStatus.PENDING,
			request.getReceiverName(), request.getReceiverPhone(),
			request.getPostalCode(), request.getAddress(), request.getAddressDetail()
		);
		// 결제 대기 만료 시각 세팅 — 30분 뒤 스케줄러가 만료 처리
		newOrder.setExpiresAt(LocalDateTime.now().plusMinutes(30));
		GoodsOrder order = goodsOrderRepository.save(newOrder);

		List<GoodsOrderItem> savedItems = request.getItems().stream()
			.map(itemReq -> {
				Goods goods = goodsMap.get(itemReq.getGoodsId());
				int itemAmount = goods.getPrice() * itemReq.getQuantity();
				return goodsOrderItemRepository.save(
					new GoodsOrderItem(order, goods, itemReq.getQuantity(), goods.getPrice(), itemAmount)
				);
			})
			.collect(Collectors.toList());

		Payment payment = paymentReadyService.getOrCreateGoodsOrderReadyPayment(
			user,
			order,
			createOrderName(savedItems),
			order.getFinalAmount(),
			request.getIdempotencyKey()
		);

		return GoodsOrderCreateResponse.from(order, savedItems, payment);
	}

	private String createOrderName(List<GoodsOrderItem> items) {
		if (items.size() == 1) {
			return items.get(0).getGoods().getName();
		}

		return items.get(0).getGoods().getName() + " 외 " + (items.size() - 1) + "건";
	}

	// @Transactional 제거 — 외부 PG 호출이 있어서 트랜잭션 밖에서 실행
	public GoodsOrderRefundResponse refundOrder(Long userId, Long goodsOrderId) {
		// 검증은 트랜잭션 없이 조회
		GoodsOrder order = goodsOrderRepository.findById(goodsOrderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!order.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		// PAID 상태만 환불 가능 — 중복 환불 요청도 여기서 차단
		if (order.getStatus() != GoodsOrderStatus.PAID) {
			throw new BusinessException(ErrorCode.GOODS_ORDER_REFUND_NOT_ALLOWED);
		}

		// 굿즈 주문에 연결된 Payment 조회
		Payment payment = paymentRepository.findByGoodsOrder_Id(goodsOrderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PAYMENT_NOT_FOUND));

		// PG 환불 요청 — 트랜잭션 외부에서 실행 (커넥션 점유 방지)
		String idempotencyKey = "refund-" + goodsOrderId + "-" + userId;
		PaymentCancelRequest cancelRequest = new PaymentCancelRequest("굿즈 주문 환불", idempotencyKey);
		paymentService.cancel(payment.getId(), userId, cancelRequest);

		// PG 환불 성공 확정 후 DB 작업만 트랜잭션으로 처리
		transactionTemplate.executeWithoutResult(status -> {
			List<GoodsOrderItem> items = goodsOrderItemRepository.findByGoodsOrder_Id(goodsOrderId);
			for (GoodsOrderItem item : items) {
				goodsRepository.increaseStock(item.getGoods().getId(), item.getQuantity());
			}
			order.updateStatus(GoodsOrderStatus.REFUNDED);
		});

		return GoodsOrderRefundResponse.from(order);
	}

	@Transactional(readOnly = true)
	public PageResponse<GoodsOrderSummaryResponse> getMyOrders(Long userId, GoodsOrderStatus status,
		Pageable pageable) {
		Page<GoodsOrder> orderPage = (status != null)
			? goodsOrderRepository.findByUser_IdAndStatus(userId, status, pageable)
			: goodsOrderRepository.findByUser_Id(userId, pageable);

		List<Long> orderIds = orderPage.getContent().stream()
			.map(GoodsOrder::getId)
			.collect(Collectors.toList());

		Map<Long, List<GoodsOrderItem>> itemsByOrderId = goodsOrderItemRepository
			.findByGoodsOrder_IdIn(orderIds)
			.stream()
			.collect(Collectors.groupingBy(item -> item.getGoodsOrder().getId()));

		Page<GoodsOrderSummaryResponse> responsePage = orderPage.map(order ->
			GoodsOrderSummaryResponse.from(order, itemsByOrderId.getOrDefault(order.getId(), List.of()))
		);

		return PageResponse.from(responsePage);
	}

	@Transactional(readOnly = true)
	public GoodsOrderDetailResponse getOrderDetail(Long userId, Long goodsOrderId) {
		GoodsOrder order = goodsOrderRepository.findById(goodsOrderId)
			.orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
		if (!order.getUser().getId().equals(userId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}

		List<GoodsOrderItem> items = goodsOrderItemRepository.findByGoodsOrder_Id(goodsOrderId);

		// N+1 방지 — goodsId 모아서 썸네일 한 번에 조회
		List<Long> goodsIds = items.stream()
			.map(item -> item.getGoods().getId())
			.toList();

		Map<Long, String> thumbnailUrlMap = goodsIds.isEmpty()
			? Map.of()
			: goodsImageRepository
			  .findByGoods_IdInAndImageTypeOrderByIdAsc(goodsIds, GoodsImageType.PRODUCT)
			  .stream()
			  .filter(img -> img.getImageKey() != null)
			  .collect(Collectors.toMap(
				  img -> img.getGoods().getId(),
				  img -> s3Service.generatePresignedGetUrl(img.getImageKey()),
				  (first, second) -> first
			  ));

		List<DetailItem> detailItems = items.stream()
			.map(item -> DetailItem.from(item, thumbnailUrlMap.get(item.getGoods().getId())))
			.collect(Collectors.toList());

		return GoodsOrderDetailResponse.from(order, detailItems);
	}

	// 스케줄러에서 주문별로 호출 — 독립 트랜잭션으로 처리
	@Transactional
	public void expireOrder(GoodsOrder order, LocalDateTime now) {
		// 경합 가드 — 조회 이후 상태가 바뀌었을 경우 스킵
		if (!order.isExpired(now)) {
			return;
		}
		List<GoodsOrderItem> items = goodsOrderItemRepository.findByGoodsOrder_Id(order.getId());
		for (GoodsOrderItem item : items) {
			goodsRepository.increaseStock(item.getGoods().getId(), item.getQuantity());
		}
		order.expire();
		log.info("[GoodsOrderScheduler] 주문 만료 처리 완료 — goodsOrderId: {}", order.getId());
	}
}
