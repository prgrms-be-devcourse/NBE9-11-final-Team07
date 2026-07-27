package com.back.popspot.domain.goods.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyString;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterResponse;
import com.back.popspot.domain.goods.dto.GoodsUpdateRequest;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.popupStore.dto.PopupStoreCreateRequest;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.s3.S3Service;
import com.back.popspot.support.IntegrationTestSupport;

/**
 * GoodsService S3 move 의 트랜잭션 경계를 검증하는 통합 테스트.
 *
 * <p>부모 {@link IntegrationTestSupport} 의 {@code @Transactional}(자동 롤백)을
 * {@code NOT_SUPPORTED} 로 덮어써 실제 커밋/롤백이 발생하도록 한다.
 * 자동 롤백이 없으므로 {@link #cleanUp()} 에서 커밋된 데이터를 직접 정리한다.
 */
@DisplayName("GoodsService S3 move 트랜잭션 통합 테스트 (커밋 후 이동 / 롤백 시 미이동)")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class GoodsServiceS3MoveIntegrationTest extends IntegrationTestSupport {

	@Autowired
	private GoodsService goodsService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PopupStoreRepository popupStoreRepository;

	@Autowired
	private GoodsRepository goodsRepository;

	@Autowired
	private GoodsImageRepository goodsImageRepository;

	@Autowired
	private PlatformTransactionManager txManager;

	@MockitoBean
	private S3Service s3Service;

	@AfterEach
	void cleanUp() {
		// FK 순서: goods_image → goods → popup_store → users
		goodsImageRepository.deleteAllInBatch();
		goodsRepository.deleteAllInBatch();
		popupStoreRepository.deleteAllInBatch();
		userRepository.deleteAllInBatch();
	}

	@Test
	@DisplayName("registerHostGoods: 롤백 시 S3 move 를 호출하지 않는다")
	void registerHostGoods_onRollback_moveNotCalled() {
		User user = userRepository.save(User.create("test@test.com", "테스터"));
		PopupStore popupStore = popupStoreRepository.save(PopupStore.of(user, buildPopupStoreRequest()));

		given(s3Service.isTempKey("temp/product.jpg")).willReturn(true);
		given(s3Service.isTempKey("temp/detail.jpg")).willReturn(true);
		given(s3Service.extractFileName("temp/product.jpg")).willReturn("product.jpg");
		given(s3Service.extractFileName("temp/detail.jpg")).willReturn("detail.jpg");

		GoodsRegisterRequest request = new GoodsRegisterRequest("테스트 상품", 10000, 50, null, List.of(
			new GoodsRegisterRequest.ImageKeyEntry("temp/product.jpg", GoodsImageType.PRODUCT),
			new GoodsRegisterRequest.ImageKeyEntry("temp/detail.jpg", GoodsImageType.DETAIL)
		));

		Long userId = user.getId();
		Long popupStoreId = popupStore.getId();

		// afterCommit 콜백이 등록된 뒤 강제 예외로 롤백
		assertThatThrownBy(() -> new TransactionTemplate(txManager).execute(status -> {
			goodsService.registerHostGoods(userId, popupStoreId, request);
			throw new RuntimeException("강제 롤백");
		})).isInstanceOf(RuntimeException.class);

		then(s3Service).should(never()).move(anyString(), anyString());
	}

	@Test
	@DisplayName("updateHostGoods: 롤백 시 S3 move 를 호출하지 않는다")
	void updateHostGoods_onRollback_moveNotCalled() {
		User user = userRepository.save(User.create("test@test.com", "테스터"));
		PopupStore popupStore = popupStoreRepository.save(PopupStore.of(user, buildPopupStoreRequest()));
		Goods goods = goodsRepository.save(Goods.register(popupStore, "테스트 상품", 10000, 50, null));
		Long goodsId = goods.getId();
		goodsImageRepository.save(
			GoodsImage.create(goods, "goods/" + goodsId + "/product/old-product.jpg", GoodsImageType.PRODUCT));
		goodsImageRepository.save(
			GoodsImage.create(goods, "goods/" + goodsId + "/detail/old-detail.jpg", GoodsImageType.DETAIL));

		given(s3Service.isTempKey("temp/new-product.jpg")).willReturn(true);
		given(s3Service.isTempKey("temp/new-detail.jpg")).willReturn(true);
		given(s3Service.extractFileName("temp/new-product.jpg")).willReturn("new-product.jpg");
		given(s3Service.extractFileName("temp/new-detail.jpg")).willReturn("new-detail.jpg");

		GoodsUpdateRequest request = new GoodsUpdateRequest(null, null, null, null, List.of(
			new GoodsUpdateRequest.ImageKeyEntry("temp/new-product.jpg", GoodsImageType.PRODUCT),
			new GoodsUpdateRequest.ImageKeyEntry("temp/new-detail.jpg", GoodsImageType.DETAIL)
		));

		Long userId = user.getId();

		// afterCommit 콜백이 등록된 뒤 강제 예외로 롤백
		assertThatThrownBy(() -> new TransactionTemplate(txManager).execute(status -> {
			goodsService.updateHostGoods(userId, goodsId, request);
			throw new RuntimeException("강제 롤백");
		})).isInstanceOf(RuntimeException.class);

		then(s3Service).should(never()).move(anyString(), anyString());
	}

	@Test
	@DisplayName("registerHostGoods: 커밋 성공 시 PRODUCT, DETAIL 각 키 쌍마다 정확히 1회씩 S3 move 를 호출한다")
	void registerHostGoods_onCommit_moveCalledForEachKeyPair() {
		User user = userRepository.save(User.create("test@test.com", "테스터"));
		PopupStore popupStore = popupStoreRepository.save(PopupStore.of(user, buildPopupStoreRequest()));

		given(s3Service.isTempKey("temp/product.jpg")).willReturn(true);
		given(s3Service.isTempKey("temp/detail.jpg")).willReturn(true);
		given(s3Service.extractFileName("temp/product.jpg")).willReturn("product.jpg");
		given(s3Service.extractFileName("temp/detail.jpg")).willReturn("detail.jpg");

		GoodsRegisterRequest request = new GoodsRegisterRequest("테스트 상품", 10000, 50, null, List.of(
			new GoodsRegisterRequest.ImageKeyEntry("temp/product.jpg", GoodsImageType.PRODUCT),
			new GoodsRegisterRequest.ImageKeyEntry("temp/detail.jpg", GoodsImageType.DETAIL)
		));

		// 서비스의 @Transactional 이 자체 커밋 → afterCommit 실행
		GoodsRegisterResponse response = goodsService.registerHostGoods(user.getId(), popupStore.getId(), request);
		Long goodsId = response.id();

		then(s3Service).should().move("temp/product.jpg", "goods/" + goodsId + "/product/product.jpg");
		then(s3Service).should().move("temp/detail.jpg", "goods/" + goodsId + "/detail/detail.jpg");
	}

	private PopupStoreCreateRequest buildPopupStoreRequest() {
		LocalDateTime now = LocalDateTime.now();
		return new PopupStoreCreateRequest(
			"테스트 팝업", "서울", PopupFeeType.FREE, null,
			now.plusDays(1), now.plusDays(2),
			now.plusDays(3), now.plusDays(4),
			null, null
		);
	}
}
