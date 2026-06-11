# customer_locations

소비자 현재 위치 테이블. customers 1:1 종속 (customer_id 가 PK = natural key).

프론트엔드가 주기적으로 `PUT /api/v1/customers/me/location` 을 호출해 덮어쓴다. 떨이 등록·마감임박 알림 시 `location_updated_at >= now - 1시간` 인 소비자를 ② 현재위치 반경 3km 알림 대상으로 포함한다 (`policy.md §알림` 참고).

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| customer_id | BIGINT | N | PK, FK → customers(id) ON DELETE CASCADE | 소비자 식별자 (1:1 PK) |
| location | GEOGRAPHY(POINT, 4326) | N |  | WGS84 현재 위경도 좌표 |
| location_updated_at | TIMESTAMP | N |  | 마지막 위치 갱신 시각 (KST). 신선도 판단 기준. |

## 인덱스

- `customer_locations_pkey` (`customer_id`) — PK
- `gix_customer_locations_location` GIST (`location`) — ST_DWithin 반경 검색용

## 제약

- `fk_customer_locations_customer` FOREIGN KEY (`customer_id`) REFERENCES customers(`id`) ON DELETE CASCADE
  - customers 가 hard delete 될 때 자동 정리.

## 관계

- `customer_locations.customer_id -> customers.id` (1:1, ON DELETE CASCADE)
- 단방향 참조 — `Customer` 엔티티에 컬렉션 없음. `CustomerLocation` 은 독립 엔티티.

## 정책 / 운영 메모

- **신선도 기준 (1시간)**: `location_updated_at >= now - 1시간` 이면 알림 대상 포함. 초과하거나 row 없으면 제외. 신선도 값은 `NEARBY_METERS`, `KST` 와 함께 `ClearanceNotificationService` 상수로 관리.
- **알림 우선순위**: ① 즐겨찾기(favoriteStore) → ② 현재위치(nearbyDeal) → ③ 기본 주소지(nearbyDeal). `resolveTargets` 에서 dedup.
- **토글**: `nearbyDeal` settingKey 재사용 (③ 주소지 와 동일). 별도 토글 없음.
- **upsert**: row 없으면 INSERT, 있으면 `update(location, now)` 호출. 1소비자 1row 유지.
- **Hard delete**: `deleted_at` 없음. customers ON DELETE CASCADE 로 정리.
