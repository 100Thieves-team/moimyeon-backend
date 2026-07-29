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
- 미확정: 운영 스키마 마이그레이션 도구(Flyway 등)는 미도입. 그 전까지 스키마 변경은
  schema.sql 수정 + dev/live 수동 반영으로 관리한다.

## 시딩 체계: 세 파일의 역할

| 파일 | 내용 | 로드 경로 |
| --- | --- | --- |
| `schema.sql` | DDL | local·test 는 `sql.init`, 로컬 MySQL 은 docker initdb |
| `seed.sql` | **참조 데이터**(약관, 카탈로그). 스키마의 일부로 취급 | local·test 항상 로드. dev/live 는 수동 1회 |
| `data-local.sql` | 사람용 로컬 테스트 데이터(테스트 회원 등) | **docker initdb 전용** (앱 설정에 없음) |

- 테스트는 "스키마 + 참조 데이터"만 전제한다. 그 외 데이터는 각 테스트가 스스로 만든다.
  사람용 테스트 데이터가 테스트에 실리면 count 단언이 깨지므로 `data-local.sql` 은 앱 설정에 싣지 않는다.
- `test` 프로파일은 `spring.profiles.group` 으로 **local 을 상속**하고 데이터 로드만 오버라이드한다.
- 참조 데이터를 추가하는 PR 은 배포 시 dev/live 에 seed.sql 해당분 수동 반영이 필요함을 PR 본문에 명시한다.

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

- 도메인 상태가 삭제 의미를 이미 포함 (예: member 의 `MemberStatus.WITHDRAWN`)
- 삭제가 아닌 무효화 시각 컬럼으로 관리 (예: refresh_token 의 `revoked_at`)
- append-only 이력이거나, 부모 애그리거트가 물리 삭제를 관리

상속하지 않는 엔티티에는 **파일 상단 주석으로 이유를 남긴다** — 어떤 테이블이 어느 쪽인지의
원본은 문서가 아니라 각 엔티티 파일이다.

소프트 삭제된 행의 조회 제외는 각 Finder 의 파생 쿼리 소관이다(예: `...DeletedAtIsNull`).
전역 필터(@Where 등)는 쓰지 않는다 — 제외가 쿼리에 드러나야 한다.

### 공통

- `createdAt`/`updatedAt` 은 모든 테이블이 갖는다(`@CreationTimestamp`/`@UpdateTimestamp` —
  베이스 상속 시 베이스가, 직접 선언 시 엔티티가 제공).
- Kotlin + JPA 호환: `allOpen` 플러그인을 `@Entity`/`@MappedSuperclass`/`@Embeddable` 에 적용.
- 값 컬렉션은 `@ElementCollection` + `@CollectionTable` (예: `member_profile_interest` —
  프로필과 생명주기를 같이하므로 별도 엔티티를 만들지 않는다).
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
  `findByIdAndStatusNot(id, WITHDRAWN)` 계열 — Finder 가 이를 감싸 도메인 세계에 탈퇴 회원이
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
