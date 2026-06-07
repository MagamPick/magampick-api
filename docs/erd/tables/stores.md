# stores

매장 테이블. 사장이 정보를 입력하면 국세청 검증 + 주소 지오코딩 + 대표 사진 업로드 후 자동 승인으로 즉시 생성된다.

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| id | BIGINT | N | PK, IDENTITY | 매장 식별자 |
| seller_id | BIGINT | N | FK → sellers.id | 소유 사장 |
| business_number | VARCHAR(10) | N |  | 사업자 번호 (하이픈 제거 10자리, 매장별, UNIQUE 미적용) |
| representative_name | VARCHAR(30) | N |  | 대표자 실명 (사업자등록증 기재, 국세청 검증 시 사용한 값) |
| open_date | DATE | N |  | 개업일자 (사업자등록증 기재, 국세청 검증 시 사용한 값) |
| name | VARCHAR(50) | N |  | 매장명 |
| road_address | VARCHAR(200) | N |  | 도로명 주소 |
| jibun_address | VARCHAR(200) | Y |  | 지번 주소 |
| detail_address | VARCHAR(100) | Y |  | 상세 주소 |
| zonecode | VARCHAR(10) | N |  | 우편번호 5자리 |
| location | GEOGRAPHY(POINT,4326) | N |  | PostGIS 위경도 (서버 지오코딩 결과). GIST 인덱스 |
| phone | VARCHAR(20) | N |  | 매장 전화번호 |
| description | VARCHAR(500) | Y |  | 매장 소개 |
| image_url | VARCHAR(500) | Y |  | 대표 사진 URL (사장 가입의 첫 매장 사진은 선택) |
| operation_status | VARCHAR(15) | N | CHECK | `OPEN`, `BREAK`, `CLOSED_TODAY`. 등록 직후 `CLOSED_TODAY` |
| deleted_at | TIMESTAMP | Y |  | 소프트 삭제 시각 |
| created_at | TIMESTAMP | N |  | 생성 시각 |
| updated_at | TIMESTAMP | N |  | 수정 시각 |

## 인덱스

- `stores_pkey` (`id`)
- `idx_stores_seller_id` (`seller_id`)
- `idx_stores_operation_status` (`operation_status`)
- `idx_stores_location` GIST (`location`)

## 제약

- `fk_stores_seller` FK `seller_id → sellers.id`
- `chk_stores_operation_status` CHECK `operation_status IN ('OPEN', 'BREAK', 'CLOSED_TODAY')`

## 관계

- `stores.seller_id` → `sellers.id` (N:1)

## 정책 결정 (노션 "매장 등록 신청" 재설계)

- **자동 승인**: 국세청 검증 + 지오코딩 + 이미지 업로드 성공 시 즉시 생성. 관리자 승인/반려 흐름 없음 (#48 의 `status`/`rejection_reason` 제거)
- **사업자 번호**: 매장별 (`stores.business_number`). 입력은 하이픈 포함 가능, 저장은 숫자 10자리. **중복 허용** (UNIQUE 미적용 — 사칭 방지는 본인확인 모듈 백로그)
- **외부 연동**: 국세청 검증 / 지오코딩 = Mock 인터페이스 (실연동 = 공공데이터포털·카카오 로컬, 후속 작업). 이미지 = **OCI Object Storage** 연동 완료 — local 은 `~/.oci` API Key, dev/prod 는 Instance Principal (`StorageService` 의 prod 구현체 `OciStorageService`)
- **이미지**: 대표 1장. 5MB / jpg·png·webp. 로그인 사장의 독립 매장 등록은 필수, 사장 가입 과정의 첫 매장 사진은 선택
- **노출 제어**: 등록 직후 `operation_status=CLOSED_TODAY` 라 소비자 노출 X — 사장이 영업시간 입력 후 [영업 시작] 으로 `OPEN` 전환해야 운영 시작. 전이 그래프 / 노출 룰 상세는 노션 "매장 영업 상태 관리" 소관
- **진입 경로**: 경로 A(사장 가입 wizard 통합) / 경로 B(로그인 사장 독립 등록)
