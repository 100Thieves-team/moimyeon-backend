-- V36 — 개인정보 국외 이전 필수 약관 (MOI-521, PRD 「회원 및 프로필」 R180·R181)
--
-- 그로스 분석 도구(PostHog, 미국)로 가명 회원 식별자와 이용 기록을 보내므로 가입 시 필수 동의를 받는다.
-- 가입 자동 동의(TermsAgreementManager.agreeRequired)는 활성 필수 약관 전체에 기록하므로 이 행만 추가하면 된다.
-- TermsPublication 은 모든 TermsType 에 활성 약관을 요구한다. enum 과 이 행은 같은 배포에 들어가야 한다.
--
-- 정식 출시 전이라 기존 회원은 내부·테스트 계정뿐이므로 일괄 동의 처리한다(MOI-521 D16).
-- 본문은 법률 검토 전 초안이며(D18), 검토 결과는 새 버전으로 낸다. 이 파일은 고치지 않는다.

INSERT INTO terms (id, type, version, title, content, required, effective_from, status, created_at, updated_at)
VALUES (X'0199cd6e3c0c7a2e9f41b5d6a8c3e721', 'OVERSEAS_TRANSFER', 'v1.0', '개인정보 국외 이전 동의',
'# 개인정보 국외 이전 동의

문서 버전: v1.0 · 시행일: 2026년 10월 10일

모이면은 서비스 이용 행태를 분석해 서비스를 개선하기 위해 아래와 같이 개인정보를 국외로 이전합니다. 이 동의는 회원 가입의 필수 항목입니다.

| 항목 | 내용 |
| --- | --- |
| 이전받는 자 | PostHog, Inc. **[확정 필요: 개인정보 문의 연락처]** |
| 이전되는 국가 | 미국 |
| 이전 일시 및 방법 | 서비스 이용 시점에 네트워크를 통해 수시로 전송 |
| 이전되는 항목 | 가명 처리된 회원 식별자, 서비스 이용 기록(화면 조회, 버튼 선택, 모임 생성·신청·참여·완료·후기 작성 등의 기록), 접속 기기·브라우저 정보, 유입 경로(광고 캠페인 정보 포함) |
| 이전 목적 | 서비스 이용 통계 분석과 서비스 개선 |
| 보유 및 이용 기간 | 회원 탈퇴 시 지체 없이 삭제. 비회원 이용 기록은 **[확정 필요: 분석 도구 보관 기간]** 후 삭제 |

회원 식별자는 이름·이메일 등 회원을 직접 알아볼 수 있는 정보와 분리해, 별도로 관리하는 키로 변환한 값만 전송합니다. 이름, 이메일, 이력서·후기·질문 내용은 전송하지 않습니다.

동의를 원하지 않으시면 회원 가입을 하지 않으실 수 있으며, 가입 후에는 회원 탈퇴로 이전을 중단할 수 있습니다. 탈퇴하면 이미 이전된 기록도 삭제합니다.',
        TRUE, '2026-10-10 00:00:00', 'ACTIVE', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6));

INSERT INTO terms_agreement (id, member_id, terms_id, agreed_at, created_at, updated_at)
SELECT UUID_TO_BIN(UUID()), m.id, X'0199cd6e3c0c7a2e9f41b5d6a8c3e721', CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM member m
WHERE NOT EXISTS (
    SELECT 1 FROM terms_agreement a
    WHERE a.member_id = m.id AND a.terms_id = X'0199cd6e3c0c7a2e9f41b5d6a8c3e721' AND a.deleted_at IS NULL
);
