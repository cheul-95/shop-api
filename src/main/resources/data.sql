-- 로컬 개발용 샘플 데이터 (application-local.yml의 spring.sql.init.mode: always로 앱 시작 시마다 실행)
-- ddl-auto: update라 데이터가 남아 있으므로, PK를 직접 지정하고 INSERT IGNORE로 중복 실행 시 건너뜀
-- SQL로 직접 넣으면 JPA Auditing이 동작하지 않으므로 created_at/updated_at은 NOW()로 채움

INSERT IGNORE INTO category (category_id, name, created_at, updated_at) VALUES
(1, '전자제품', NOW(), NOW()),
(2, '주변기기', NOW(), NOW()),
(3, '생활용품', NOW(), NOW());

INSERT IGNORE INTO product (product_id, category_id, name, price, stock, created_at, updated_at) VALUES
(1,  1, '노트북',            1200000, 10,  NOW(), NOW()),
(2,  1, '게이밍 노트북',      2300000, 5,   NOW(), NOW()),
(3,  1, '태블릿',             800000,  20,  NOW(), NOW()),
(4,  1, '스마트폰',           1100000, 15,  NOW(), NOW()),
(5,  1, '모니터 27인치',      350000,  30,  NOW(), NOW()),
(6,  2, '노트북 거치대',      45000,   50,  NOW(), NOW()),
(7,  2, '노트북 파우치',      25000,   80,  NOW(), NOW()),
(8,  2, '무선 마우스',        30000,   100, NOW(), NOW()),
(9,  2, '기계식 키보드',      120000,  40,  NOW(), NOW()),
(10, 2, 'USB-C 허브',         55000,   60,  NOW(), NOW()),
(11, 3, '텀블러',             18000,   200, NOW(), NOW()),
(12, 3, '데스크 매트',        22000,   70,  NOW(), NOW());
