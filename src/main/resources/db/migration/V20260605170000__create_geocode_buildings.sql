-- 자체 지오코딩 참조 데이터 (ADR-002 / ADR-003).
-- 행정안전부 위치정보요약DB(entrc_{sido}.txt, 서울+경기) 1회 적재.
-- 정방향(주소→좌표) = 도로명 자연키 정확 매칭, 역방향(좌표→도로명 라벨) = PostGIS 최근접(KNN).
-- 좌표는 적재 시 ST_Transform(5179 GRS80 UTM-K → 4326 WGS84)로 변환해 저장 (런타임 변환 X).
-- PostGIS 확장은 addresses 마이그레이션 시점에 이미 활성 → 별도 CREATE EXTENSION 불필요.

CREATE TABLE geocode_buildings (
    -- 자연키: 도로명코드(시군구코드5 + 도로명번호7) + 지하여부 + 건물본번 + 건물부번.
    -- 위치정보요약DB 검증 결과 이 4-튜플이 좌표상 유일 (서울 충돌 0 / 경기 완전중복 1행은 적재 시 흡수).
    road_name_code   VARCHAR(12)            NOT NULL,
    underground      BOOLEAN                NOT NULL,
    building_main_no INTEGER                NOT NULL,
    building_sub_no  INTEGER                NOT NULL,
    -- 역지오코딩 라벨용 합성 도로명주소 ("{시도명} {시군구명} {도로명} [지하 ]{본번}[-{부번}]")
    road_address     VARCHAR(200)           NOT NULL,
    location         GEOGRAPHY(POINT, 4326) NOT NULL,
    CONSTRAINT pk_geocode_buildings
        PRIMARY KEY (road_name_code, underground, building_main_no, building_sub_no)
);

-- 역지오코딩 최근접(location <-> point) 가속용
CREATE INDEX gix_geocode_buildings_location ON geocode_buildings USING GIST (location);
