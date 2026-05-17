---
name: spec
description: 옵트인 handoff 도구. GitHub Issue 기반 구현 명세 작성. 정책 결정 + API 계약 + 도메인 특수 동작을 docs/specs/ 에 저장. mechanical detail (Swagger / 패키지 / 트랜잭션 / 로깅 / Test Cases 열거 등) 은 convention 문서가 single source — spec 에 적지 않는다. 자동 워크플로우엔 포함 안 됨 — handoff 시나리오 (다른 세션 / 모델 / 외주 / 사전 리뷰) 에서만 사용자가 명시 호출.
---

# /spec — 구현 명세 작성 (옵트인 handoff 도구)

**옵트인 도구** — 자동 워크플로우 (`/issue` → `/impl` → 머지) 에 포함되지 않는다. handoff 가 필요한 시나리오에서 사용자가 명시 호출. `/issue` 로 결정된 정책 / scope 를 받아, **정책 결정 + API 계약 + 도메인 특수 동작** 만 spec 파일로 박는다. mechanical detail 은 convention 문서가 끌고 간다 — spec 은 그것을 *전제* 하고 차이만 적는다 (§4 §0 "Don't write" 리스트 참조).

> 임의 결정 금지 ([CLAUDE.md 의사결정 룰](../../../CLAUDE.md)). 정책 / scope 미정 발견 시 → `/issue` 로 돌아가 결정 후 재호출.

## 언제 쓰나 (호출 가이드)

기본 흐름은 `/issue` → `/impl` (plan mode → 코드 → 머지). spec 은 다음 케이스에서만 옵트인:

- **다른 세션 / 모델 / 에이전트로 위임** — Opus 가 결정한 정책을 Sonnet 또는 별도 Codex / Claude 세션에서 구현. 컨텍스트 다리가 필요한 경우
- **외주 / 다른 개발자 핸드오프** — 다른 사람이 구현할 예정. spec 이 사양서 역할
- **다중 stakeholder 사전 리뷰** — 구현 전 명세를 여러 관계자가 검토해야 하는 경우
- **병렬 에이전트 구현** — 같은 spec 으로 여러 모듈을 병렬 구현
- **무거운 정책 영역** — 결제 / 정산 / 환불 / 인증처럼 정책이 복잡하고 향후 다른 기능이 spec 자체를 참조해야 하는 경우 (단, 일반적으로 이런 정책은 `docs/policy.md` 갱신으로 더 잘 해결됨)

위에 해당 안 되면 `/impl` 의 plan mode 가 같은 역할을 in-session 휘발성으로 함 — `/spec` 호출 안 함.

## 입력
- `{이슈번호}` — GitHub Issue 번호 (필수, 예: `/spec 12`)
- 슬러그는 이슈 제목에서 자동 추출 ([`glossary.md`](../../../docs/glossary.md) 영문 매핑 사용)

## 흐름

### 1. 이슈 로드
```powershell
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
& $gh issue view {N} --repo MagamPick/magampick-api --json number,title,body,labels
```

가져온 이슈에서:
- **title** → 기능명 추출 (`✨ feat: ` / `🐛 fix: ` 등 prefix 제거)
- **body** → 4섹션 파싱 (Context / Scope / 핵심 정책 결정 / Business Logic 큰 그림)
- **labels** → type 라벨 + domain 라벨

**Type 가드** — labels 의 type 라벨 확인:
- `feat` / `fix` → 계속 진행 (handoff 시나리오에서 spec 적합)
- `refactor` / `docs` / `chore` → "`/spec` 부적합. 바로 `/impl {N}` 호출 권장" 안내, **중단** (워크플로우 분기는 [AGENTS.md §"워크플로우"](../../../AGENTS.md) 참조)

### 2. 작업 위치 확인 (슬롯 가드)

`/spec` 은 이슈 #{N} 의 브랜치가 attach 된 **슬롯 안에서** 실행되어야 한다. 정상 흐름은 `/issue` 가 슬롯에 attach 해둔다 — 이 단계는 위치 검증 + 누락 시 fallback.

**현재 위치 판별** (`git branch --show-current` / `git worktree list`):
- 현재 브랜치가 `feat/{N}-*` (이슈 type prefix) → 슬롯 안에 있음. **그대로 진행**
- `develop` / `main` (= 메인 디렉터리) 인 경우:
  - 이슈 #{N} 의 브랜치가 어느 슬롯에 attach 돼 있나? (`git worktree list` 에서 `feat/{N}-*` 검색)
    - **있으면** → 그 슬롯 경로 안내 + "그 디렉터리에서 에이전트 띄워 `/spec {N}` 재실행" → **중단**
    - **없으면** (fallback — `/issue` 안 거치고 GitHub 에서 수동 생성한 이슈 등) → 아래 부트스트랩 후 안내 → **중단**

**fallback 부트스트랩** (브랜치가 어느 슬롯에도 안 잡혀있을 때만):
1. 슬러그 추출 — 이슈 제목 type prefix 제거 (`^[이모지] [type]: ` 패턴) → 남은 한국어 기능명을 [`glossary.md`](../../../docs/glossary.md) 영문 매핑으로 kebab-case 변환 (예: `✨ feat: 매장 등록 신청` → `store-registration`). glossary 미정 용어는 사용자에게 옵션 제시 + 확정.
2. **빈 슬롯 찾기** — `git worktree list` 에서 `(detached HEAD)` 표시된 슬롯. 기본 슬롯 풀은 `magampick-api-wt1/wt2/wt3` ([AGENTS.md §"병렬 운영"](../../../AGENTS.md)). 모두 점유 중이면 사용자에게 슬롯 정리 / 임시 슬롯 추가 여부 확인 후 중단.
3. 브랜치 생성 + 슬롯 attach:
   ```powershell
   $gh = 'C:\Program Files\GitHub CLI\gh.exe'
   & $gh issue develop {N} --repo MagamPick/magampick-api --base develop --name "feat/{N}-{슬러그}"
   git -C ../magampick-api-wtX switch "feat/{N}-{슬러그}"
   ```
   - type 이 feat 가 아니면 prefix 조정 (`fix/`, `refactor/`, `docs/` 등)
4. 사용자에게 슬롯 경로 안내 + "그 디렉터리에서 에이전트 띄워 `/spec {N}` 재실행" → **중단**

> **Claude Code 한정 편의**: relaunch 대신 `EnterWorktree` 로 슬롯 진입 후 이어가도 된다 (세션 앵커 이동). Codex 엔 없는 기능이라 canonical 은 relaunch.
> 이후 `/spec` 의 spec 저장 + `/impl` 전부 이 슬롯 안에서 진행 — 메인 디렉터리 (`develop`/`main`) 에서 작업 금지.

### 3. 사전 점검 (Read only)
- 이슈 본문의 미정 사항 있나? → 있으면 **`/issue` 로 돌아가 결정 후 재호출** 안내, **중단**
- [features.md](../../../docs/features.md) / [policy.md](../../../docs/policy.md) / [glossary.md](../../../docs/glossary.md) / [erd/overview.md](../../../docs/erd/overview.md) — 컨텍스트 확인
- [coding-convention.md](../../../docs/coding-convention.md) / [api-convention.md](../../../docs/api-convention.md) / [auth.md](../../../docs/auth.md) / [test-convention.md](../../../docs/test-convention.md) — 작성 시 따를 룰

### 4. 7섹션 본문 작성 (대화)

**섹션마다 채우고 사용자 검토 → 다음 섹션** 흐름. `/issue` 와 일관.

#### 0. "Don't write" 리스트 — convention 위임 영역 (필수 원칙)

spec 은 *정책 결정* + *API 계약* + *도메인 특수 동작* 만 담는다. 다음 항목들은 convention 문서가 single source 이므로 **spec 에 적지 않는다**:

| 항목 | Single source |
|---|---|
| Swagger 어노테이션 본문 (`@Tag` / `@Operation` / `@Schema` / `@ApiResponse` / `@Parameter`) | [`api-convention.md`](../../../docs/api-convention.md) §12 |
| 패키지 / 파일 경로 / 레이어 분리 | [`coding-convention.md`](../../../docs/coding-convention.md) §1~2 |
| Entity / Builder / `@Table` / 비즈니스 메서드 패턴 | [`coding-convention.md`](../../../docs/coding-convention.md) §3 |
| `@Transactional` 위치 (클래스 단 readOnly / 메서드 단 override) | [`coding-convention.md`](../../../docs/coding-convention.md) §2 |
| MapStruct / Lombok / Builder 사용 결정 | [`coding-convention.md`](../../../docs/coding-convention.md) §3, §8 |
| 예외 / `BaseErrorCode` / 도메인별 ErrorCode 분리 위치 | [`coding-convention.md`](../../../docs/coding-convention.md) §7 |
| 로그 포맷 문자열 / 레벨 | [`coding-convention.md`](../../../docs/coding-convention.md) §10 |
| Processing Flow 의 표준 흐름 (JWT 추출 → repository.findById → 404 → dirty checking → Mapper) | 별도 설명 X — 표준 흐름은 convention + spec 의 API 표 / Validation Rules 로 도출 |
| Test Cases 의 case 단위 enumeration | [`test-convention.md`](../../../docs/test-convention.md) — /impl 이 API 표 + Edge Cases 보고 도출 |
| 마이그레이션 형식 / Enum CHECK / Point 인덱스 / KST | [`erd/overview.md`](../../../docs/erd/overview.md) |
| 인증 / 인가 / 본인 리소스 접근 매처 | [`auth.md`](../../../docs/auth.md) |
| self-evident edge case (같은 값으로 갱신 / null body / 멱등성 등) | 적지 않는다 — 의미 있는 정책성 edge case 만 |

위 항목 중 **convention 에서 벗어나는 결정** (예: 신규 SecurityConfig 매처 추가, 도메인 특수 동시성 처리, 표준 흐름 안 따르는 분산 락) 만 §7 Implementation Notes 에 적는다.

> 판단 기준: "이 줄을 빼도 /impl 이 convention 만 보고 같은 코드를 짤 수 있는가?" Yes → 빼라.

#### 1. Context (필수)
- 이슈 Context 를 **그대로 복사** (다음 세션 / `/impl` 호출 시 spec 만 읽어도 컨텍스트 파악 가능하게)
- 이슈 링크는 맨 위에 한 줄 (예: `> 이슈: #12`)

#### 2. Scope (필수)
- 이슈 Scope (In Scope / Out of Scope) 를 **그대로 복사**

#### 3. API Specification (필수)
엔드포인트별로 반복:

```markdown
### {METHOD} {PATH}

**Description**: 짧은 설명
**Authentication**: 인증 / 권한 요구 (auth.md 참조)

**Path Parameters** (해당 시)
| 파라미터 | 타입 | 설명 |

**Query Parameters** (해당 시)
| 파라미터 | 타입 | 필수 | 설명 |

**Request Body** ({DtoName})
| 필드 | 타입 | 제약 | 설명 |

**Response** - {StatusCode} ({DtoName})
| 필드 | 타입 | 설명 |

**Error Responses**
| 상태 | 에러 코드 | 상황 |
```

룰:
- URL / 메서드 / 상태 코드 — [api-convention.md](../../../docs/api-convention.md) 따름
- 응답은 `ApiResponse<T>` envelope 자동 적용 (api-convention §3) — payload 만 명시
- 페이지네이션이면 `PageResponse<T>` / 무한 스크롤은 `SliceResponse<T>`
- 에러 코드 — [coding-convention.md](../../../docs/coding-convention.md) 의 `BaseErrorCode` 패턴
- Swagger 어노테이션 부착 위치 / 본문 텍스트 / 예시는 적지 않는다 → [api-convention.md](../../../docs/api-convention.md) §12 가 single source. spec 의 **필드 / 제약 / 에러 표** 가 충분히 정확하면 `/impl` 이 그것을 바탕으로 `@Schema` 등을 자동 부착
- Request / Response DTO 필드는 문자열 길이, 숫자 범위, 컬렉션 크기, 형식 제약을 가능한 한 명시
- 길이 / 범위 제약이 이슈나 문서에 없으면 임의로 확정하지 않고 사용자에게 확인
- 사용자에게 확인할 때는 추천값과 이유를 함께 제시
  - 예: `nickname` 은 UI 표시성과 한글/영문 닉네임 사용성을 고려해 2~20자 추천
  - 예: `email` 은 일반적인 이메일 저장 한계와 unique index 운용을 고려해 `VARCHAR(255)` 추천

#### 4. Data Model (필수)

```markdown
### 새 테이블
- **{테이블명}**: 설명
  - 주요 컬럼
  - 관계

### 기존 테이블 변경 (해당 시)
- **{테이블명}**: 변경 내용

### 마이그레이션
- `V{N}__{설명}.sql` — DB migration policy 따름 (coding-convention §9)

### ERD
- 작성 대상: `docs/erd/tables/{table}.md`
- `docs/erd/overview.md` 갱신 필요 시 명시 (보통 미정 사항 해결 시)
```

룰:
- 식별자 `BIGINT`, Enum = `VARCHAR + CHECK`, 위치 = `GEOGRAPHY(POINT, 4326)`, KST timezone — [erd/overview.md](../../../docs/erd/overview.md) 따름
- `VARCHAR` 길이, 숫자 precision/scale, nullable, unique, check 제약을 가능한 한 명시
- DB 제약 값이 이슈나 문서에 없으면 임의로 확정하지 않고 사용자에게 추천값과 이유를 제시한 뒤 확인
- 마이그레이션은 새 파일로만 (기존 머지된 파일 수정 X — CLAUDE.md)

#### 5. Business Logic (필수)

```markdown
### Processing Flow (해당 시)
**표준 흐름은 적지 않는다** — JWT 추출 → repository.findById → 404 → dirty checking → Mapper 호출 류는 convention 으로 자명.
도메인 특수 단계만 적는다 — 외부 API 호출 + 실패 보상, 분산 락, 상태 전이 트리거, 멀티 엔티티 트랜잭션 경계 등.

### Validation Rules
- 구체적 수치로 (예: 1-500자, 양수만 등)

### State Transition (해당 시)
| 현재 | 액션 | 다음 | 조건 |

### Error Cases
| 상황 | 예외 | HTTP |

### Edge Cases (해당 시)
**정책성 / 도메인 특수 케이스만** — 같은 값 갱신 / null body / 멱등성 같은 self-evident 케이스는 적지 않는다.
참고할 만한 패턴: 시간 경계, 동시성, 중복 호출, 권한 경계, 외부 API 실패, 상태 충돌

### Side Effects (해당 시)
- 알림 발송 / 환불 트리거 / 이벤트 publish 등 부수 효과
- State Transition 과 함께 나오면 매핑 명시

### Test Cases (해당 시)
**case 단위 enumeration 은 적지 않는다** — `/impl` 이 API 표 + Validation Rules + Error Cases + Edge Cases 보고 표준 케이스를 도출한다.
**적는 경우**: 의미 있는 정책성 / 도메인 특수 시나리오만 — 통합 흐름 (회원가입 / 주문 / 결제 / 환불), 외부 API 실패 보상, 상태 전이 검증 등. 한국어 메서드명 ([test-convention.md](../../../docs/test-convention.md)).
```

#### 6. External Dependencies (해당 시)
- 외부 API (토스페이, 카카오맵, 국세청, FCM 등) 와 연동 시 호출 흐름 / 실패 처리 / 환경 변수

#### 7. Implementation Notes (해당 시)
**convention 에서 벗어나는 결정만** 적는다. convention 으로 자명한 mechanical detail (패키지 / 트랜잭션 / 로깅 / MapStruct / ErrorCode 분리 위치 / Entity Builder 등) 은 §4 §0 "Don't write" 리스트로 위임. 적을 게 없으면 섹션 생략.

```markdown
- **convention 밖 보안 매처 추가**: 예 — `/api/v1/customers/me/**` → `hasRole("CUSTOMER")` 매처 신규 (기존 매처 패턴과 비대칭)
- **Entity 관계 (도메인 특수)**: 양방향 + mappedBy 가 필요한 이유
- **비동기 처리**: 알림 = `@Async` / 메시지 큐 (기본 동기에서 벗어나는 경우만)
- **외부 API 어댑터 추상화**: 직접 호출 대신 Adapter 가 필요한 이유
- **캐시**: 사용 여부 / 키 / TTL
- **동시성**: 재고 차감 = pessimistic lock / optimistic lock 등
- 기타 convention 밖 결정
```

### 5. 저장 위치 / 명명

- 디렉토리: `docs/specs/`
- 파일명: `{이슈번호}-{슬러그}.md` (예: `12-store-registration.md`)
- 이미 존재하면 사용자에게 덮어쓰기 확인

### 6. 검토 (사용자 승인 단계 ★)

- **섹션마다** 작성 후 사용자에게 보여주고 OK 받은 다음 섹션으로
- 모든 섹션 완료 후 **전체 본문 다시 한 번 메시지로 출력** → 최종 OK 받기 전까지 파일 저장 X

### 7. 파일 저장

사용자 최종 OK 후 `docs/specs/{이슈번호}-{슬러그}.md` 에 저장.

### 8. 결과 보고

저장된 파일 경로 + 다음 단계 안내:

> `/impl {이슈번호}` 로 구현 진행 가능. spec 이 있으면 `/impl` 의 §1 단계에서 자동 탐색되어 함께 읽힌다 (옵트인이지만 존재 시 활용).

## 미정 발견 시 룰

`/spec` 작성 중 다음 발견 시 **즉시 중단**, `/issue` 로 돌아가라고 안내:
- 정책 (`policy.md`) 미정
- scope (`features.md` / `product.md`) 미정
- 도메인 용어 (`glossary.md`) 미정

이유: spec = 확정된 내용만. 미정 사항이 들어가면 안 됨.

단, **이 단계에서 허용되는 docs 수정**:
- `docs/erd/overview.md` 의 미정 사항 (보통 ERD 설계 시 결정되는 것)
- 이 외 docs 수정은 별도 이슈 ([CLAUDE.md 워크플로우](../../../CLAUDE.md))

## 에러 처리

| 상황 | 처리 |
|---|---|
| 이슈 없음 (`gh issue view` 실패) | 사용자에게 알리고 중단 |
| 이슈 본문에 4섹션 누락 | 어떤 섹션 누락인지 알리고 `/issue` 재호출 안내 |
| 슬러그 충돌 (파일 이미 존재) | 사용자에게 덮어쓰기 / 다른 슬러그 확인 |
| domain 라벨 없음 | 사용자에게 라벨 누락 알림 (이슈에 domain 라벨 빠짐) |

## 주의

- **worktree 안에서 실행** — 2번 단계 가드. 주 디렉터리(`develop`/`main`)면 중단하고 worktree 로 안내
- **임의 결정 X** — 미정 사항 발견 시 `/issue` 로 돌아가기
- **사용자 검토 없이 파일 저장 X** — 6번 단계 강제
- **spec + convention 분리** — spec = 정책 / API 계약 / 도메인 특수 동작. mechanical detail (Swagger / 패키지 / 트랜잭션 / 로깅 / Test 열거 등) 은 convention 문서에 위임 (§4 §0 "Don't write" 리스트). `/impl` 은 spec + convention 을 함께 본다
- **Context / Scope 그대로 복사** — spec 파일만 읽어도 모든 정보 파악 가능 (다음 세션 재현용)
- **테스트 케이스 목록은 한국어** ([test-convention](../../../docs/test-convention.md))
- **PowerShell 5.1 호환**: gh CLI 호출 시 `--repo MagamPick/magampick-api` 명시 (현재 디렉토리가 git repo 아닐 수 있음)
