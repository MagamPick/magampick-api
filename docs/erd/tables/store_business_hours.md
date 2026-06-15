# store_business_hours

매장 요일별 영업시간. **영업 요일만 row 저장** — 휴무 요일은 row 자체가 없음 (노션: 영업시간 설정).

매장 영업 상태 관리(`stores.operation_status`)의 `OPEN` 전환 조건(오늘 요일이 영업 요일인지)으로도 사용한다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | row 식별자 |
| store_id | BIGINT | N | FK → stores.id | 매장 |
| day_of_week | VARCHAR(10) | N | CHECK | `MONDAY` ~ `SUNDAY` (Java `DayOfWeek`) |
| open_time | TIME | N | CHECK `<close_time` | 시작 시각 |
| close_time | TIME | N | CHECK `>open_time` | 종료 시각 (같은 날 내, 자정 넘김 미지원) |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `store_business_hours_pkey` (`id`)
- `uq_sbh_store_dow` UNIQUE (`store_id`, `day_of_week`)
- `idx_sbh_store_id` (`store_id`)

## 제약

- `fk_sbh_store` FK `store_id → stores.id`
- `uq_sbh_store_dow` UNIQUE (`store_id`, `day_of_week`) — 같은 매장의 같은 요일 중복 row 차단
- `chk_sbh_day_of_week` CHECK `day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')`
- `chk_sbh_time_range` CHECK `open_time < close_time` — 자정 넘김 미지원

## 관계

- `store_business_hours.store_id` → `stores.id` (N:1)

## 정책 결정 (노션: 영업시간 설정 / 매장 영업 상태 관리)

- **영업 요일만 row 저장**: 휴무 요일 = row 없음. 영업 요일 0개 매장은 모든 요일이 휴무 = `OPEN` 전환 항상 비활성
- **자정 넘김 불가**: `open_time < close_time` 동일 날 내. 24시간 영업 / 자정 넘기는 영업시간은 MVP X
- **브레이크타임 없음**: 한 요일 = 한 row. 브레이크타임은 MVP X (백로그)
- **OPEN 중 오늘 요일 변경 제한**: `stores.operation_status = OPEN` 일 때 오늘 요일 row 수정·삭제 차단 (`TODAY_BUSINESS_HOURS_LOCKED`) — 영업시간 설정 명세 소관
