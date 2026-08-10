package io.plady.moimyeon.core.domain.room

import org.springframework.stereotype.Component
import java.util.UUID

@Component
class RoomParticipantResumeFinder {
    fun get(roomId: UUID, participantMemberId: UUID): RoomParticipantResume {
        // TODO(룸 관리): 룸 생명주기에 묶인 참여자 제출 이력서 모델이 구현되면 이 조회 계약에 연동한다.
        TODO("룸 참여자 제출 이력서 조회 구현 필요: roomId=$roomId, participantMemberId=$participantMemberId")
    }
}
