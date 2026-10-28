package com.lsch.shop.domain.product.entity;

import com.lsch.shop.domain.category.entity.Category;
import com.lsch.shop.global.error.ErrorCode;
import com.lsch.shop.global.error.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

class ProductTest {

    @Test
    void decreaseStock() {
        Category category = new Category("전자제품");
        Product product = new Product("노트북", category, 1_000_000, 10);

        product.decreaseStock(3);
        assertThat(product.getStock()).isEqualTo(7);
    }

    @Test
    void outofStock() {
        Category category = new Category("전자제품");
        Product product = new Product("노트북", category, 1_000_000, 10);

        assertThatThrownBy(() -> product.decreaseStock(0))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    void invalidInput() {
        Category category = new Category("전자제품");
        Product product = new Product("노트북", category, 1_000_000, 10);
        assertThatThrownBy(() -> product.decreaseStock(-5))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_QUANTITY);
    }

    @Test
    void invalidStock() {
        Category category = new Category("전자제품");
        Product product = new Product("노트북", category, 1_000_000, 5);
        assertThatThrownBy(() -> product.decreaseStock(10))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OUT_OF_STOCK);
    }
}