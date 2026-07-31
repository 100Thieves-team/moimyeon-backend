# 개념·격벽 설계

[← 허브로](README.md)

설계의 기본 단위는 "개념(Concept)"이다. 행위·상태·불변식이 함께 움직이는 덩어리를 하나의 개념으로 묶고,
개념 사이에는 격벽(Bulkhead)을 세워 **지식의 방향**을 통제한다.

## 패키지 구조

`core.domain` 은 flat 이 아니라 **개념 단위 하위 패키지**로 나눈다.
한 개념 패키지 안에 도메인 모델·Service·Implement(Finder/Manager/Mapper)를 함께 둔다.

```text
core.domain
└── {개념}            개념 명사 패키지. 예: member
    ├── {도메인 모델}   예: Member, Nickname(VO)
    ├── {개념}Service   예: MemberService
    └── {Implement}     예: MemberFinder, MemberManager, MemberMapper, NicknameGenerator
```

어느 패키지에 둘지 애매하면 "이 행위·상태가 누구의 불변식과 함께 움직이는가"로 판단한다.
두 개념을 잇는 부수 개념(연결 이력 등)은 판정 책임을 가진 쪽 패키지에 둔다
(예: 약관 동의 이력은 terms 패키지 — "필수 약관 전부 동의?" 판정을 terms 쪽 Finder 가 소유).

## 개념 간 지식 방향

격벽의 핵심 질문: **"누가 누구를 알아야 하는가?"** 참조가 필요해도 지식은 최소로 유지한다.

- **id 참조가 기본**: 다른 개념을 알아야 하면 도메인 객체가 아니라 **식별자만** 든다.
  예: Profile 은 Member 의 id 만 안다(`MemberProfile.memberId: UUID`). 역방향(Member 가
  프로필의 존재를 아는 것)은 필요해질 때까지 만들지 않는다.
- **파생 가능한 사실은 저장하지 않는다**: 다른 데이터에서 계산되는 값을 중복 저장하면 정합성
  관리 대상만 늘어난다. 예: 프로필의 "관심 회사 수"는 컬럼이 아니라 조인 행을 세는 계산이다.
- **다른 개념의 규칙은 그 개념의 도구에게 묻는다**: 여러 개념이 얽힌 규칙은 상대 개념의 내부를
  뒤지지 않고, 상대가 노출한 판정(`TermsAgreementFinder.hasAgreedAllRequiredActive` 등)을 쓴다.
  그 판정을 도메인 에러로 바꾸는 것은 **그 규칙이 걸리는 쓰기 Implement** 의 일이다
  (예: 프로필 생성 시 약관 동의 확인 → `ProfileManager.append` 안). Service 로 올리면 같은
  매핑이 호출자마다 복제된다.
- **성격이 같으면 한 격벽**: 데이터가 여럿이라도 소유자·수명·접근 방식이 같으면 하나의 개념으로
  묶는다(예: 크롤러 소유 읽기 전용 참조 데이터들 → `catalog` 하나). 성격이 갈라지면 그때 쪼갠다.
- **외부는 도메인이 모른다**: 외부 API·소셜 로그인·크롤러 같은 바깥 세계는 우리 의도와 무관하게
  변하므로 도메인 영역이 직접 참조하지 않는다. 별도 모듈(clients, security)로 격리하고
  ([modules.md](modules.md)), 외부 데이터가 필요하면 내부 테이블로 적재해서 읽는다
  (예: 크롤러가 채우는 참조 데이터를 앱은 읽기만 한다).

## 격벽을 지키는 규칙

- 다른 개념의 **Service 참조는 금지**, 다른 개념의 **Implement(Finder 등) 참조는 허용**한다.
- **격벽을 넘는 것은 Implement 의 일이다.** Service 는 자기 개념의 Implement 를 보고, 그 Implement
  가 필요하면 다른 격벽의 컴포넌트를 본다. 결제·취소·정산처럼 **경계에 걸친 행위**는 여러 개념의
  데이터를 한 커밋으로 다뤄야 하므로 그 조합 Implement 가 그 일을 맡는다.
- **다른 개념이 필요하면 그 개념의 로직 클래스를 우선 쓴다.** Repository 직접 접근은 금지가 아니라
  차선이다 — 남의 엔티티를 자기 커밋 안에서 변경해야 하거나, 락 획득이 목적이거나, 위임할 로직
  클래스가 아직 없을 때만 내려가고 그 이유를 주석으로 남긴다. `CancelProcessor` 가 쿠폰·포인트는
  `OwnedCouponUsageManager`·`PointHandler` 에 위임하면서 order 엔티티는 Repository 로 직접 다루는
  이유가 그것이다([layers.md](layers.md)).
  단순 조회 컴포넌트(`Finder`/`Reader`)는 그럴 일이 없다 — 자기 개념만 본다.
- **개념 객체가 다른 개념을 이미 알고 있으면 Service 가 그 개념의 Implement 를 직접 써도 된다.**
  `OrderItem` 이 `productId`·`productName` 을 들고 있으니 `OrderService` 가 `ProductFinder` 를
  주입받는다. `MemberProfile` 이 관심 직무·회사·지역 id 를 들고 있으니 `ProfileService` 가
  `CatalogRefValidator` 를 쓴다.
  반대로 **Member 는 프로필의 존재를 모르므로** `MemberService` 는 `ProfileFinder` 를 쓰지 않는다 —
  둘을 함께 보여줘야 하는 응답은 `MemberFacade` 가 조합한다.
- **자기 개념 안에서는 Repository 를 직접 본다.** 같은 개념의 Implement 를 한 줄 위임으로 끼우지
  않는다 — 격벽이 없는 자리에 컴포넌트를 세우면 의존 그래프만 복잡해진다([layers.md](layers.md)).
- **흐름 분기를 Implement 에 숨기지 않는다.** `findOrRegister` 류의 합성 메서드 금지 —
  조회냐 등록이냐는 흐름이므로 Service 가 명시적으로 분기한다
  (`SocialAuthService.authenticate` — 기존 회원이면 로그인, 아니면 provisioning).
  단 **한 커밋 단위의 조합은 Implement 가 소유한다** — 두 개념의 쓰기가 함께 커밋돼야 하면
  그 조합 자체가 하나의 개념이다(`MemberProvisioner` — 회원 생성 + 필수 약관 자동 동의).
  판별 기준(그 단계들 사이에 외부 I/O 가 끼는가)은 [layers.md](layers.md)의
  "Implement 도 조합한다" 절.
- 상태로 개념을 오염시키지 않는다. 예: "탈퇴한 회원"은 도메인 세계의 `Member` 개념에 들어오지 못하게
  **persistence 경계(Finder)에서 걸러낸다**. 도메인 객체는 상태 분기를 모른다 ([layers.md](layers.md)).
- 개념 규칙은 값 객체가 보증한다. 예: 닉네임 형식·길이·금칙어는 `Nickname` VO 생성 시점에 검증된다.
  잘못된 값은 도메인 세계에 아예 존재할 수 없다.
- **도메인 객체의 필드는 nullable 하지 않는 것을 지향한다.** null 은 업무적으로 정말 "없을 수 있는"
  값(선택 입력 등)에만 허용한다. 조회 비용을 아끼거나 부분 수정을 편하게 하려는 목적으로 필드를
  nullable 로 만들지 않는다 — 그 상황은 필드를 비우는 대신, 그 행위만 담는 별도 객체
  (예: 닉네임 변경이면 변경에 필요한 값만 가진 행위 객체)를 만들거나 전체를 조회해 갱신하는 쪽으로
  푼다. 반쯤 채워진 도메인 객체가 돌아다니기 시작하면 모든 사용처가 null 분기를 떠안는다.

## 새 개념을 추가할 때

경계를 미리 완성하려 하지 않는다. 단순하게 구현을 시작하고, 한 클래스에 서로 다른 영역의 일이
뭉치기 시작하거나 특정 클래스가 사방에서 주입되는 신호가 보이면 그때 경계를 긋는다.
처음의 경계 판단이 구현 중에 뒤집히는 것은 자연스러운 일이다.

경계를 그을 때 확인할 것:

- 기존 개념과의 참조가 id 로 충분한가, 판정 입력으로 충분한가.
- `core.domain.{개념}` 패키지에 모델·Service·Implement 를 함께 두었는가.
- 에러 코드 번호대를 할당했는가 ([errors.md](errors.md)).
