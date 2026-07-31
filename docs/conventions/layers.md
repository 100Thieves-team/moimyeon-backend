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
   메서드 본문에서 그대로 읽혀야 한다. 본문에 들어가는 것은 **검증 도구 호출 + 외부 I/O +
   쓰기 호출** 셋뿐이다. 판정 방법·조회 방법·매핑·쿼리 같은 세부가 섞여 흐름을 가리면 아래
   레이어로 내린다.
2. **Service 는 JPA 엔티티를 직접 다루지 않는다.** 엔티티 타입은 저장소 접근 코드 안에서 도메인
   객체로 변환을 끝내고 나온다. Service 이상에 엔티티가 등장하면 경계 위반이다.

**Implement 레이어(Finder/Validator/Manager 등)는 이 둘을 지키기 위한 권장 패턴이지, 그 자체가
강제는 아니다.** 개념이 단순하면 역할을 잘게 쪼개지 않아도 된다. 반대로 판정·저장소 접근·변환
코드가 Service 본문에 쌓여 흐름이 안 읽히기 시작하면 그때 분리한다.

각 레이어의 역할 한 줄 정의:

| 레이어 | 역할 | 하지 않는 것 |
| --- | --- | --- |
| Controller | 요청/응답 DTO 변환, ApiResponse 래핑 | 비즈니스 판단, 여러 Service 조합 |
| Facade | 여러 Service 결과를 **조합**해 응답 DTO 조립 | 비즈니스 판단 |
| Service | 비즈니스 **흐름의 골격** — 검증 도구 호출 + 외부 I/O + 쓰기 호출 | 직접 판정(`requireBusiness`), JPA 엔티티 사용, 트랜잭션 경계 |
| Implement | **재사용 로직** — 조회·검증·저장·변환·생성, **커밋 단위 조합**, 트랜잭션 경계 | 외부 I/O, 사용자 대면 흐름 분기 |
| Repository | 파생 쿼리·JPQL | 도메인 규칙 |

## Controller

- 요청 DTO 를 도메인 입력으로 변환하고(`request.toXxx(...)`), 결과를 `ApiResponse` 로 감싸는
  것까지가 컨트롤러의 일이다.
- 단일 Service 호출은 컨트롤러가 직접 한다. **여러 Service 의 결과를 조합해야 하는 응답은
  컨트롤러에서 조합하지 않고 Facade 를 둔다.**
- 컨트롤러는 **애그리거트(개념) 단위**로 묶는다. 엔드포인트가 아니라 개념이 기준이다.
  예: 내 상태·닉네임(회원 속성)은 `MemberController`, 소개 작성/수정은 `ProfileController`.
- 인증 주입은 `@LoginMember currentMember: CurrentMember` ([auth.md](auth.md)).
- **Bean Validation 을 쓰지 않는다.** 요청 바디는 `@RequestBody` 로만 받고, 값 규칙은 요청 DTO 의
  `toXxx()` 안에서 확정한다. 쿼리/경로 파라미터 제약은 핸들러 본문에서 검증한다
  ([api-design.md](api-design.md)).

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

Service 는 개념의 **흐름의 골격**이다. 본문에 들어가는 것은 셋뿐이다.

> **검증 도구 호출 + 외부 I/O + 쓰기 호출**

```kotlin
// ProfileService.create
validateCatalogRefs(content)                     // 다른 개념(catalog)의 판정 — 트랜잭션 밖
return profileManager.append(memberId, content)  // 쓰기 — 약관 동의·프로필 중복은 append 가 자기 경계 안에서
```

**Service 는 판정하지 않는다.**

- **`requireBusiness`/`requireFound` 를 Service 본문에 쓰지 않는다.** 규칙 위반은 그 규칙을
  판정한 Implement 가 던진다. Service 에 `if`/`requireBusiness` 가 쌓이기 시작하면 그 판정은
  아래로 내려갈 신호다.
- 다른 개념의 Service 호출 금지, Implement 호출은 허용.
- Service 는 트랜잭션 경계를 갖지 않는다. 경계는 쓰기 Implement 가 소유한다 —
  [트랜잭션](#트랜잭션) 절 참고.

### 검증은 기본적으로 쓰기 Implement 안에서 한다

대부분의 검증은 **한 줄짜리**다. 그런 것을 위해 별도 컴포넌트를 만들면 클래스만 늘고 호출
경로만 길어진다. 그 판정에 필요한 데이터를 이미 보고 있는 `Manager`/`Adder`/`Recorder` 안에서
그냥 검증한다.

```kotlin
@Transactional
fun changeNickname(memberId: UUID, nickname: Nickname) {
    val entity = requireFound(memberRepository.findByIdAndDeletedAtIsNull(memberId), MEMBER_NOT_FOUND)
    requireBusiness(!memberRepository.existsByNicknameAndIdNot(nickname.value, memberId), NICKNAME_DUPLICATED)
    ...
}
```

특히 **"이미 있는가" 류는 쓰기 경계 안에서 확정되어야 정확하다.** Service 에서 미리 확인하면
판정이 두 곳으로 갈리고, 커밋 밖의 확인이라 어차피 확정이 아니다 — 최종 방어선은 유니크 제약이고
그 충돌을 도메인 에러로 번역하는 것도 그 쓰기 Implement 의 일이다
(`ProfileManager.append` 가 `uk_member_profile_member` 충돌을 `PROFILE_ALREADY_EXISTS` 로 번역한다).

### 별도 `Validator` 는 다른 개념의 판정을 물어야 할 때만 만든다

**단일 개념 안에서 자기 데이터만 보면 되는 검증은 도구로 빼지 않는다.** 그 컴포넌트가 Repository 를
직접 불러 그 자리에서 검증하면 된다. 굳이 빼면 클래스와 의존만 늘어난다.

`Validator` 는 **판정 자체가 개념 경계를 넘을 때** 생긴다. 두 방향이 있다.

- **자기 개념의 판정을 밖에 노출** — `CatalogRefValidator`. catalog 가 자기 참조 데이터의 유효성을
  판정해 profile·room·job-posting 이 쓴다.
- **자기 개념의 판정인데 다른 개념 데이터가 필요** — `ReviewPolicyValidator`. "이 리뷰를 쓸 자격이
  있는가"는 review 의 규칙이지만 order 데이터를 조회해야 판정된다.

어느 쪽이든 그 조회 묶음 자체가 로직이고, 조회 결과를 뒤 단계로 넘길 것이 있으면 반환한다
(`ReviewPolicyValidator.validateNew` 가 `ReviewKey` 를 만들어 돌려준다).

```kotlin
// catalog 개념이 자기 참조 데이터(직무·지역·회사)의 유효성을 판정해 밖에 노출한다.
// profile 이 쓰고, room·job-posting 도 같은 판정을 쓴다.
@Component
class CatalogRefValidator(
    private val jobRoleRepository: JobRoleRepository,
    private val sigunguRepository: SigunguRepository,
    private val companyRepository: CompanyRepository,
) {
    fun validateJobRoles(jobRoleIds: Collection<Long>) { ... }
    fun validateSigungu(sigunguId: Long) { ... }
    fun validateCompanies(companyIds: Collection<Long>) { ... }
}
```

- **도구 이름은 규칙을 말한다. 호출자를 말하지 않는다.** `validateForCreate` 처럼 호출하는 Service
  메서드 이름이 박히면 그 호출자 전용이 되어 재사용이 사라진다 — Service 본문을 옮겨놓은 것일 뿐이다.
- **검증 항목은 쪼개서 노출한다.** 여러 규칙을 한 메서드로 묶으면 그 조합이 곧 호출자의 모양이 된다.
  프로필은 세 가지를 다 쓰지만 다른 개념은 하나만 쓸 수 있다.
- 조회만 하므로 **쓰기 트랜잭션 밖(Service)에서 호출**한다. 트랜잭션 안에 넣으면 커밋 단위가
  참조 조회만큼 길어진다.

### 같은 개념 안에서는 Implement 끼리 참조하지 않는다

`Manager` 가 같은 개념의 `Finder` 를 주입받아 한 줄 위임을 쓰는 형태는 만들지 않는다.
**자기 개념의 데이터는 Repository 를 직접 부른다.** 한 줄 얻자고 컴포넌트를 끼우면 의존 그래프만
복잡해지고, 그 Finder 가 바뀔 때 영향 범위가 넓어진다.

```kotlin
// ✗ 같은 개념(member)의 Finder 를 한 줄 위임으로 끼운다
class NicknameGenerator(private val memberFinder: MemberFinder)

// ○ 자기 개념의 Repository 를 직접 본다
class NicknameGenerator(private val memberRepository: MemberRepository)
```

같은 개념 안에서 Implement 를 참조하는 것은 **위임이 아니라 실질적 이유가 있을 때만**이다.

- 트랜잭션 경계를 나누려고 (`SettlementTransferProcessor` → `SettlementTransferHandler`)
- 커밋 단위 조합 (`MemberProvisioner` → `MemberManager`)
- 그 자체가 로직인 컴포넌트 (`ProfileManager` → `ProfileInterestManager` 의 차집합 교체,
  `OrderManager` → `OrderKeyGenerator`)

### 격벽을 넘는 것은 Implement 의 일이다

> **Service 는 자기 개념의 Implement 를 본다. 그 Implement 가 다른 격벽의 컴포넌트를 본다.**

```
PaymentService → PaymentValidator·PaymentProcessor → order·cancel Repository, PointHandler
```

결제·취소·정산처럼 **경계에 걸친 행위**는 여러 개념의 데이터를 한 커밋으로 다뤄야 한다. 그 조합
Implement 가 다른 개념에 접근하는 것은 정상이다 — 그러라고 만든 자리다.

**단, 접근 방식에 우선순위가 있다.**

> **다른 개념이 필요하면 그 개념의 로직 클래스를 우선 쓴다.**
> Repository 직접 접근은 금지가 아니라 **차선**이다 — 위임할 로직 클래스가 없거나, 그 클래스로는
> 목적을 이룰 수 없을 때만 내려간다.

`CancelProcessor` 가 그 둘을 한 클래스 안에서 정확히 갈라 쓴다.

```kotlin
// 위임 — 그 개념이 로직을 갖고 있다(상태 전이·잔액 갱신·이력 기록·예외 번역)
ownedCouponUsageManager.revert(payment.ownedCouponId)
pointHandler.earn(User(payment.userId), PointType.PAYMENT, payment.id, payment.usedPoint)

// 직접 — 남의 엔티티를 자기 커밋 안에서 변경해야 한다. 도메인 객체를 받으면 변경 감지가 안 된다
val order = orderRepository.findByOrderKeyAndStateAndStatus(...) ?: throw ...
order.canceled()
```

Repository 직접이 정당한 경우는 대체로 이 셋이다.

- **남의 엔티티를 자기 커밋 안에서 변경**해야 한다 (`order.canceled()`) — Finder 는 도메인 객체를
  반환하므로 변경 감지가 걸리지 않는다
- **락 획득이 목적**이다 — 락은 Finder 의 계약이 아니다
- 그 개념에 **위임할 로직 클래스가 아직 없다**

그 외 **단순 조회·판정은 반드시 그 개념의 로직 클래스를 거친다.**

```kotlin
// ✗ 다른 개념(member)의 Repository 로 단순 조회
val entity = memberRepository.findByIdAndDeletedAtIsNull(memberId) ?: throw ...

// ○ 그 개념이 노출한 조회를 쓴다
val member = memberFinder.getById(memberId)
```

Repository 로 내려갈 때는 **왜 위임할 수 없었는지 주석으로 남긴다.** 그래야 다음 사람이
"편해서 그랬나"와 구분한다.

**단순 조회 컴포넌트(`Finder`/`Reader`)는 다른 개념 Repository 를 볼 일이 없다.** 자기 개념만 본다.
그럴 필요가 생겼다면 그건 조회가 아니라 경계에 걸친 행위라는 신호다.

**예외 — 개념 객체가 이미 다른 개념을 알고 있으면 Service 가 직접 쓴다.**
`OrderItem` 이 `productId`·`productName` 을 들고 있으니 `OrderService` 가 `ProductFinder` 를
주입받는다. `MemberProfile` 이 관심 직무·회사·지역 id 를 들고 있으니 `ProfileService` 가
`CatalogRefValidator` 를 쓴다. 개념 객체가 모르는 사이라면 Service 도 몰라야 한다 —
`Member` 는 프로필의 존재를 모르므로 `MemberService` 는 `ProfileFinder` 를 쓰지 않고,
둘을 함께 보여줄 응답은 `MemberFacade` 가 조합한다([concepts.md](concepts.md)).

## Implement (= Logic)

Service 의 흐름을 가리는 세부(조회 방법·엔티티 변환·저장·값 생성)를 내려받는 자리다.
**쓰는 것 자체가 목적이 아니다** — 위의 두 규칙(흐름 가시성·엔티티 격리)을 지키는 수단이고,
필요해질 때 만든다. `@Component`, 개념 패키지 안에 위치. 역할 접미사로 책임을 드러낸다.

| 접미사         | 책임                                                       | 예시                       |
|-------------|----------------------------------------------------------|--------------------------|
| `Finder`    | 조회. **`get*`(non-null) / `exists*`·`is*`(Boolean) 만 노출** | `ProfileFinder`          |
| `Validator` | **다른 개념에 노출하는** 규칙 검증. 위반이면 자기가 던진다        | `CatalogRefValidator` |
| `Reader`    | 복합 조회 + 도메인 객체 조립                                        |                          |
| `Adder`     | 추가                                                       |                          |
| `Handler`   | 흐름 처리                                                    |                          |
| `Processor` | 처리/후처리                                                   |                          |
| `Manager`   | 쓰기(생성/수정)                                                | `ProfileManager`         |
| `Recorder`  | append-only 이력 기록                                        | `TermsAgreementRecorder` |
| `Generator` | 값 생성기                                                    | `NicknameGenerator`      |
| `Provisioner` | 한 커밋 단위의 여러 쓰기 조합 + 예외 번역                        | `MemberProvisioner`      |
| `Mapper`    | Entity ↔ 도메인 변환 (`object`, 빈 아님)                         | `ProfileMapper`          |

**이 표는 강제가 아니라 자주 쓰는 어휘의 참고 사전이다.** 같은 책임이면 표의 접미사를 재사용해
일관성을 유지하되, 표에 없는 책임이 나오면 요구사항에 맞춰 책임이 드러나는 이름을 새로 지으면
된다(예: `Calculator`, `Validator`). 중요한 건 접미사 자체가 아니라 읽기/쓰기/계산/검증의 책임이
한 클래스에 섞이지 않는 것이다.

### Implement 도 조합한다 — 단, 커밋 단위 안에서만

합성 금지의 목적은 **흐름이 Implement 로 숨는 것을 막는 것**이지, 조합 자체를 막는 것이 아니다.
경계는 이렇다.

> **"흐름"은 Service, "한 커밋 단위 안의 순서"는 Implement.**
> 판별 기준: **그 단계들 사이에 외부 I/O 가 끼는가.**

- 낀다 → 흐름이다. Service 가 소유한다. (소셜 API·알림·파일 업로드·PG 응답을 사이에 두고
  갈라지는 순서)
- 안 낀다 → 한 커밋으로 묶일 수 있다. **쓰기 Implement 가 조합한다.** 트랜잭션 경계가 이미
  거기 있으므로, 조합을 Service 에 두면 경계와 조합이 서로 다른 레이어로 찢어진다.

찢어지면 생기는 일:

- **예외 번역이 트랜잭션 밖으로 밀린다.** 제약 위반은 flush/commit 시점에 나므로 경계 밖에서만
  잡히고, 그러면 번역 코드가 호출자 수만큼 복제된다.
- **DB 제약 이름 같은 storage 지식이 Service 로 샌다.** "예외는 Implement 에서 던진다" 원칙과
  정면으로 어긋난다.
- **라운드트립이 늘어난다.** 조합이 Service 에 있으면 각 단계가 독립 호출이 되어, 한 번에 할 수
  있는 조회가 N 번으로 갈라진다.

**조합 Implement 는 하위 Implement 를 주입받아 호출한다** — 같은 개념이든 다른 개념이든
(`MemberProvisioner` → `MemberManager`·`TermsFinder`·`TermsAgreementRecorder`). 조합이 존재
이유이므로 위의 "같은 개념 안에서는 참조하지 않는다"의 예외에 해당한다.

레이어를 불문하고 금지되는 참조는 **다른 개념의 Service** 하나다([concepts.md](concepts.md)) —
Service 는 흐름이므로, 흐름이 흐름을 부르면 어느 쪽을 읽어도 전체가 안 보인다.

한 커밋 안의 여러 쓰기를 조합하는 컴포넌트에는 책임이 드러나는 이름을 준다 —
`Provisioner`, `Processor`, `Handler` 등. 접미사 표는 참고 사전이지 강제가 아니다.

**유니크 충돌의 번역 위치도 이 선을 따른다.** 제약명으로 판별 가능한 충돌은 그 제약을 아는
쓰기 Implement 안에서 도메인 에러로 번역한다(`MemberManager.changeNickname` —
`uk_member_nickname` 을 아는 유일한 곳).

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

**판정한 쪽이 던진다.** Service 는 도구를 호출할 뿐 자기가 `requireBusiness`/`requireFound` 를
쓰지 않는다 ([errors.md](errors.md)).

| 무엇 | 던지는 곳 |
| --- | --- |
| "없음"의 의미 부여(어떤 E 코드인가) | 조회 책임을 가진 `Finder` (`get*` 안의 `requireFound`) |
| 규칙 위반 | 그 규칙을 판정한 쓰기 Implement (여러 엔티티 조회면 `Validator`) |
| 상태 전이 위반 | 쓰기 `Manager` (엔티티의 `can*` 판정을 도메인 에러로 매핑) |
| 제약 위반(유니크 등) | 그 제약을 아는 쓰기 Implement 또는 조합 Implement |

`Finder` 의 `exists*`/`is*` 는 **판정을 응답으로 돌려줄 때**(조회 API) 쓴다. 그 값을 Service 가
받아 `requireBusiness` 로 바꾸고 있다면 그 판정은 아래로 내려갈 자리다.

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
| Service | 없음 |
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
   그 조합 자체가 재사용 가능한 하나의 개념이다. (`MemberProvisioner` — 가입은 회원 생성 +
   필수 약관 자동 동의가 한 커밋. 예정 사례: 탈퇴는 회원·프로필·관심 소프트 삭제가
   한 커밋이어야 하므로 `MemberWithdrawProcessor` 하나가 감싼다.)

   실패한 쓰기를 잡아 **재시도**하는 조합은 메서드 전체를 `@Transactional` 로 묶으면
   rollback-only 가 된 같은 트랜잭션 안에서 재시도하게 되므로, 그 조합 Implement 가
   `TransactionTemplate` 으로 시도 단위 경계를 명시한다. (`MemberProvisioner.provision` —
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

- `MemberManager.append` — `saveAndFlush`. `MemberProvisioner` 가 닉네임 충돌을 잡아 재시도한다.
- `MemberManager.changeNickname` — `flush()` 직후 자기 안에서 `NICKNAME_DUPLICATED` 로 번역한다.
  제약명(`uk_member_nickname`)을 아는 곳이 번역까지 맡는다.

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
