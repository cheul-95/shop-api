package com.lsch.shop.domain.product.service;

import com.lsch.shop.domain.product.dto.ProductResponse;
import com.lsch.shop.domain.product.entity.Product;
import com.lsch.shop.domain.product.repository.ProductRepository;

import com.lsch.shop.global.error.ErrorCode;
import com.lsch.shop.global.error.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Transactional(readOnly = true)
@Service
@RequiredArgsConstructor
public class ProductService {
    private final ProductRepository productRepository;

    public Page<ProductResponse> getProducts(String keyword, Pageable pageable) {

        if(!StringUtils.hasText(keyword)){
            return productRepository.findAll(pageable)
                    .map(ProductResponse::from);
        }
        return productRepository.findByNameContaining(keyword, pageable)
                .map(ProductResponse::from);
    }

    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PRODUCT_NOT_FOUND));
        return ProductResponse.from(product);
    }
}
