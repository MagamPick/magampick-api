# geocode_buildings

자체 지오코딩 참조 데이터 (ADR-002 / ADR-003). 행정안전부 **위치정보요약DB**(`entrc_{sido}.txt`)에서 **서울 + 경기** 분을 1회 적재한다. FK 무관계 standalone 테이블이며, 도메인 서비스(매장 등록 등)가 SELECT 만 한다.

- **정방향** (주소 → 좌표): 다음 우편번호 위젯 결과로 도로명 자연키를 조립해 정확 매칭. 매칭 실패 → 매장 등록 거부(`ADDRESS_GEOCODING_FAILED`).
- **역방향** (좌표 → 도로명 라벨): PostGIS 최근접(`location <-> point`). GPS 주소지 라벨 채움용 (주소 측 배선은 후속).
- **갱신 없음**: MVP 1회 적재 고정 (변동분 `entrc_mod.txt` 주기 갱신은 out of scope).

## 컬럼

| 컬럼 | 타입 | NULL | 제약 | 설명 |
|---|---|---|---|---|
| road_name_code | VARCHAR(12) | N | PK (1/4) | 도로명코드 = 시군구코드(5) + 도로명번호(7) |
| underground | BOOLEAN | N | PK (2/4) | 지하여부 (위치정보요약DB 지하여부 0/1) |
| building_main_no | INTEGER | N | PK (3/4) | 건물본번 |
| building_sub_no | INTEGER | N | PK (4/4) | 건물부번 (없으면 0) |
| road_address | VARCHAR(200) | N |  | 역지오코딩 라벨용 합성 도로명주소 (`{시도명} {시군구명} {도로명} [지하 ]{본번}[-{부번}]`) |
| location | GEOGRAPHY(POINT, 4326) | N |  | WGS84 좌표 (적재 시 5179 → 4326 변환) |

## 인덱스

- `pk_geocode_buildings` PRIMARY KEY (`road_name_code`, `underground`, `building_main_no`, `building_sub_no`) — 정방향 자연키 정확 매칭
- `gix_geocode_buildings_location` GIST (`location`) — 역방향 최근접(KNN, `<->`) 가속

## 제약

- 위 4-튜플 복합 PK. 위치정보요약DB 실검증상 이 자연키가 좌표상 유일 (서울 충돌 0 / 경기 완전중복 1행은 적재 시 `ON CONFLICT DO NOTHING` 으로 흡수). 법정동코드(레이아웃 PK5)는 매칭에 불필요해 컬럼에서 제외.
- 참조 데이터라 `created_at` / `updated_at`(BaseEntity) 없음.

## 관계

- FK 없음 (standalone 참조 테이블). 도메인 엔티티와 조인하지 않고 서비스가 조회만 한다.

## 적재 / 운영 메모

- **데이터 스펙**: 인코딩 CP949, 구분자 `|`, 18컬럼. 좌표계 **GRS80 UTM-K = EPSG:5179**. 좌표 누락(비공개/공개제한 건물) 행은 location NOT NULL 이라 적재 제외.
- **적재기**: `com.magampick.geocode.loader.GeocodeDataLoader` (`@Profile("geocode-load")` CommandLineRunner). 실행 = `--spring.profiles.active=<env>,geocode-load --geocode.load.dir="...\202604_위치정보요약DB_전체분"`. 좌표는 적재 SQL 의 `ST_Transform(ST_SetSRID(ST_MakePoint(x,y),5179),4326)` 로 변환.
- **검증**: 청운동 자하문로 94 (`road_name_code='111103100012'`, 본번 94, 부번 0) → 약 (lat 37.585, lng 126.968). 5174 로 잘못 변환 시 수백 m~수 km 어긋나므로 즉시 감지.
- **데이터 파일은 레포에 커밋하지 않는다** (수백 MB) — 적재기 코드만 산출물. PostGIS `spatial_ref_sys` 에 5179 가 있어야 하며, postgis 확장 설치 시 기본 포함.
