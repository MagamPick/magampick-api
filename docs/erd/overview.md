# ERD Overview

전체 도메인 간 관계의 큰 그림. 각 테이블의 컬럼·인덱스 상세 정의는 **해당 도메인 구현 시점에 [`tables/`](./tables/) 에 점진적으로 작성**한다 (애자일 방식).

## 설계 결정 사항 (전역)

- **식별자**: `BIGINT` (Spring `Long` + `GENERATED ALWAYS AS IDENTITY`)
- **위치 정보**: **PostGIS** 사용 — `stores.location`, `addresses.location` 는 `GEOGRAPHY(POINT, 4326)` + GIST 인덱스
- **Enum**: PostgreSQL native ENUM 대신 `VARCHAR + CHECK` 제약 (Hibernate `@Enumerated(EnumType.STRING)` 표준)
- **Soft Delete (`deleted_at`)**: customers, sellers, stores, clearance_items, orders 등 주요 엔티티만. 종속 데이터(order_items 등)는 hard delete
- **휴대폰 번호 UNIQUE 영구 미적용**: 별개 계정 모델 + 사장 다중 사업자 운영. 본인인증 = 번호 소유자 검증이지 1번호 1계정 강제가 아님 (한국 이커머스 표준). 자세한 사유는 [auth.md §8](../auth.md)
- **시간대**: 모든 `TIMESTAMP` 컬럼은 KST 통일 (운영 인프라 시간대 일치, [api-convention.md §7](../api-convention.md))
- **사용자 분리**: `customers` / `sellers` / `admins` 세 테이블 분리 (가입 흐름·필드 다름)

---

## Diagram

```mermaid
erDiagram
    customers {
        bigint id PK
        varchar email UK
        varchar phone
        varchar nickname
        timestamp phone_verified_at
        timestamp deleted_at
    }
    sellers {
        bigint id PK
        varchar email UK
        varchar business_number UK
        varchar owner_name
        varchar phone
        timestamp deleted_at
    }
    admins {
        bigint id PK
        varchar email UK
        varchar name
    }
    addresses {
        bigint id PK
        bigint customer_id FK
        varchar label
        varchar road_address
        varchar jibun_address
        varchar detail_address
        varchar zonecode
        geography location
        boolean is_default
    }
    favorites {
        bigint customer_id PK_FK
        bigint store_id PK_FK
    }

    store_categories {
        bigint id PK
        varchar name UK
    }
    store_store_categories {
        bigint store_id PK_FK
        bigint store_category_id PK_FK
    }
    stores {
        bigint id PK
        bigint seller_id FK
        varchar name
        varchar road_address
        varchar jibun_address
        varchar detail_address
        varchar zonecode
        geography location
        varchar phone
        varchar description
        varchar image_url
        varchar status
        varchar rejection_reason
        timestamp deleted_at
    }
    store_business_hours {
        bigint id PK
        bigint store_id FK
        smallint day_of_week
        time open_time
        time close_time
    }
    store_closed_days {
        bigint id PK
        bigint store_id FK
        date closed_date
        varchar reason
    }

    products {
        bigint id PK
        bigint store_id FK
        varchar name
        decimal regular_price
        varchar image_url
        varchar status
    }
    clearance_items {
        bigint id PK
        bigint store_id FK
        bigint product_id FK_nullable
        varchar name
        decimal regular_price
        decimal sale_price
        int total_quantity
        int remaining_quantity
        timestamp pickup_start_at
        timestamp pickup_end_at
        varchar status
        timestamp deleted_at
    }
    orders {
        bigint id PK
        bigint customer_id FK
        bigint store_id FK
        decimal total_price
        decimal discount_amount
        decimal point_used
        decimal final_price
        varchar status
        timestamp pickup_time
        timestamp deleted_at
    }
    order_items {
        bigint id PK
        bigint order_id FK
        bigint clearance_item_id FK
        int quantity
        decimal unit_price
        decimal subtotal
    }
    payments {
        bigint id PK
        bigint order_id FK_UK
        varchar payment_key UK
        decimal amount
        varchar method
        varchar status
        timestamp paid_at
    }
    refunds {
        bigint id PK
        bigint payment_id FK
        decimal amount
        varchar reason
        varchar status
        timestamp refunded_at
    }

    notifications {
        bigint id PK
        bigint recipient_id
        varchar recipient_type
        varchar type
        varchar title
        text body
        boolean is_read
    }
    push_tokens {
        bigint id PK
        bigint owner_id
        varchar owner_type
        varchar token UK
        varchar platform
    }
    notification_settings {
        bigint customer_id PK_FK
        boolean order_alert
        boolean clearance_alert
        int radius_km
    }

    reviews {
        bigint id PK
        bigint customer_id FK
        bigint order_id FK_UK
        bigint store_id FK
        int rating
        text content
        timestamp deleted_at
    }
    review_images {
        bigint id PK
        bigint review_id FK
        varchar url
        int sort_order
    }
    review_replies {
        bigint id PK
        bigint review_id FK_UK
        bigint seller_id FK
        text content
    }
    review_reports {
        bigint id PK
        bigint review_id FK
        bigint reporter_id FK
        varchar reason
        varchar status
    }

    points {
        bigint customer_id PK_FK
        bigint balance
        timestamp last_used_at
    }
    point_transactions {
        bigint id PK
        bigint customer_id FK
        bigint order_id FK_nullable
        bigint amount
        varchar type
        varchar reason
        timestamp expires_at
    }
    coupons {
        bigint id PK
        varchar name
        varchar discount_type
        decimal discount_value
        date valid_from
        date valid_until
    }
    user_coupons {
        bigint id PK
        bigint customer_id FK
        bigint coupon_id FK
        varchar status
        timestamp issued_at
        timestamp used_at
    }

    announcements {
        bigint id PK
        bigint admin_id FK
        varchar title
        text content
        timestamp published_at
    }
    inquiries {
        bigint id PK
        bigint customer_id FK
        varchar category
        varchar title
        text content
        varchar status
    }
    inquiry_replies {
        bigint id PK
        bigint inquiry_id FK
        bigint admin_id FK
        text content
    }
    settlements {
        bigint id PK
        bigint seller_id FK
        date period_start
        date period_end
        decimal gross_amount
        decimal fee_amount
        decimal net_amount
        varchar status
        timestamp settled_at
    }

    customers ||--o{ addresses : "owns"
    customers ||--o{ favorites : "saves"
    customers ||--o{ orders : "places"
    customers ||--o{ reviews : "writes"
    customers ||--o{ inquiries : "submits"
    customers ||--|| notification_settings : "configures"
    customers ||--|| points : "owns"
    customers ||--o{ point_transactions : "records"
    customers ||--o{ user_coupons : "owns"
    customers ||--o{ review_reports : "files"

    sellers ||--o{ stores : "operates"
    sellers ||--o{ review_replies : "writes"
    sellers ||--o{ settlements : "receives"

    admins ||--o{ announcements : "publishes"
    admins ||--o{ inquiry_replies : "writes"

    store_categories ||--o{ store_store_categories : "categorizes"
    stores ||--o{ store_store_categories : "has"
    stores ||--o{ favorites : "favored_by"
    stores ||--o{ store_business_hours : "has"
    stores ||--o{ store_closed_days : "has"
    stores ||--o{ products : "lists"
    stores ||--o{ clearance_items : "lists"
    stores ||--o{ orders : "receives"
    stores ||--o{ reviews : "receives"

    products ||--o{ clearance_items : "becomes"

    clearance_items ||--o{ order_items : "ordered_as"

    orders ||--o{ order_items : "contains"
    orders ||--|| payments : "paid_by"
    orders ||--|| reviews : "reviewed_as"
    orders ||--o{ point_transactions : "earns_or_uses"
    payments ||--o{ refunds : "refunded_by"

    reviews ||--o{ review_images : "has"
    reviews ||--o| review_replies : "replied_with"
    reviews ||--o{ review_reports : "reported_via"

    coupons ||--o{ user_coupons : "issued_as"

    inquiries ||--o{ inquiry_replies : "answered_by"
```

> Mermaid 가 polymorphic 관계(예: `notifications.recipient_id` ← customers/sellers/admins) 는 표현 불가 → 본문/상세 파일에서 설명.

---

## Tables

도메인별 상세 파일은 **해당 도메인 코드 작업 시 작성**. 빈 파일을 미리 만들지 않음.

### Users
- `customers` — 소비자 회원
- `sellers` — 사장 (사업자 인증 + 관리자 승인 필요)
- `admins` — 운영자
- `addresses` — 소비자 주소지 (최대 3개, 알림 반경 기준)
- `favorites` — 소비자-매장 즐겨찾기 (M:N)

### Stores
- `stores` — 매장 (좌표 PostGIS)
- `store_categories` — 매장 카테고리 (베이커리/카페 등)
- `store_business_hours` — 요일별 영업시간
- `store_closed_days` — 정기/임시 휴무

### Products
- `products` — 매장 일반 상품 (정상가 메뉴, 대표 이미지 1장 `image_url`)
- `clearance_items` — 마감 임박 상품 (떨이, 별도 엔티티)

> "상품 카테고리 / 상품 해시태그 / 다중 상품 이미지" 는 본 ERD 에 두지 않는다 (#56 정정). 매장 카테고리(`store_categories`) 와 별개이고, 해시태그는 리뷰 도메인 작업 시점에 재정의.

### Orders
- `orders` — 주문 (당일 결제 = 당일 픽업, 상태 머신)
- `order_items` — 주문 항목 (clearance_item 기반)
- `payments` — 결제 (토스페이 `payment_key` 외부 식별자)
- `refunds` — 환불 (부분 환불 허용)

### Notifications
- `notifications` — 알림 기록 (polymorphic recipient)
- `push_tokens` — **FCM 토큰** (PWA 디바이스별 별도 row, 소비자·사장 대상)
- `notification_settings` — 소비자별 알림 설정 + 반경 km

### Reviews
- `reviews` — 별점 + 텍스트 (주문 1개당 리뷰 1개, 7일 이내 수정)
- `review_images` — 리뷰 사진 (최대 3장)
- `review_replies` — 사장 답글 (리뷰당 1개)
- `review_reports` — 리뷰 신고

### Benefits
- `points` — 사용자별 포인트 잔액 (캐시 + 마지막 사용 시각)
- `point_transactions` — 적립/사용/만료 내역
- `coupons` — 쿠폰 마스터 (관리자/이벤트 발급)
- `user_coupons` — 사용자에게 발급된 쿠폰 인스턴스

### Operations
- `announcements` — 공지사항
- `inquiries` — 고객센터 문의
- `inquiry_replies` — 문의 답변
- `settlements` — 사장별 주간 정산

---

## 주요 polymorphic / 모호한 관계 (FK 미설정)

| 컬럼 | 가능 참조 대상 | 처리 |
|---|---|---|
| `notifications.recipient_id` + `recipient_type` | customers, sellers, admins | 앱 레벨 무결성 |
| `push_tokens.owner_id` + `owner_type` | customers, sellers | 앱 레벨 무결성. 관리자는 푸시 미수신 |
| `review_reports.reporter_id` | customers (또는 admins) | 일단 customers 만 가정. 필요 시 reporter_type 추가 |

---

## 미정 사항 (구현 시점에 다시 보기)

- **환불 정책**: refunds 골격만. 정책 미정 ([product.md Pending Decisions](../product.md))
- **포인트 적립률·최소 사용 단위**: 컬럼은 있지만 값은 정책 결정 후
- **노쇼 누적 / 제재 강도**: 노쇼 처리 컬럼 미설계 (orders.status 에 흡수 vs 별도 테이블)
- **검색 기록 / 인기 키워드**: features.md 에는 있으나 ERD 미반영. 별도 도메인으로 추후 추가
