# PR 3 테스트 스켈레톤

체크포인트 B 시점의 초안이다. 이후 바뀐 속성(`leftRoomCount` 제거, `firstResume` → `activeResumeCount`, `reconfirmed` 추가)은 `decisions.md` I19·I23과 `plan.md` 6단계 사건표가 기준이다.

새 파일: `event/growth/GrowthEventListenerTest`(단위 3), `event/growth/GrowthEventCommitIT`(커밋·롤백 2).
아래는 기존 테스트 파일에 추가할 테스트 이름이다. 단위 테스트는 `GrowthEventPublisher`를 MockK로 받아 `verify`,
IT만 있는 클래스는 Spring `@RecordApplicationEvents`로 발행된 `GrowthEvent`를 확인한다.

| 파일 | 테스트 |
| --- | --- |
| `MemberRegistrarTest` | 가입하면 신규 회원 주체로 member.signed_up(provider)을 발행한다 |
| `MemberWithdrawerTest` | 실제로 탈퇴하면 member.withdrew(leftRoomCount)를 발행한다 / 이미 탈퇴했거나 재시도 반환이면 발행하지 않는다 |
| `MemberRestorerTest` | 탈퇴 계정을 복구하면 member.restored를 발행한다 / 탈퇴 상태가 아니면 발행하지 않는다 |
| `ResumeRegistrarTest` | 이력서를 등록하면 resume.registered(resumeId, firstResume)를 발행한다 (첫 이력서 true, 두 번째 false) |
| `RoomManagerTest` | 룸을 만들면 room.created(roomId, jobPostingId, jobRoleId, interviewStage, min/maxParticipants, daysUntilStart)를 발행한다 / 중복 요청이면 발행하지 않는다 / 확정하면 room.confirmed(participantCount, hoursSinceCreated) / cancelWithoutGuard는 room.canceled |
| `RoomApplicationSubmissionManagerTest` | 신청하면 신청자 주체로 room_application.submitted / 철회하면 room_application.withdrawn |
| `RoomApplicationManagerIT` | 수락하면 신청자 주체로 room_application.accepted(minutesSinceSubmitted) / 슬롯 초과로 닫히면 발행하지 않는다 / 반려하면 room_application.rejected |
| `RoomProgressManagerTest` | 방장 완료는 room.completed(completedBy=HOST, confirmedCount, attendedCount) / 자동 완료는 completedBy=SYSTEM / 완료 재전송은 발행하지 않는다 |
| `RoomLeaveManagerTest` | 참여자가 나가면 room_participant.left(role, roomStatus, byWithdrawal=false) / 탈퇴로 나가면 byWithdrawal=true / 나갈 것이 없어 조용히 반환하면 발행하지 않는다 / 최소 인원 미달로 재개되면 room.recruiting_reopened(PARTICIPANT_LEFT) / 방장 위임으로 재개되면 room.recruiting_reopened(HOST_LEFT) |
| `ReviewSubmissionManagerTest` | 후기를 제출하면 작성자 주체로 review.submitted(roomId, reviewId) |
| `ReviewSkipRecorderTest` | 처음 건너뛰면 review.skipped / 이미 건너뛴 기록이 있으면 발행하지 않는다 |
