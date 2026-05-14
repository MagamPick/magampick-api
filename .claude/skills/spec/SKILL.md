---
name: spec
description: GitHub Issue 기반 구현 명세 작성. 이슈에서 결정된 정책/scope 를 가져와 API/Data Model/Business Logic/구현 결정까지 채워 docs/specs/ 에 저장. 명세-설계가 한 파일에 다 들어가는 단계 — /impl 은 이 파일만 보고 코드 작성.
---

# /spec — 구현 명세 작성

마감픽 워크플로우 2단계. `/issue` 로 결정된 정책 / scope 를 받아, 구현에 필요한 모든 결정 (API, DB, 로직, 구현 디테일) 을 spec 파일로 박는다.

> 임의 결정 금지 ([CLAUDE.md 의사결정 룰](../../../CLAUDE.md)). 정책 / scope 미정 발견 시 → `/issue` 로 돌아가 결정 후 재호출.

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
- **title** → 기능명 추출 (`✨ feat: ` prefix 제거)
- **body** → 5섹션 파싱 (Context / Scope / User Roles / 핵심 정책 결정 / Business Logic 큰 그림)
- **labels** → domain 라벨로 도메인 그룹 추정

### 2. 슬러그 추출 + 작업 브랜치 생성

**슬러그 추출**:
1. 이슈 제목에서 type prefix 제거 (`^[이모지] [type]: ` 패턴)
2. 남은 한국어 기능명을 [`glossary.md`](../../../docs/glossary.md) 영문 매핑으로 변환
3. 영문 단어 kebab-case 로 결합

예: `✨ feat: 매장 등록 신청` → `매장 등록 신청` → `store-registration`

glossary 에 없는 용어는 사용자에게 옵션 제시 + 확정. 추출 결과는 **사용자에게 확인 후 사용**.

**작업 브랜치 생성** (슬러그 확정 후):
```powershell
$gh = 'C:\Program Files\GitHub CLI\gh.exe'
& $gh issue develop {N} --repo MagamPick/magampick-api --base develop --name "feat/{N}-{슬러그}" --checkout
```
- GitHub 이슈에 연결된 브랜치를 origin 에 생성 + 로컬 checkout (PR 머지 시 이슈 자동 클로즈)
- type 이 feat 가 아니면 prefix 조정 (`fix/`, `refactor/` 등)
- 이미 `feat/{N}-*` 작업 브랜치에 있으면 skip
- **이후 `/spec` 의 spec 저장 + `/impl` 전부 이 브랜치에서 진행** — `develop` / `main` 에서 작업 금지

### 3. 사전 점검 (Read only)
- 이슈 본문의 미정 사항 있나? → 있으면 **`/issue` 로 돌아가 결정 후 재호출** 안내, **중단**
- [features.md](../../../docs/features.md) / [policy.md](../../../docs/policy.md) / [glossary.md](../../../docs/glossary.md) / [erd/overview.md](../../../docs/erd/overview.md) — 컨텍스트 확인
- [coding-convention.md](../../../docs/coding-convention.md) / [api-convention.md](../../../docs/api-convention.md) / [auth.md](../../../docs/auth.md) / [test-convention.md](../../../docs/test-convention.md) — 작성 시 따를 룰

### 4. 8섹션 본문 작성 (대화)

**섹션마다 채우고 사용자 검토 → 다음 섹션** 흐름. `/issue` 와 일관.

#### 1. Context (필수)
- 이슈 Context 를 **그대로 복사** (다음 세션 / `/impl` 호출 시 spec 만 읽어도 컨텍스트 파악 가능하게)
- 이슈 링크는 맨 위에 한 줄 (예: `> 이슈: #12`)

#### 2. Scope (필수)
- 이슈 Scope (In Scope / Out of Scope) 를 **그대로 복사**

#### 3. User Roles (해당 시)
- 이슈에서 가져옴

#### 4. API Specification (필수)
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

#### 5. Data Model (필수)

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
- 마이그레이션은 새 파일로만 (기존 머지된 파일 수정 X — CLAUDE.md)

#### 6. Business Logic (필수)

```markdown
### Processing Flow
1. 입력 검증
2. 핵심 로직
3. 출력

### Validation Rules
- 구체적 수치로 (예: 1-500자, 양수만 등)

### State Transition (해당 시)
| 현재 | 액션 | 다음 | 조건 |

### Error Cases
| 상황 | 예외 | HTTP |

### Edge Cases
이 기능에서 발생 가능한 경계 / 특수 케이스 자유롭게 서술.
참고할 만한 패턴: 시간 경계, 동시성, 중복 호출, null / 빈 값, 권한 경계, 외부 API 실패
— **단, 해당하는 것만 다룬다. 억지로 채우지 않음.**

### Side Effects (해당 시)
- 알림 발송 / 환불 트리거 / 이벤트 publish 등 부수 효과
- State Transition 과 함께 나오면 매핑 명시

### Test Cases (해당 시)
한국어 메서드명으로 (test-convention.md 따름):

#### Service 단위 테스트
- `매장_등록_성공`
- `매장_등록_실패_사업자번호_중복`

#### Controller @WebMvcTest
- `POST_stores_201_성공`
- `POST_stores_400_사업자번호_누락`
```

#### 7. External Dependencies (해당 시)
- 외부 API (토스페이, 카카오맵, 국세청, FCM 등) 와 연동 시 호출 흐름 / 실패 처리 / 환경 변수

#### 8. Implementation Notes (해당 시)
구현 결정 사항. 단순 기능은 섹션 생략:

```markdown
- **Entity 관계**: 단방향 / 양방향, mappedBy 위치
- **트랜잭션 경계**: Service 메서드 단위 / Facade 패턴 / Propagation
- **비동기 처리**: 알림 = `@Async` / 메시지 큐 / 동기
- **외부 API 어댑터 구조**: 직접 호출 / Adapter 추상화
- **캐시**: 사용 여부 / 키 / TTL
- **동시성**: 재고 차감 = pessimistic lock / optimistic lock / DB level
- **예외 클래스 분리**: 도메인별 / 공통
- 기타 구현 결정
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

> `/impl {이슈번호}` 로 구현 진행 가능

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
| 이슈 본문에 5섹션 누락 | 어떤 섹션 누락인지 알리고 `/issue` 재호출 안내 |
| 슬러그 충돌 (파일 이미 존재) | 사용자에게 덮어쓰기 / 다른 슬러그 확인 |
| domain 라벨 없음 | 사용자에게 라벨 누락 알림 (이슈에 domain 라벨 빠짐) |

## 주의

- **임의 결정 X** — 미정 사항 발견 시 `/issue` 로 돌아가기
- **사용자 검토 없이 파일 저장 X** — 6번 단계 강제
- **명세 + 설계 한 파일** — `/impl` 은 이 파일만 보고 코드 작성하므로 누락 없게
- **Context / Scope 그대로 복사** — spec 파일만 읽어도 모든 정보 파악 가능 (다음 세션 재현용)
- **테스트 케이스 목록은 한국어** ([test-convention](../../../docs/test-convention.md))
- **PowerShell 5.1 호환**: gh CLI 호출 시 `--repo MagamPick/magampick-api` 명시 (현재 디렉토리가 git repo 아닐 수 있음)
