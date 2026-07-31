# 스토리지 (storage:db-core)

[← 허브로](README.md)

## 스키마 관리: schema.sql 이 단일 소스

- 테이블/타입/제약/인덱스는 `storage/db-core/src/main/resources/schema.sql` 에서 관리한다.
  **엔티티는 매핑만 담당**한다 — 유니크 제약 하나만 예외로 엔티티에도 선언한다(아래 JPA 매핑 규칙).
- H2(`MODE=MySQL`)와 MySQL 8 **모두에서 도는 공통 문법만** 쓴다(ENGINE/CHARSET, `ON UPDATE
  CURRENT_TIMESTAMP` 같은 방언 지시 금지).
- 재실행 안전을 위해 DROP IF EXISTS(자식→부모) 후 CREATE(부모→자식). DROP 은 인메모리 H2
  재초기화·docker 최초 initdb 용이다. 데이터가 있는 영속 DB 에 수동 실행하면 파괴적이므로 금지.
- `ddl-auto`: 공통 `validate` (엔티티-스키마 불일치를 부팅에서 검출), local 은 `none` + `sql.init`.

## 영속 DB 변경: Flyway 마이그레이션

- 반영 주체가 환경마다 다르다. local·test(H2)는 `schema.sql`, dev·staging·live 는
  `db/migration/V*.sql` 이다(`spring.flyway.enabled` 로 가른다). 앱 부팅 시 Flyway 가
  JPA 보다 먼저 실행되고, 그 결과를 `ddl-auto=validate` 가 검증한다.
- **`schema.sql` 은 여전히 단일 소스다.** 스키마를 바꾸는 PR 은 `schema.sql` 수정과
  마이그레이션 추가를 **함께** 한다. `schema.sql` 은 "지금의 전체 모습", 마이그레이션은
  "거기까지 간 경로"이고 둘의 결과는 같아야 한다.
- 마이그레이션은 MySQL 에서만 실행되므로 MySQL 방언을 써도 된다. `schema.sql` 은 H2 공통 문법 제약을
  그대로 받는다 — 그래서 의도적으로 어긋나는 부분이 있다(`job_posting.content` 의 MEDIUMTEXT·FULLTEXT,
  크롤러 테이블의 `ON UPDATE CURRENT_TIMESTAMP` 와 FK). 그 목록은 `schema.sql` 상단 ⚠️ 블록에 있다.
- 파일명은 충돌을 피하려고 순번 대신 의미를 쓴다: `V<번호>__<무엇을_하는지>.sql`.
  번호는 머지 순서대로 이어 붙이되, 브랜치가 겹치면 뒤에 머지되는 쪽이 번호를 올린다.
- **적용된 마이그레이션은 절대 수정하지 않는다**(체크섬이 깨져 부팅이 막힌다). 잘못 썼으면
  되돌리는 새 파일을 추가한다. 약관 개정처럼 데이터가 바뀌는 것도 새 파일이다.
- 데이터가 있는 테이블은 `ALTER` 로만 바꾼다. `DROP TABLE`·`TRUNCATE` 는 0행이 확인된 테이블에만 쓰고,
  그 전제를 마이그레이션 안에서 가드로 검증한다(V3 참고 — 행이 있으면 스스로 실패한다).
- 크롤러 소유 테이블(`sido`·`sigungu`·`job_group`·`job_role`·`company`·`job_posting`·`job_posting_role`)의
  FK 는 남긴다. "FK 를 걸지 않는다"는 앱 테이블 규칙이고, 크롤러 FK 는 적재 파이프라인이 쓰는 장치다.
- `V1__baseline_dev_snapshot.sql` 은 Flyway 도입 시점 dev 의 실제 모습이라 컨벤션에 어긋나는
  이름·타입이 들어 있다. 의도된 것이며 V2 가 바로잡는다.

## 시딩 체계: 세 파일의 역할

| 파일 | 내용 | 로드 경로 |
| --- | --- | --- |
| `schema.sql` | DDL | local·test 는 `sql.init`, 로컬 MySQL 은 docker initdb |
| `seed.sql` | **참조 데이터**(약관, 카탈로그). 스키마의 일부로 취급 | local·test 항상 로드. dev/live 는 마이그레이션(V5) |
| `data-local.sql` | 사람용 로컬 테스트 데이터(테스트 회원 등) | **docker initdb 전용** (앱 설정에 없음) |

- 테스트는 "스키마 + 참조 데이터"만 전제한다. 그 외 데이터는 각 테스트가 스스로 만든다.
  사람용 테스트 데이터가 테스트에 실리면 count 단언이 깨지므로 `data-local.sql` 은 앱 설정에 싣지 않는다.
- `test` 프로파일은 `spring.profiles.group` 으로 **local 을 상속**하고 데이터 로드만 오버라이드한다.
- 참조 데이터를 추가하는 PR 은 `seed.sql` 과 마이그레이션에 같은 INSERT 를 함께 넣는다.
  수동 반영에 기대면 환경마다 어긋난다 — dev 의 약관이 오래 비어 있었던 것이 그 사례다.
  단, `seed.sql` 의 카탈로그 최소 시드(sido·sigungu·job_group·job_role·company)는 마이그레이션에
  넣지 않는다. dev/live 에는 크롤러가 채운 진짜 데이터가 있고 고정 id 가 충돌한다.

## 엔티티 스타일

### 베이스 엔티티 계층

```text
AbstractEntity (@MappedSuperclass, id 없음)
│   deletedAt: LocalDateTime? (NULL=유효, private) + createdAt/updatedAt
│   active() / isActive() / delete(now) / isDeleted()
├─ BaseEntity      : + Long id (IDENTITY)
└─ UuidBaseEntity  : + UUID id (BINARY(16))
```

- **표준 베이스는 소프트 삭제(deleted_at 컬럼 방식)를 내장한다.** `BaseEntity`/`UuidBaseEntity` 를
  상속하면 물리 삭제 대신 `delete(now)`(deleted_at 세팅)로 처리하고, 스키마에
  `deleted_at DATETIME NULL` 컬럼이 있어야 한다(`ddl-auto=validate` 가 검출).
- `deletedAt` 은 `private` — 외부에서 대입하지 않고 `active()`/`delete(now)`/`isDeleted()`
  의도 메서드로만 다룬다. 삭제 시각은 호출자가 주입한다(테스트 재현성).
- id 타입 선택: 외부(API)에 노출되는 식별자면 `UuidBaseEntity`, 내부 식별이면 `BaseEntity`.

**별도 라이프사이클이 있는 엔티티는 베이스를 상속하지 않는다.** 상태 컬럼이 둘이 되면 개념이
흐려지므로, 애초에 상속하지 말고 **id·createdAt/updatedAt 을 엔티티에 직접 선언**한다.
"별도 라이프사이클"의 예:

- 삭제가 아닌 무효화 시각 컬럼으로 관리 (예: refresh_token 의 `revoked_at`)
- 부모 애그리거트가 삭제를 관리 (예: social_account — 삭제 시각은 member 의 `deleted_at`)

**행의 존재 여부는 `deleted_at` 하나로만 표현한다.** 도메인 상태 enum 에 삭제 의미를 겹쳐 담지
않는다 — 한 사실을 두 컬럼으로 들면 둘을 동기화하는 불변식이 따라붙는다. member 가 그 예로,
`MemberStatus.WITHDRAWN` + `withdrawn_at` 을 `deleted_at` 하나로 합쳤고 `MemberStatus` 에는
그와 직교하는 제재 상태(ACTIVE/RESTRICTED)만 남겼다.

상속하지 않는 엔티티에는 **파일 상단 주석으로 이유를 남긴다** — 어떤 테이블이 어느 쪽인지의
원본은 문서가 아니라 각 엔티티 파일이다.

소프트 삭제된 행의 조회 제외는 각 Finder 의 파생 쿼리 소관이다(예: `...DeletedAtIsNull`).
전역 필터(@Where 등)는 쓰지 않는다 — 제외가 쿼리에 드러나야 한다.

### 소프트 삭제와 유니크 제약

유니크 인덱스는 NULL 을 서로 다른 값으로 취급하므로 `UNIQUE(col, deleted_at)` 은 방향이 반대다
(활성 중복이 통과하고, 같은 시각에 삭제된 행이 차단된다). "살아있는 행끼리만 유일"이 필요하면
NULL 의 방향을 뒤집는 생성 컬럼을 둔다:

```sql
_active_check BOOLEAN GENERATED ALWAYS AS (CASE WHEN deleted_at IS NULL THEN TRUE ELSE NULL END),
CONSTRAINT uk_xxx_active UNIQUE (col, _active_check)
```

- `STORED`/`VIRTUAL` 키워드는 **쓰지 않는다** — H2 가 파싱하지 못한다. MySQL 은 생략 시 VIRTUAL 이
  기본이고, VIRTUAL 이어도 세컨더리 유니크 인덱스는 정상 동작하며 `ADD COLUMN` 이 in-place 로 끝난다.
- 표현식은 `CASE WHEN` 을 쓴다(`IF()` 는 H2 미지원).
- 엔티티의 `@Table(uniqueConstraints = ...)` 에도 `_active_check` 를 포함한 실제 제약 이름·컬럼으로 선언한다.
- **기본값은 이 컬럼을 두지 않는 것이다.** 삭제된 행이 키를 계속 점유해야 하는 제약도 있다
  (예: `uk_member_nickname` 은 탈퇴자 포함 전체 유일, `uk_social_account_provider_provider_id` 는
  탈퇴자 재가입 차단이 이 점유에 의존한다). 적용 여부는 제약별 제품 결정이다.
- PK 충돌은 이 방법으로 풀리지 않는다(예: member_profile 의 `member_id`). 되살리기로 처리한다.

### 공통

- `createdAt`/`updatedAt` 은 모든 테이블이 갖는다(`@CreationTimestamp`/`@UpdateTimestamp` —
  베이스 상속 시 베이스가, 직접 선언 시 엔티티가 제공).
- Kotlin + JPA 호환: `allOpen` 플러그인을 `@Entity`/`@MappedSuperclass`/`@Embeddable` 에 적용.
- 값 컬렉션은 `@ElementCollection` + `@CollectionTable` (예: `review_tag` — 마스터 테이블이 없는
  열거 문자열이라 별도 엔티티를 만들지 않는다).
  **기준은 "마스터 테이블이 없는 단순 값인가"다.** 양쪽이 다 엔티티인 M:N 의 조인 테이블은
  값 컬렉션으로 두지 않고 **조인 테이블도 엔티티로 만든다**
  (예: `member_profile_interest_company`, `member_profile_interest_job_role`).
  값 컬렉션에는 세 가지 대가가 따르기 때문이다.
  - Hibernate 가 한 건만 바뀌어도 소유자의 행을 **전량 DELETE 후 재삽입**한다.
  - 레포지터리가 없어 "이 직무에 관심 있는 회원" 같은 반대 방향 조회를 못 한다.
  - `deleted_at` 이 없어 "웬만하면 남긴다"는 소프트 삭제 원칙에서 그 테이블만 빠진다.

  뒤집어 말하면, 조인에 속성이 붙을 여지가 조금이라도 있으면(관심 등록 시각, 우선순위)
  처음부터 엔티티로 만든다. 나중에 갈아엎는 비용이 훨씬 크다.
- 이력 테이블은 append-only 로 선언하고(주석) 애플리케이션에서 UPDATE/DELETE 하지 않는다
  (예: `terms_agreement`).

## JPA 매핑 규칙

- **연관관계 매핑(@OneToMany, @ManyToOne 등)은 기본적으로 걸지 않는다.** 다른 테이블 참조는
  id 값(`memberId: UUID`, `jobRoleId: Long`)으로 든다. 연관관계는 두 테이블의 라이프사이클이
  정확히 함께 움직인다고 판단될 때 그때 가서 **고려**한다
  (예: 회원 ↔ 소셜 계정 — 회원과 함께 생성·삭제되므로 cascade + orphanRemoval 연관관계).
- **엔티티에 적는 제약은 유니크뿐이다.** 업무적으로 의미 있는 유일성(닉네임 전역 유일 등)은
  `@Table(uniqueConstraints = [UniqueConstraint(name = ..., columnNames = [...])])` 로
  **스키마와 같은 제약 이름**으로 선언한다. 유니크 충돌 catch 매핑([errors.md](errors.md))이
  제약 이름을 읽으므로, 코드에서 어떤 유니크가 있는지 보이는 것이 중요하다.
- **그 외 제약(길이, null 여부, 기본값)과 인덱스는 엔티티에 적지 않는다.** schema.sql 이 관리한다.
  `ddl-auto=validate` 는 타입·컬럼 존재만 검증하므로 엔티티에 중복 선언할 이유가 없다.
- **FK 제약도 기본적으로 걸지 않는다.** 참조 무결성은 저장 전 명시 검증이 담당한다
  ([errors.md](errors.md)의 참조 무결성 사전 검증).

## Repository

- `interface XxxRepository : JpaRepository<XxxEntity, ID>` 파생 쿼리 우선, 복잡하면 `@Query`(JPQL).
- **조회 메서드는 도메인 규칙을 반영한 이름으로 뚫는다.** 예: 살아있는 회원 조회는
  `findByIdAndDeletedAtIsNull(id)` 계열 — Finder 가 이를 감싸 도메인 세계에 탈퇴 회원이
  들어오지 않게 한다 ([layers.md](layers.md)).
- N+1 대응: `hibernate.default_batch_fetch_size: 100` 전역 + 필요 시 fetch join.
- `open-in-view: false`.

## 참조 데이터 (크롤러 소유)

지역·직업유형·회사 같은 카탈로그성 테이블은 **크롤러가 소유**하는 참조 데이터다.

- 폐기는 표준 베이스의 소프트 삭제(`deleted_at`, NULL=유효)로 관리한다. 재크롤 시 미출현이면 크롤러가 세팅한다.
- 업무키(각종 `code`, `corp_code`)에 유니크를 걸어 재적재 멱등성을 보장한다.
- `updated_at` 갱신은 크롤러(갱신 주체)가 직접 세팅한다(`ON UPDATE CURRENT_TIMESTAMP` 는 H2 미지원이라 제외).
- 앱(core-api)은 **읽기 전용**으로만 접근한다. 유효 데이터 조회는 `deleted_at IS NULL` 조건.
- FE 개발용 최소 시드를 seed.sql 에 둔다(업무키 기준이라 크롤러 적재와 충돌하지 않음).

## 테스트

- Repository 통합 테스트는 `CoreDbContextTest` 상속(H2, 생성자 주입) + `@Transactional`.
  참조 id 가 실제 존재하는 데이터가 되도록 부모(회원 등)를 먼저 persist 하는 헬퍼를 테스트 안에
  둔다 ([testing.md](testing.md)).
