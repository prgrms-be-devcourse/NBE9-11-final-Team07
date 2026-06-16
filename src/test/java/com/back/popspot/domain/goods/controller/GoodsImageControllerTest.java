package com.back.popspot.domain.goods.controller;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import com.back.popspot.domain.goods.dto.GoodsImagePresignRequest;
import com.back.popspot.domain.goods.dto.GoodsRegisterRequest;
import com.back.popspot.domain.goods.entity.Goods;
import com.back.popspot.domain.goods.entity.GoodsImage;
import com.back.popspot.domain.goods.entity.GoodsImageType;
import com.back.popspot.domain.goods.repository.GoodsImageRepository;
import com.back.popspot.domain.goods.repository.GoodsRepository;
import com.back.popspot.domain.popupStore.entity.PopupFeeType;
import com.back.popspot.domain.popupStore.entity.PopupStore;
import com.back.popspot.domain.popupStore.repository.PopupStoreRepository;
import com.back.popspot.domain.user.entity.User;
import com.back.popspot.domain.user.repository.UserRepository;
import com.back.popspot.global.s3.S3Service;
import com.back.popspot.support.IntegrationTestSupport;

@DisplayName("굿즈 이미지 API")
class GoodsImageControllerTest extends IntegrationTestSupport {

	@MockitoBean
	private S3Service s3Service;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PopupStoreRepository popupStoreRepository;

	@Autowired
	private GoodsRepository goodsRepository;

	@Autowired
	private GoodsImageRepository goodsImageRepository;

	private Long popupStoreId;
	private Long goodsId;

	@BeforeEach
	void setUp() {
		User user = userRepository.save(User.create("host@test.com", "호스트"));

		PopupStore popupStore = new PopupStore();
		ReflectionTestUtils.setField(popupStore, "user", user);
		ReflectionTestUtils.setField(popupStore, "title", "테스트 팝업");
		ReflectionTestUtils.setField(popupStore, "location", "서울");
		ReflectionTestUtils.setField(popupStore, "feeType", PopupFeeType.FREE);
		ReflectionTestUtils.setField(popupStore, "reservationStartAt", LocalDateTime.now().plusDays(1));
		ReflectionTestUtils.setField(popupStore, "reservationEndAt", LocalDateTime.now().plusDays(30));
		popupStoreId = popupStoreRepository.save(popupStore).getId();

		Goods goods = Goods.register(popupStore, "테스트 굿즈", 10000, 50, "테스트 상세");
		goodsId = goodsRepository.save(goods).getId();
	}

	@Test
	@WithMockUser
	@DisplayName("presigned URL 발급에 성공한다")
	void generatePresignedUrls() throws Exception {
		String tempKey = "temp/test-uuid.jpg";
		String presignedUrl = "https://bucket.s3.amazonaws.com/" + tempKey + "?signed=true";

		given(s3Service.buildTempKey(eq("test.jpg"))).willReturn(tempKey);
		given(s3Service.generatePresignedPutUrl(tempKey)).willReturn(presignedUrl);

		GoodsImagePresignRequest request = new GoodsImagePresignRequest(
			GoodsImageType.PRODUCT, List.of("test.jpg")
		);

		mockMvc.perform(post("/host/goods/images")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("SUCCESS"))
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].imageKey").value(tempKey))
			.andExpect(jsonPath("$.data[0].presignedUrl").value(presignedUrl));
	}

	@Test
	@WithMockUser
	@DisplayName("굿즈 등록 시 temp 이미지가 final 경로로 이동되고 GoodsImage가 DB에 저장된다")
	void registerGoods_withImages_movesToFinalPathAndSavesGoodsImage() throws Exception {
		String tempProductKey = "temp/product-uuid.jpg";
		String tempDetailKey = "temp/detail-uuid.jpg";

		// isTempKey, extractFileName은 mock이라 기본값이 false/null이므로 stub 필요
		given(s3Service.isTempKey(tempProductKey)).willReturn(true);
		given(s3Service.isTempKey(tempDetailKey)).willReturn(true);
		given(s3Service.extractFileName(tempProductKey)).willReturn("product-uuid.jpg");
		given(s3Service.extractFileName(tempDetailKey)).willReturn("detail-uuid.jpg");
		// move()는 void — 기본값이 no-op이므로 stub 불필요

		GoodsRegisterRequest request = new GoodsRegisterRequest(
			"신상 굿즈", 15000, 30, "상세 설명",
			List.of(
				new GoodsRegisterRequest.ImageKeyEntry(tempProductKey, GoodsImageType.PRODUCT),
				new GoodsRegisterRequest.ImageKeyEntry(tempDetailKey, GoodsImageType.DETAIL)
			)
		);

		mockMvc.perform(post("/host/popups/{popupStoreId}/goods", popupStoreId)
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.code").value("SUCCESS"));

		// finalKey = ImageDomain.GOODS.finalKey(goodsId, type.code(), fileName)
		// goodsId는 auto-generated이므로 포맷(goods/{id}/{type}/{file}) 기준으로 검증
		List<GoodsImage> savedImages = goodsImageRepository.findAll();
		assertThat(savedImages).hasSize(2);
		assertThat(savedImages).extracting(GoodsImage::getImageType)
			.containsExactlyInAnyOrder(GoodsImageType.PRODUCT, GoodsImageType.DETAIL);
		assertThat(savedImages).extracting(GoodsImage::getImageKey)
			.allSatisfy(key -> assertThat(key).startsWith("goods/"))
			.anySatisfy(key -> assertThat(key).contains("/product/").endsWith("product-uuid.jpg"))
			.anySatisfy(key -> assertThat(key).contains("/detail/").endsWith("detail-uuid.jpg"));
	}
}
