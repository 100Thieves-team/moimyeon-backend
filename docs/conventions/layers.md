# 레이어드 아키텍처

[← 허브로](README.md)

```text
HTTP 요청
  │
  ▼
Controller (core.api.controller.v1)     ── 요청/응답 DTO 변환, ApiResponse 래핑
  │            │
  │            └─▶ Facade (core.api.facade)  ── 여러 Service 결과 조합이 필요할 때만
  ▼
Service (core.domain.{개념})            ── Business 레이어: 비즈니스 "흐름"(오케스트레이션)
  │
  ▼
Implement (Finder/Manager/... )         ── Logic 레이어: 재사용 로직 + 저장소 접근
  │
  ▼
Repository / Entity (storage.db.core)   ── Spring Data JPA
```

## 반드시 지킬 것은 두 가지다

1. **Service 를 읽으면 비즈니스 흐름이 보여야 한다.** 무엇을 어떤 순서로 확인하고 언제 저장하는지가
   메서드 본문에서 그대로 읽혀야 한다. 조회 방법·매핑·쿼리 같은 세부가 본문에 섞여 흐름을 가리면
   아래 레이어로 내린다.
2. **Service 는 JPA 엔티티를 직접 다루지 않는다.** 엔티티 타입은 저장소 접근 코드 안에서 도메인
   객체로 변환을 끝내고 나온다. Service 이상에 엔티티가 등장하면 경계 위반이다.

**Implement 레이어(Finder/Manager 등)는 이 둘을 지키기 위한 권장 패턴이지, 그 자체가 강제는
아니다.** 개념이 단순하면 역할을 잘게 쪼개지 않아도 되고, 엔티티가 오가지 않는 단순 판정
(`existsBy...` 등)은 Service 가 Repository 를 직접 불러도 된다. 반대로 저장소 접근·변환 코드가
Service 본문에 쌓여 흐름이 안 읽히기 시작하면 그때 분리한다.

각 레이어의 역할 한 줄 정의:

| 레이어 | 역할 | 하지 않는 것 |
| --- | --- | --- |
| Controller | 요청/응답 DTO 변환, ApiResponse 래핑 | 비즈니스 판단, 여러 Service 조합 |
| Facade | 여러 Service 결과를 **조합**해 응답 DTO 조립 | 비즈니스 판단 |
| Service | 비즈니스 **흐름** — 검증 순서, 분기 | JPA 엔티티 사용, 트랜잭션 경계 |
| Implement | **재사용 로직** — 조회·저장·변환·생성 단위 구현, 트랜잭션 경계 | 흐름 분기(합성 메서드 금지) |
| Repository | 파생 쿼리·JPQL | 도메인 규칙 |

## Controller

- 요청 DTO 를 도메인 입력으로 변환하고(`request.toXxx(...)`), 결과를 `ApiResponse` 로 감싸는
  것까지가 컨트롤러의 일이다.
- 단일 Service 호출은 컨트롤러가 직접 한다. **여러 Service 의 결과를 조합해야 하는 응답은
  컨트롤러에서 조합하지 않고 Facade 를 둔다.**
- 컨트롤러는 **애그리거트(개념) 단위**로 묶는다. 엔드포인트가 아니라 개념이 기준이다.
  예: 내 상태·닉네임(회원 속성)은 `MemberController`, 소개 작성/수정은 `ProfileController`.
- 인증 주입은 `@LoginMember currentMember: CurrentMember` ([auth.md](auth.md)).
- 요청 바디는 `@Valid @RequestBody`, 쿼리/경로 파라미터 제약은 애노테이션만 붙인다
  (클래스 레벨 `@Validated` 금지 — [api-design.md](api-design.md)).

## Facade

여러 Service 의 결과를 조합해야 하는 응답의 조립 지점. `core.api.facade` 에 `[개념]Facade`
(`@Component`)로 둔다.

```kotlin
@Component
class ProfileFacade(
    private val profileService: ProfileService,
    private val catalogService: CatalogService,
) {
    fun update(memberId: UUID, content: ProfileContent): ProfileResponse {
        profileService.update(memberId, content) // 쓰기는 식별자만 반환
        val updated = profileService.getProfile(memberId) // 응답 조립은 재조회로
        return ProfileResponse.from(updated, catalogService.getCompanies(updated.interestCompanyIds))
    }
}
```

- Facade 는 Service 들을 호출해 결과를 모으고 응답 DTO 로 조립해 반환한다. 컨트롤러는
  `ApiResponse.success(facade.xxx(...))` 한 줄이 된다.
- **조합만 하고 판단은 하지 않는다.** if 로 도메인 규칙을 분기하기 시작하면 Service 로 내린다.
- Service 끼리 서로 호출하는 대신, 여러 개념의 결과가 필요한 지점은 Facade 로 올린다
  ([concepts.md](concepts.md)의 참조 규칙과 함께 동작한다).

## Service (= Business)

- 개념의 **흐름**을 소유한다. 무엇을 어떤 순서로 확인하고, 언제 저장하는지.
  ```kotlin
  // ProfileService.create 의 흐름
  memberFinder.getById(memberId)          // 살아있는 회원인가 (아니면 E1006 이 이미 던져짐)
  validateCatalogRefs(content)            // 참조 무결성 사전 검증 (E1301/E1302/E1303)
  requireBusiness(termsAgreementFinder.hasAgreedAllRequiredActive(...), TERMS_NOT_AGREED)
  requireBusiness(!profileFinder.exists(...), PROFILE_ALREADY_EXISTS)
  profileManager.append(memberId, content) // 저장 (동시성 레이스는 유니크 충돌 catch 로 흡수)
  ```
- 교차 규칙(다른 개념과 얽힌 규칙)은 Service 가 확인한다. 단, 상대 개념의 Finder 가 노출한
  판정을 입력으로 쓴다 ([concepts.md](concepts.md)).
- 다른 개념의 Service 호출 금지, Implement 호출은 허용.
- Service 는 기본적으로 트랜잭션 경계를 갖지 않는다. 경계는 쓰기 Implement 가 소유한다 —
  [트랜잭션](#트랜잭션) 절 참고.

## Implement (= Logic)

Service 의 흐름을 가리는 세부(조회 방법·엔티티 변환·저장·값 생성)를 내려받는 자리다.
**쓰는 것 자체가 목적이 아니다** — 위의 두 규칙(흐름 가시성·엔티티 격리)을 지키는 수단이고,
필요해질 때 만든다. `@Component`, 개념 패키지 안에 위치. 역할 접미사로 책임을 드러낸다.

| 접미사         | 책임                                                       | 예시                       |
|-------------|----------------------------------------------------------|--------------------------|
| `Finder`    | 조회. **`get*`(non-null) / `exists*`·`is*`(Boolean) 만 노출** | `ProfileFinder`          |
| `Reader`    | 복합 조회 + 도메인 객체 조립                                        |                          |
| `Adder`     | 추가                                                       |                          |
| `Handler`   | 흐름 처리                                                    |                          |
| `Processor` | 처리/후처리                                                   |                          |
| `Manager`   | 쓰기(생성/수정)                                                | `ProfileManager`         |
| `Recorder`  | append-only 이력 기록                                        | `TermsAgreementRecorder` |
| `Generator` | 값 생성기                                                    | `NicknameGenerator`      |
| `Mapper`    | Entity ↔ 도메인 변환 (`object`, 빈 아님)                         | `ProfileMapper`          |

**이 표는 강제가 아니라 자주 쓰는 어휘의 참고 사전이다.** 같은 책임이면 표의 접미사를 재사용해
일관성을 유지하되, 표에 없는 책임이 나오면 요구사항에 맞춰 책임이 드러나는 이름을 새로 지으면
된다(예: `Calculator`, `Validator`). 중요한 건 접미사 자체가 아니라 읽기/쓰기/계산/검증의 책임이
한 클래스에 섞이지 않는 것이다.

### nullable 과 엔티티는 persistence 경계를 넘지 않는다

이 레포의 가장 중요한 레이어 규칙.

- **nullable**: `null` 은 Finder 내부(persistence 경계)까지만 쓴다. Finder 는
  - `get*`: non-null 도메인 객체 반환. 없으면 내부에서 `requireFound(..., E코드)` 로 즉시 예외.
  - `exists*` / `is*`: Boolean 판정.
  - `find*`(nullable 반환) 메서드를 **외부에 노출하지 않는다**. null 검사와 예외 매핑이 호출부마다
    반복되는 것을 막기 위함이다.
  ```kotlin
  fun getProfile(memberId: UUID): MemberProfile {
      val entity = requireFound(memberProfileRepository.findById(memberId).orElse(null), CoreErrorType.PROFILE_NOT_FOUND)
      return ProfileMapper.toDomain(entity)
  }
  ```
- **엔티티**: Entity 는 Implement(Mapper) 안에서 도메인 객체로 변환되어 나간다.
  Service 이상의 레이어에 Entity 타입이 등장하면 경계 위반이다.
- **상태 필터도 경계에서**: 도메인에 들어오면 안 되는 상태의 행은 Finder 의 파생 쿼리로 걸러낸다
  (예: 탈퇴 회원은 `...DeletedAtIsNull` 조회로 도메인 세계에 들여보내지 않는다).
  예외적으로 걸러진 행을 봐야 하는 경우는 의도가 드러나는 전용 조회를 따로 판다.

### 예외는 Implement 에서 던진다

"없음"의 의미 부여(어떤 E 코드인가)는 조회 책임을 가진 Finder 가 한다. Service 는 null 을 받지 않으므로
not-found 매핑을 반복하지 않는다. 규칙 위반은 Service 가 `requireBusiness` 로 던진다 ([errors.md](errors.md)).

## 트랜잭션

> **트랜잭션은 레이어가 아니라 커밋 단위에 붙인다.**

판별 기준은 하나다. 이 메서드가 실패했을 때 **DB 쓰기 전체가 한꺼번에 없던 일이 돼야 하는가?**
Yes → 그 메서드가 트랜잭션 경계. No → 붙이지 않는다.

경계를 쓰기 Implement(`Manager`/`Recorder` 등) 단위로 설계하는 목적은 **커밋의 응집**이다 —
함께 롤백돼야 하는 쓰기 묶음이 곧 하나의 쓰기 컴포넌트가 되고, 트랜잭션은 그 컴포넌트의
메서드에 붙는다. Service 에 `@Transactional` 이 금지라서가 아니다. Service 는 커밋 단위가
아니라 흐름이고, 외부 I/O(소셜 API·알림·파일 업로드·PG)가 끼어드는 자리라서 경계로 삼을
이유가 없다. Service 에 달면 외부 시스템이 느려질 때 DB 커넥션을 그만큼 붙잡아, 장애가
무관한 API 로 번진다.

| 대상 | 트랜잭션 |
| --- | --- |
| Controller / Facade | 없음 |
| Service | 기본 없음 (예외: 아래 "여러 쓰기를 묶어야 할 때"의 3) |
| `Finder`/`Reader` | 여러 쿼리를 한 스냅샷으로 읽을 때만 `readOnly = true` |
| `Manager`/`Recorder`/`Adder` (쓰기) | `@Transactional` **필수** |
| `Generator`/`Mapper` (계산·변환) | 없음 |

쓰기 컴포넌트의 `@Transactional` 이 필수인 이유: dirty checking(엔티티 수정·소프트 삭제)은
영속성 컨텍스트가 없으면 조용히 유실된다. 호출자가 트랜잭션을 갖고 있으리라는 암묵적
의존은 시그니처에 드러나지 않으므로, 쓰기 컴포넌트가 자기 경계를 소유한다
(예: `ProfileInterestManager.replaceAll` — 트랜잭션 없이 호출되면 소프트 삭제만 유실되고
삽입만 커밋되는 부분 쓰기가 된다).

### 여러 쓰기를 원자적으로 묶어야 할 때

우선순위대로 시도한다.

1. **호출을 쓰기 컴포넌트 안으로 내린다.** 함께 커밋돼야 하는 두 쓰기는 대개 한 쓰기
   컴포넌트의 책임이다. 하위 Manager 도 각자 `@Transactional` 을 갖되 전파는 기본
   `REQUIRED` 이므로, 상위 트랜잭션에 참여해 커밋 하나가 된다.
   (`ProfileManager.append` → `ProfileInterestManager.replaceAll` 이 이 형태다.)
2. **둘을 감싸는 새 Implement 를 만든다.** 두 개념의 쓰기가 한 커밋이어야 하면,
   그 조합 자체가 재사용 가능한 하나의 개념이다. (예정 사례: 탈퇴는 회원·프로필·관심
   소프트 삭제가 한 커밋이어야 하므로 `MemberWithdrawProcessor` 하나가 감싼다.)
3. **Service 에서 `TransactionTemplate` 으로 시도 단위 경계를 잡는다.** 실패한 쓰기를 잡아
   **재시도**하는 흐름은 메서드 전체를 `@Transactional` 로 묶으면 rollback-only 가 된 같은
   트랜잭션 안에서 재시도하게 되므로 이 형태를 쓴다. (`SocialAuthService.provision` —
   가입 + 약관 자동 동의가 한 시도, 닉네임 충돌 재시도는 새 트랜잭션.)

`REQUIRES_NEW` 는 쓰지 않는다. "일부만 커밋돼야 한다"는 요구는 대개 컴포넌트를 잘못
나눴다는 신호다.

### 트랜잭션 안에 두지 않는 것 (하드 룰)

- 외부 시스템 호출: HTTP/Feign, 메일·푸시·SMS, 파일 스토리지, 메시지 발행
- 되돌릴 수 없는 부수효과 전반 — 롤백돼도 되돌아오지 않으므로 커밋 단위에 있을 이유가 없다
- 재시도 루프·대기 (예: `NicknameGenerator.generateUnique` 의 점유 확인 SELECT 루프는
  가입 트랜잭션 밖에서 실행한다 — 유일성의 최종 보장은 DB 유니크 제약이다)

외부 호출은 Service 에 남기고, 그 앞뒤의 DB 쓰기만 Implement 로 내려 각각 짧은
트랜잭션으로 만든다. 외부 호출이 성공한 뒤 DB 쓰기가 실패하는 구간은 **원자적일 수
없다** — 보상 로직이나 재처리로 다루고, 트랜잭션으로 덮으려 하지 않는다.

성공 사례: OAuth 로그인은 토큰 교환(외부 I/O)이 Spring Security 필터 체인(트랜잭션 밖)에서
끝나고, `OAuth2LoginSuccessHandler` 는 DB 쓰기만 한다. 가입 커밋(트랜잭션 A)과 세션
저장(트랜잭션 B)은 **의도된 비원자 경계**다 — B 가 실패해도 재로그인이
`existsBySocialAccount` → `recordLogin` 경로로 흘러 복구되므로 원자성을 요구하지 않는다.

### 조회 트랜잭션

- **여러 쿼리를 한 스냅샷으로 읽어야 할 때만** `@Transactional(readOnly = true)` 를 붙인다.
  레포지토리 호출이 하나뿐인 위임 메서드에는 붙이지 않는다 — `SimpleJpaRepository` 가 이미
  클래스 레벨 `readOnly` 트랜잭션이라 트랜잭션만 하나 더 열 뿐 얻는 것이 없다.

  ```kotlin
  @Transactional(readOnly = true)   // 프로필 + 관심직무 + 관심회사 3쿼리를 한 스냅샷으로
  fun getProfile(memberId: UUID): MemberProfile { ... }

  fun exists(memberId: UUID): Boolean =            // 단일 위임 — 붙이지 않는다
      memberProfileRepository.existsByMemberIdAndDeletedAtIsNull(memberId)
  ```

- 한 요청이 만드는 트랜잭션 개수를 의식한다. 습관적으로 붙이면 요청 하나에 트랜잭션이
  수 개씩 열리고 닫힌다. 개별 비용은 작지만 "습관적으로 붙인다"의 결과라는 점은 같다.
- `open-in-view: false`. 레이지 로딩에 기대지 말고 경계 안에서 조립을 끝낸다.

### 유니크 충돌을 호출자가 잡아야 하면 쓰기 Implement 안에서 flush 한다

flush 하지 않으면 제약 위반은 **커밋 시점**, 즉 쓰기 Implement 의 트랜잭션 경계에서
터진다. 그러면 어떤 제약이 터졌는지 구분하기 어렵고, 같은 메서드 안의 다른 쓰기까지
모두 수행한 뒤에 실패한다. 호출자가 `DataIntegrityViolationException` 을 잡아 도메인
예외로 매핑하는 흐름이라면, 쓰기 직후 flush 해서 예외가 예측 가능한 지점에서 터지게 한다
([errors.md](errors.md)의 유니크 충돌 처리와 같은 규칙).

- `MemberManager.append` — `saveAndFlush`. `SocialAuthService` 가 닉네임 충돌을 잡아 재시도한다.
- `MemberManager.changeNickname` — `flush()`. `MemberService` 가 `NICKNAME_DUPLICATED` 로 매핑한다.

flush 여부는 **호출자가 그 예외를 잡는가**로 결정한다. 잡지 않으면 붙이지 않는다.

## 레이어 간 반환 타입

| 경계 | 타입 |
| --- | --- |
| Web ↔ Controller | 전용 DTO (`controller/v1/request`, `response`) |
| Controller ↔ Facade | 응답 DTO (Facade 가 조립을 끝내고 반환) |
| Controller/Facade ↔ Service | 조회: 도메인 객체 / 쓰기: 식별자(id) |
| Service ↔ Implement | 조회: 도메인 객체(non-null)·Boolean / 쓰기: 식별자(id) |
| Implement ↔ Repository | Entity (Mapper 로 변환) |

**쓰기(save/update) 연산은 도메인 객체를 반환하지 않는다.** 식별자(id — Long 이 기본, PK 가 UUID 면
UUID)를 우선 반환하고, 반환할 것이 마땅치 않으면 Unit. 응답 조립에 갱신된 상태가 필요하면
쓰기 후 Finder 로 **재조회**한다(예: `ProfileFacade.update` — update 후 getProfile 로 응답 구성).
