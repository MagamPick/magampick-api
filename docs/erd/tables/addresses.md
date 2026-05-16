# addresses

소비자 주소지 테이블. 소비자 1:N 종속. 최대 3개 보유 (앱 레벨 제약).

알림 발송 fallback 채널 (`policy.md §알림` 3순위 — 설정 주소) 및 미래 탐색/매장 추천 기준의 데이터 공급용. 본 테이블은 데이터 저장만 담당하며, 알림 발송 트리거 / 탐색 활용은 별도 도메인 (계층 4 / 계층 8-A) 에서 SELECT 만 한다.

좌표 변환은 클라이언트 위젯 (카카오 우편번호 SDK 등) 이 처리하며, 서버는 위경도를 받아 PostGIS GEOGRAPHY 로 저장.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 주소지 식별자 |
| customer_id | BIGINT | N | FK → customers(id) ON DELETE CASCADE | 소유 소비자 |
| label | VARCHAR(20) | N | CHECK 공백 금지 | 사용자 지정 라벨 (자유 텍스트, enum 아님 — "집"/"회사"/"엄마집" 등) |
| road_address | VARCHAR(200) | N | CHECK 공백 금지 | 도로명 주소 (위젯 응답) |
| jibun_address | VARCHAR(200) | Y |  | 지번 주소 (위젯 응답, 없을 수 있음) |
| detail_address | VARCHAR(100) | Y |  | 상세 주소 (사용자 직접 입력 — 동/호수 등) |
| zonecode | VARCHAR(10) | Y | CHECK `^[0-9]{5}$` 또는 NULL | 우편번호 5자리 |
| location | GEOGRAPHY(POINT, 4326) | N |  | WGS84 위경도 좌표 |
| is_default | BOOLEAN | N | DEFAULT FALSE | 기본 주소지 여부 |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `addresses_pkey` (`id`)
- `idx_addresses_customer_id` (`customer_id`) — 소비자별 목록 조회 (FK 조회 패턴)
- `uq_addresses_customer_default` UNIQUE (`customer_id`) WHERE `is_default = TRUE` — customer 당 default 정확히 0 또는 1 강제 (부분 UNIQUE 인덱스)
- `gix_addresses_location` GIST (`location`) — 미래 반경 검색 (계층 4 / 8-A) 대비

## 제약

- `fk_addresses_customer` FOREIGN KEY (`customer_id`) REFERENCES customers(`id`) ON DELETE CASCADE
  - customers 가 hard delete 될 때 (30일 유예 후) 자동 정리. customers soft delete (`deleted_at`) 만으로는 CASCADE 발동하지 않음.
- `chk_addresses_label_not_blank` CHECK `length(trim(label)) >= 1`
- `chk_addresses_road_address_not_blank` CHECK `length(trim(road_address)) >= 1`
- `chk_addresses_zonecode_format` CHECK `zonecode IS NULL OR zonecode ~ '^[0-9]{5}$'`

## 관계

- `addresses.customer_id -> customers.id` (N:1, ON DELETE CASCADE)
- 단방향 `@ManyToOne(fetch = LAZY)`. `Customer` 쪽에 `@OneToMany` 컬렉션 없음 (양방향 lazy loading 위험 회피).

## 정책 / 운영 메모

- **보유 한도 (≤ 3)** 는 앱 레벨에서만 강제 (Service 의 `count + check` → `ADDRESS_LIMIT_EXCEEDED` 400). DB 트리거 / advisory lock 미도입 — 졸업 단계 race 빈도 무시 가능. 출시 시점에 트래픽 보고 검토.
- **default 정확히 1개** 는 부분 UNIQUE 인덱스로 DB 가 강제. 0개도 허용 (보유 0개 / 마지막 1개 삭제 후 시점).
- **default 변경 / 자동 승계** 는 단일 `@Transactional` 안에서 처리. JPA flush 순서가 INSERT → UPDATE → DELETE 라 부분 UNIQUE 위반 위험이 있어 `addressRepository.flush()` 로 명시적 순서 제어.
- **입력 UX** = 주소 검색 위젯만 지원 (카카오 우편번호 SDK / 네이버 검색 등). 지도 좌표 픽 / 임시 위치 픽은 본 테이블 out of scope — 필요해지면 별도 이슈 (별도 테이블 가능성).
- **Hard delete** — `deleted_at` 컬럼 없음 (erd/overview.md "Soft Delete" 규칙: 종속 데이터는 hard delete).
