package com.lsch.shop.domain.product.controller;

import com.lsch.shop.domain.product.dto.ProductResponse;
import com.lsch.shop.domain.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Page<ProductResponse> getProducts(@RequestParam(required = false) String keyword, @PageableDefault(size = 10, sort = "id", direction = DESC) Pageable pageable) {
        return productService.getProducts(keyword, pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }
}
