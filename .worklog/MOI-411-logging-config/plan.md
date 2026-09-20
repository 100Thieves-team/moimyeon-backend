# MOI-411 설계 작업

- [x] 2026-09-19 HTTP 요청 로깅 구현 승인: 사용자가 완료 보고를 승인했다. 검증·리뷰를 통과한 HTTP 연결 변경을 공통 기반 다음의 로컬 커밋으로 정리한다. push·PR·배포는 포함하지 않는다.

- [x] 2026-09-18 구현 세부 정책 승인: 미등록 메시지 숨김·안전 설정 강제·로컬 외부 전송 OFF·환경 정규화·추가 상한·메타데이터 및 요청 형식 제약, 라이브러리·의존성·Bean 구성을 사용자에게 설명하고 승인받았다. DR-26 참조.

- [x] 2026-09-14 사용자 운영 정책 확정: 추천안 채택, live는 팀원 3명 전원이 공동 담당. 상세는 `decisions.md` DR-22.

초기 요청 범위는 설계였다. 이후 승인으로 공통 설정·HTTP 연결·S3 저장 코드 구현까지 진행했다. 아래는 단계별 이력이며, 현재 상태는 마지막 S3 저장 슬라이스 기록을 따른다.

- [ ] 이슈·참고 코드·현재 로깅/배포 구성 확인: 조사 완료.
- [ ] S3 전송 경계·안전한 출력 계약·장애 정책·검증 순서 제안: 문서 작성 완료.
- [ ] 읽기 전용 QA 검토: Sentry, 필터 순서, 비동기 context, router 생명주기 보완 반영.

체크박스는 사람의 단계 승인 기록이므로 실행만 완료한 항목은 체크하지 않았다. 검증·push한 커밋 없음. 테스트·Terraform plan 미실행.

설계 문서: `_workspace/MOI-411_LOGGING_DESIGN.md`.

후속 요청에 따라 선택 이유·대안·대가·재검토 조건을 `decisions.md`에 별도로 정리했다. 사용자 요구사항과 설계 제안, 검증 전 수치를 구분했다.

2026-09-11 재정리: 서비스 추적 트랙과 그로스 트랙으로 문제·설계를 다시 묶은 문서는
`_workspace/MOI-411_LOGGING_TWO_TRACK.md`. 9/11 관측성 커밋(a5ced207)으로 DR-06·DR-07의
코드 근거가 바뀐 점을 그 문서 4절에 기록했다.

2026-09-11 최신화: 사용자 요청으로 PR #123(dev Grafana·Prometheus·Sentry) 반영. 설계 제안서·`decisions.md`(DR-03·06·07·10·11·검증 범위)·`context.md`·`tbd.md`·두 트랙 문서 4절을 `[9/11 갱신]` 표시로 수정했다. 로그 전송 경로 결정은 바뀌지 않았다. 코드·인프라 변경 없음.

2026-09-14 보강: 문제·대안·선택 이유·남는 비용 순서로 설계 문서를 다시 작성했다. 8종 로그, 수준별 의미, WARN 집계, ERROR 첫 발생·반복 묶음, DEBUG 3일, 환경별 설정·부팅 우선순위, 감사 경계를 추가했다. 기존 문서 스냅샷과 초안·윤문 기록은 `_workspace/2026-09-14-001/`에 둔다. 결정은 DR-13~21에 누적했다. 코드·인프라·외부 위키·알림 채널 변경은 없다.

2026-09-14 구현 착수 요청: 사용자가 설계 선행 커밋 후 공통 로깅 설정과 테스트 작성을 요청했다. 현재 작업 브랜치는 feat/MOI-411-logging-config다. 설계 원본을 이 디렉터리로 옮겨 체크아웃 후에도 읽을 수 있게 했다. 로컬 커밋만 수행하며 push·PR·배포는 이번 요청에 포함하지 않는다.

## 첫 설정 슬라이스

프로세스가 시작되면 활성 프로파일로 로그 환경과 출력 형식을 정한다. 상충하는 기본 환경은 시작을 거부하며, 로컬·테스트 실행은 외부 로그 전송을 끈다. 출력은 민감한 메시지·인자를 그대로 내보내지 않는 공통 설정을 통과한다.

테스트는 staging과 dev/perf 부팅, test/local 상속, 환경 충돌, 로컬 외부 전송 차단, kotlin-logging의 실제 출력 및 민감값·SQL 출력 차단을 먼저 작성한다. 변경 범위는 support:logging과 필요한 공통 빌드·DB 로깅 설정이다. HTTP 필터·ErrorType 재분류·S3 전송·알림은 다음 슬라이스이며 API 계약은 바꾸지 않는다.

설계 커밋 전 검증: 최신 origin/dev(e65ebcdc)에서 `./gradlew test ktlintCheck` 통과. 문서 링크·공백·시크릿 게이트 통과. 앞선 읽기 전용 QA의 설계 지적은 반영 완료 상태로 가져왔다.

## 설정 슬라이스 결과

설계 선행 커밋은 `de130a92`다. 실제 Boot 설정 테스트를 먼저 실패시킨 뒤 공통 facade 의존·고정 XML·환경 선택·안전한 출력·SQL 우회 차단을 구현했다. 코드 리뷰의 부팅 순서 지적과 QA의 logging.config 우회 지적을 회귀 테스트로 확인해 수정했다.

최종 `./gradlew test ktlintCheck` 통과, 로깅 테스트 20개 중 신규 14개다. code-reviewer와 qa-reviewer 재검토도 통과했다. 구체적인 동작과 후속 범위는 [구현 기록](implementation.md)에 있다. 구현 변경은 미커밋 상태이며 push·PR·배포는 수행하지 않았다.

예제 반영 최종 검증: 로깅 테스트 28개(이번 후속 작업에서 8개 추가)와 루트 `./gradlew test ktlintCheck` 통과. code-reviewer·qa-reviewer 모두 추가 필수 지적 없음. 경로 템플릿의 신뢰할 출처는 후속 HTTP 연결의 책임임을 README와 코드에 명시했다. 변경은 미커밋 상태다.

환경별 XML 유지 요청 반영: local/local-dev/dev/live XML을 복구하고 test/staging/dev-perf/bootstrap XML을 추가했다. 공통 encoder만 include로 공유하며 수준·형식·appender 연결은 환경별 파일이 소유한다. Kotlin enum의 수준·형식 필드는 제거했다. dev,perf와 perf,dev의 같은 파일 선택, test/local 상속, 잘못된 환경의 안전한 부팅 실패를 검증했다. code-reviewer 재검토 통과.

환경별 XML 변경 후 최종 `./gradlew test ktlintCheck` 통과. XML 파싱·공통 include 경로·커밋 전 게이트 통과. 변경은 미커밋 상태로 유지한다.


## HTTP 요청 연결

2026-09-18 사용자 진행 승인에 따라 공통 기반을 77801ac9로 커밋했다. 다음 동작을 테스트부터 구현한다: 요청별 서버 발급 ID와 MDC scope를 열고, 인증 필터·MVC·오류 및 비동기 처리가 끝난 뒤 최종 상태와 등록된 경로를 한 번 기록한다. 로그 실패는 응답을 바꾸지 않는다.

실제 내장 Tomcat으로 성공·400·401·403·OAuth·미매칭·예외·비동기 완료를 확인한다. 본문을 캐싱하지 않고 응답 payload를 보존한다. 공통 모듈은 Servlet/Security 의존 없이 유지하며 HTTP 어댑터는 core-api에 둔다. S3·알림과 비즈니스 ErrorType 수준 변경은 이번 범위에서 제외한다.

HTTP 연결 구현 완료: 단위·실제 Tomcat 테스트 14개, 공통 로깅 테스트 29개와 전체 `test ktlintCheck` 통과. async 재디스패치가 최초 경로를 덮는 문제를 실패 테스트로 확인해 수정했다. QA의 실제 OTel 검증 요구를 반영해 sampling=0인 HTTP span·handler trace·완료 JSON 연결을 확인했고 최종 code/QA 리뷰를 통과했다. HTTP 후속 변경은 미커밋 상태로 남기며 push·PR·배포는 수행하지 않았다.


## S3 저장 슬라이스

2026-09-19 사용자 진행 요청. 최신 origin/dev(0a257c27)로 rebase했고 기존 세 커밋은 a809ea1e/f86b13cb/c32d2569로 바뀌었다. dev에 FireLens 라우터와 로그 전용 S3/CW 저장소를 추가하고 live는 비활성 기본값을 유지한다. 설정 파일은 버전별 S3 객체로 배포하며 과거 task definition 롤백이 이전 설정을 계속 읽을 수 있도록 보존한다.

Terraform fmt·validate·격리된 mock plan 테스트와 실제 Fluent Bit 컨테이너의 로컬 S3/CW 대역 전송을 검증한다. 실제 환경 plan은 기존 PR CI의 sanitized 요약만 확인한다. 로컬·에이전트 apply와 운영 리소스 변경은 수행하지 않는다.

S3 로컬 검증: Terraform 1.15.9 fmt, shared/dev/live validate, mock provider plan 4개, 배포 입력 17개 및 인프라 셸 계약 검사, 전체 Gradle test·ktlintCheck 통과. 기존 Redis service discovery의 failure_threshold deprecation 경고가 남아 있다. QA에서 찾은 CI 설정 refresh 권한과 provision DEBUG 보존 문제를 수정했다.

실컨테이너 테스트에서 S3 503 뒤 강제 종료·동일 볼륨 복구까지 추가했다. AWS for Fluent Bit 2.x의 복구 파일 NUL 실패를 재현했고, 3.4.17 digest 고정으로 교체해 같은 시나리오와 실제 HTTP 생존 명령을 통과했다. 출력 카운터 대신 실제 gzip 객체를 해제해 모든 행을 파싱한다.

현재까지 실제 AWS plan·apply·배포·push·PR 생성은 수행하지 않았다. 검토 가능한 PR 본문은 `pr-draft.md`에 있다. 실제 CI plan은 PR 생성 후 sanitized 요약으로 확인해야 하며, 머지는 자동 apply 승인이다. 로컬 결과만으로 이슈를 완료 처리하지 않는다.

최종 리뷰: code-reviewer 필수 지적 없음, qa-reviewer PASS. 후속 권고인 CW 전송 확인 뒤 SIGKILL, 예상 외 CW API 거부, 실제 router HTTP 명령 검증을 반영했다. health 경로는 부팅 직후 metrics 생성에 의존하지 않는 `/`다. 라우터 메모리는 hard limit만 지정해 같은 값의 soft/hard 동시 지정도 피했다. 변경 파일 시크릿 검사·Markdown 상대 링크 검사·git diff --check 통과. 이번 S3 변경은 미커밋 상태이며 PR 초안 승인 단계에서 대기한다.

2026-09-20 보고서 요청: `docs/architecture/logging-report.md`에 문제·구조·트레이드오프·동작 변화·개발/운영 활용·남은 범위를 통합했다. 아키텍처와 HTTP/저장 실패/배포 시퀀스 4개를 Mermaid 원본과 SVG로 추가했고, HTML 열람본은 `_workspace/MOI-411-report/logging-report.html`에 만들었다. 기존 README·목표 설계의 진입 링크와 오래된 구현 상태 표현을 갱신했다. 읽기 전용 사실 리뷰의 자동 apply 조건 지적을 반영했다. 그림 렌더링·HTML 이미지/가로 넘침·JSON/분석 예시·상대 링크·공백·문서 시크릿 검사를 확인했다. 이번 요청에서 실행 코드·인프라 설정은 추가 수정하지 않았고 커밋·push·PR·AWS 작업도 수행하지 않았다.


- [x] 2026-09-20 사용자가 PR 게시를 요청했다. 기존 초안을 기준으로 커밋·push·draft PR 생성과 CI sanitized plan 확인을 진행한다. 머지·apply 승인은 포함하지 않는다.

PR 전 재검증: 최신 origin/dev 추가 변경 없음. 전체 Gradle test·ktlintCheck, Terraform fmt·mock plan 4개, 배포 입력 17개와 인프라 셸 계약 통과. 기존 코드·QA PASS와 문서 사실 검토를 유지한다. 팀 가이드는 LLM Wiki `topics/t-moimyeon-로깅-아키텍처와-사용-가이드`에 게시했고, Mermaid 4개·본문 재조회·Wiki lint 검증을 마쳤다.
