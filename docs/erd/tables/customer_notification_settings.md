# customer_notification_settings

소비자 알림 수신 설정. 가입 시 기본값으로 생성되며, 항목별 on/off 를 관리한다.

## 컬럼

| 컬럼 | 타입 | 제약 | 설명 |
|---|---|---|---|
| `id` | `BIGINT` | PK, IDENTITY | surrogate PK |
| `customer_id` | `BIGINT` | NOT NULL, UNIQUE | `customers.id` FK |
| `nearby_deal` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 주변 마감 임박 알림 수신 여부 |
| `favorite_store` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 즐겨찾기 매장 알림 수신 여부 |
| `order_refund` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 주문·환불 알림 수신 여부 |
| `review_reply` | `BOOLEAN` | NOT NULL, DEFAULT TRUE | 리뷰 답글 알림 수신 여부 |
| `event_benefit` | `BOOLEAN` | NOT NULL, DEFAULT FALSE | 이벤트·혜택 알림 수신 여부 |
| `marketing` | `BOOLEAN` | NOT NULL, DEFAULT FALSE | 마케팅 알림 수신 여부 |
| `created_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 생성 시각 |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL, DEFAULT CURRENT_TIMESTAMP | 갱신 시각 |

## 인덱스 / 제약

| 이름 | 컬럼 | 용도 |
|---|---|---|
| `PRIMARY KEY` | `id` | surrogate PK |
| `UNIQUE` | `customer_id` | 소비자당 1행 |
| `fk_cns_customer` | `customer_id` → `customers(id)` | 참조 무결성 |

## 관계

- **customers** — `customer_id` FK. 소비자 1명당 1행 (UNIQUE).

## 비즈니스 규칙

- 가입 시 자동 생성 (`CustomerNotificationSettingService.createDefault`).
- 항목별로 `PATCH /api/v1/customers/me/notification-settings/{key}` 로 변경.
- 기본값: 마케팅·이벤트 = OFF, 그 외 = ON.
