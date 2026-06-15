# seller_notification_settings

사장 알림 수신 설정. 가입 시 기본값으로 생성되며, 항목별 on/off 를 관리한다.

## 컬럼

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, IDENTITY | surrogate PK |
| `seller_id` | `BIGINT` | NOT NULL, UNIQUE | `sellers.id` FK |
| `new_order` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 새 주문 알림 수신 여부 |
| `order_cancel` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 주문 취소 알림 수신 여부 |
| `refund_request` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 환불 요청 알림 수신 여부 |
| `new_review` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 새 리뷰 알림 수신 여부 |
| `notice` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 공지 알림 수신 여부 |
| `marketing` | `BOOLEAN` | NOT NULL, DEFAULT FALSE | 마케팅 알림 수신 여부 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 갱신 시각 |

## 인덱스 / 제약

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `PRIMARY KEY` | `id` | surrogate PK |
| `UNIQUE` | `seller_id` | 사장당 1행 |
| `fk_sns_seller` | `seller_id` → `sellers(id)` | 참조 무결성 |

## 관계

- **sellers** — `seller_id` FK. 사장 1명당 1행 (UNIQUE).

## 비즈니스 규칙

- 가입 시 자동 생성 (`SellerNotificationSettingService.createDefault`).
- 항목별로 `PATCH /api/v1/seller/notification-settings/{key}` 로 변경.
- 기본값: 마케팅 = OFF, 그 외 = ON.
