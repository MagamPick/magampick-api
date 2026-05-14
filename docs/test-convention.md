# Test Convention

Spring Boot 3.5 / Java 21 / JUnit 5 / Mockito / Testcontainers (PostGIS) 기준.

> 코드 변경 시 어떤 테스트를 어떻게 작성할지의 컨벤션. 작성 정책(§2) 은 도메인 코드 작업 시 항상 따른다.

---

## 1. 테스트 종류와 대상

| 종류 | 도구 | 범위 | 주 대상 |
|---|---|---|---|
| **단위** | JUnit 5 + Mockito | 메서드 1개 | Service / Mapper / Utility (Spring 안 띄움) |
| **슬라이스 — Controller** | `@WebMvcTest` + MockMvc | Controller 레이어 | URL 매핑, Validation, 응답 직렬화 |
| **슬라이스 — Repository** | `@DataJpaTest` + Testcontainers | Repository 레이어 | JPA 쿼리, 실제 SQL, 제약 |
| **통합** | `@SpringBootTest` + MockMvc + Testcontainers | 한 API 전체 흐름 | 핵심 비즈니스 흐름 (회원가입/주문/결제/환불) |
| **E2E** | `@SpringBootTest(RANDOM_PORT)` + `TestRestTemplate` | 여러 API 시나리오 | 거의 사용 안 함 |

---

## 2. 작성 정책 (B 강도)

### 🔴 필수 — 자동 작성

| 대상 | 테스트 종류 |
|---|---|
| **Service** (모든 public 메서드) | **단위 테스트** |

비즈니스 로직 = 버그 발생 1순위. ROI 가장 높음.

### 🟡 권장 — 자동 작성

| 대상 | 테스트 종류 |
|---|---|
| **Controller** (모든 endpoint) | **슬라이스 (`@WebMvcTest`)** |

검증 항목: 정상 응답, Validation 실패(`400`), 권한 실패(`403`), Not Found(`404`), envelope 포맷.

### 🟢 선택 — 명시 요청 시만

| 대상 | 테스트 종류 |
|---|---|
| **Repository — 커스텀 쿼리 / PostGIS 반경 검색** | 슬라이스 (`@DataJpaTest`) |
| **핵심 비즈니스 흐름** (회원가입 / 주문 / 결제 / 환불) | 통합 (`@SpringBootTest`) |
| **사용자 시나리오** (가입→로그인→주문 같은) | E2E |

### ⛔ 생략 — 시간 낭비

- 기본 CRUD Repository (Spring Data 가 검증된 코드)
- MapStruct 자동 생성 Mapper (Service 단위 테스트에서 자연스럽게 검증됨)
- 단순 Getter/Setter (Lombok 자동 생성)
- 모든 endpoint 에 통합 테스트 — 슬라이스 + 단위로 충분

---

## 3. 테스트 도구

| 용도 | 라이브러리 | 비고 |
|---|---|---|
| 프레임워크 | JUnit 5 | Spring Boot 기본 |
| Mock | Mockito | `@Mock`, `@InjectMocks`, `given().willReturn()` |
| Assertion | AssertJ | `assertThat(...).isEqualTo(...)` |
| Controller 슬라이스 | MockMvc | `mockMvc.perform(...)` |
| DB | Testcontainers PostGIS | `postgis/postgis:16-3.4-alpine` |
| 커버리지 | Jacoco | `./gradlew test jacocoTestReport` |

모두 `build.gradle` 에 이미 포함 (Mockito, AssertJ 는 `spring-boot-starter-test` 가 가져옴).

---

## 4. 네이밍 / 구조

### 클래스명
`{TargetClassName}Test`
- `StoreService` → `StoreServiceTest`
- `StoreController` → `StoreControllerTest`

### 메서드명 — **한국어 + 언더바**

```java
@Test
void 매장_등록_성공() { ... }

@Test
void 매장명_누락시_400_반환() { ... }

@Test
void 권한_없는_사용자가_매장_수정시_403() { ... }
```

이유: 가독성 ↑, 의도 명확, 한 줄 요약처럼 읽힘.

### 구조 — **given-when-then 주석**

```java
@Test
void 매장_등록_성공() {
    // given
    StoreCreateRequest request = StoreFixture.aCreateRequest();
    given(storeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

    // when
    StoreResponse response = storeService.create(request);

    // then
    assertThat(response.name()).isEqualTo(request.name());
    verify(storeRepository).save(any(Store.class));
}
```

3개 섹션을 주석으로 명시 → 의도 명확.

---

## 5. 단위 테스트 — Service

```java
@ExtendWith(MockitoExtension.class)
class StoreServiceTest {

    @Mock StoreRepository storeRepository;
    @Mock SellerRepository sellerRepository;
    @InjectMocks StoreService storeService;

    @Test
    void 매장_등록_성공() {
        // given
        Seller seller = SellerFixture.aSeller();
        StoreCreateRequest request = StoreFixture.aCreateRequest();
        given(sellerRepository.findById(any())).willReturn(Optional.of(seller));
        given(storeRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // when
        StoreResponse response = storeService.create(seller.getId(), request);

        // then
        assertThat(response.name()).isEqualTo(request.name());
        verify(storeRepository).save(any(Store.class));
    }

    @Test
    void 미인증_사장은_매장_등록_불가() {
        // given
        Seller pending = SellerFixture.aSellerWith(VerificationStatus.PENDING);
        given(sellerRepository.findById(any())).willReturn(Optional.of(pending));

        // when / then
        assertThatThrownBy(() -> storeService.create(pending.getId(), null))
            .isInstanceOf(BusinessException.class)
            .hasFieldOrPropertyWithValue("errorCode", SellerErrorCode.NOT_VERIFIED);
    }
}
```

규칙:
- `@ExtendWith(MockitoExtension.class)` — Spring 안 띄움
- 의존성은 `@Mock`, 테스트 대상은 `@InjectMocks`
- BDD 스타일 (`given().willReturn()`, `then().verify()`) 권장
- 예외 검증은 `assertThatThrownBy(...)` + `hasFieldOrPropertyWithValue` 로 errorCode 까지 확인

---

## 6. 슬라이스 — Controller (`@WebMvcTest`)

```java
@WebMvcTest(StoreController.class)
class StoreControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean StoreService storeService;
    @Autowired ObjectMapper objectMapper;

    @Test
    void 매장_등록_성공() throws Exception {
        // given
        given(storeService.create(any(), any())).willReturn(StoreFixture.aResponse());

        // when / then
        mockMvc.perform(post("/api/v1/seller/stores")
                .with(user("seller").roles("SELLER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(StoreFixture.aCreateRequest())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.name").value("동네빵집"));
    }

    @Test
    void 매장명_누락시_400() throws Exception {
        mockMvc.perform(post("/api/v1/seller/stores")
                .with(user("seller").roles("SELLER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_INPUT"));
    }
}
```

규칙:
- `@WebMvcTest(XController.class)` — 해당 컨트롤러만 로드
- `@MockBean` 으로 Service 대체
- envelope 포맷 (`$.success`, `$.data`, `$.error.code`) 까지 검증

---

## 7. 슬라이스 — Repository (`@DataJpaTest`)

**커스텀 쿼리만 작성**. 기본 CRUD 는 생략.

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingConfig.class)
class StoreRepositoryTest extends PostgresTestBase {

    @Autowired StoreRepository storeRepository;

    @Test
    void 반경_1km_내_매장만_조회된다() {
        // given
        Store near = storeRepository.save(StoreFixture.aStoreAt(37.498, 127.028));  // 강남역
        Store far  = storeRepository.save(StoreFixture.aStoreAt(35.158, 129.160));  // 부산

        Point origin = new GeometryFactory().createPoint(new Coordinate(127.027, 37.497));

        // when
        List<Store> result = storeRepository.findWithinRadius(origin, 1000);

        // then
        assertThat(result).extracting(Store::getId).containsExactly(near.getId());
    }
}
```

규칙:
- `@DataJpaTest` — JPA 레이어만 띄움
- `@AutoConfigureTestDatabase(replace = NONE)` — H2 자동 대체 비활성 (PostGIS 필요)
- `extends PostgresTestBase` — 공통 Testcontainers 설정 (§10)
- `@Import(JpaAuditingConfig.class)` — `@EnableJpaAuditing` 활성 필요한 경우

---

## 8. 통합 테스트 (`@SpringBootTest`)

핵심 비즈니스 흐름에만 작성. 모든 endpoint 에 작성하지 X.

대상 예시:
- 회원가입 (가입 → 인증 → 토큰 발급)
- 주문 생성 (재고 차감 → 결제 → 포인트 적립)
- 환불 (결제 취소 → 재고 복구 → 포인트 회수)
- 노쇼 처리

```java
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OrderCreateIntegrationTest extends PostgresTestBase {

    @Autowired MockMvc mockMvc;
    @Autowired CustomerRepository customerRepository;
    @Autowired ClearanceItemRepository itemRepository;
    @Autowired PointRepository pointRepository;

    @Test
    void 주문_생성시_재고_차감_및_포인트_적립() throws Exception {
        // given
        Customer customer = customerRepository.save(CustomerFixture.aCustomer());
        ClearanceItem item = itemRepository.save(ClearanceItemFixture.anItemWithQuantity(10));

        // when
        mockMvc.perform(post("/api/v1/orders")
                .with(user(customer.getId().toString()).roles("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"itemId\":%d,\"quantity\":2}".formatted(item.getId())))
            .andExpect(status().isCreated());

        // then — 재고 차감
        ClearanceItem updated = itemRepository.findById(item.getId()).orElseThrow();
        assertThat(updated.getRemainingQuantity()).isEqualTo(8);

        // then — 포인트 적립
        Point points = pointRepository.findById(customer.getId()).orElseThrow();
        assertThat(points.getBalance()).isPositive();
    }
}
```

규칙:
- `@SpringBootTest` + `@AutoConfigureMockMvc`
- `@Transactional` — 각 테스트 후 자동 롤백 (DB 깨끗하게 유지)
- `extends PostgresTestBase` — 공통 Testcontainers
- 여러 컴포넌트의 협업 + 트랜잭션 / 보안 / 이벤트까지 검증

---

## 9. Fixture 패턴

테스트마다 객체를 직접 생성하면 깨지기 쉬움. **빌더 클래스로 통일**.

### 위치

```
src/test/java/com/magampick/{domain}/fixture/
└── {Entity}Fixture.java
```

예: `src/test/java/com/magampick/store/fixture/StoreFixture.java`

### 패턴

```java
public class StoreFixture {

    public static Store aStore() {
        return aStore(SellerFixture.aSeller());
    }

    public static Store aStore(Seller seller) {
        return Store.builder()
            .seller(seller)
            .name("동네빵집")
            .address("서울시 강남구 ...")
            .location(point(127.028, 37.498))
            .status(StoreStatus.APPROVED)
            .build();
    }

    public static Store aStoreAt(double lat, double lng) {
        return aStore().toBuilder()
            .location(point(lng, lat))
            .build();
    }

    public static StoreCreateRequest aCreateRequest() {
        return new StoreCreateRequest("동네빵집", "서울시 ...");
    }

    public static StoreResponse aResponse() {
        return new StoreResponse(1L, "동네빵집", "서울시 ...");
    }

    private static Point point(double lng, double lat) {
        return new GeometryFactory().createPoint(new Coordinate(lng, lat));
    }
}
```

명명 규칙:
- `a{Entity}()` — 기본 인스턴스
- `a{Entity}With{Field}(...)` — 특정 필드 변경 변형
- `a{Entity}Request()` / `a{Entity}Response()` — DTO 변형
- 정적 메서드만 (인스턴스화 X)

---

## 10. Testcontainers — PostGIS 공통 베이스

DB 슬라이스 / 통합 테스트가 공통 사용. **컨테이너 1번만 띄우기 (재사용)** 위해 베이스 클래스 사용.

```java
// src/test/java/com/magampick/global/PostgresTestBase.java
@Testcontainers
public abstract class PostgresTestBase {

    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>("postgis/postgis:16-3.4-alpine")
            .withDatabaseName("magampick_test")
            .withUsername("test")
            .withPassword("test")
            .withReuse(true);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void registerProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
```

- `withReuse(true)` 로 컨테이너 재사용 → 테스트 속도 ↑
  - 활성화: `~/.testcontainers.properties` 에 `testcontainers.reuse.enable=true` 필요
- `static` 으로 JVM 당 1번만 시작
- Flyway 마이그레이션은 Spring Boot 가 자동 실행 → 스키마 자동 생성

---

## 11. 커버리지 (Jacoco)

- 측정: `./gradlew test jacocoTestReport`
- 리포트: `build/reports/jacoco/test/html/index.html`
- **강제 임계값 없음** (참고용)
- 단, **Service 레이어 커버리지 낮으면 위험 신호** — 작성 누락 의심

---

## 12. 빠른 참조

| 대상 | 종류 | 어노테이션 |
|---|---|---|
| Service | 단위 | `@ExtendWith(MockitoExtension.class)` |
| Mapper / Utility | 단위 | (없음) — `new` 로 직접 |
| Controller | 슬라이스 | `@WebMvcTest(XController.class)` |
| Repository (커스텀 쿼리) | 슬라이스 | `@DataJpaTest` + `extends PostgresTestBase` |
| 핵심 흐름 | 통합 | `@SpringBootTest @AutoConfigureMockMvc @Transactional` + `extends PostgresTestBase` |
