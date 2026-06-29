package com.back.popspot.domain.goods.entity;

import com.back.popspot.global.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(name = "goods_image")
public class GoodsImage extends BaseEntity {

    public static GoodsImage create(Goods goods, String imageKey, GoodsImageType imageType) {
        GoodsImage image = new GoodsImage();
        image.goods = goods;
        image.imageKey = imageKey;
        image.imageType = imageType;
        return image;
    }
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "goods_id", nullable = false)
	private Goods goods;

	@Column(name = "image_key", nullable = false)
	private String imageKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "image_type", nullable = false)
	private GoodsImageType imageType;

    public void changeImageKey(String newImageKey) {
        this.imageKey = newImageKey;
    }
}
