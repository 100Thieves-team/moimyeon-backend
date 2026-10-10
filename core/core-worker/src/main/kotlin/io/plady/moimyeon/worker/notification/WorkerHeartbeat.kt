package io.plady.moimyeon.worker.notification

/**
 * 소비 주기 하나가 끝까지 돌았음을 알린다. ECS 컨테이너 상태 검사가 이 신호로 Worker의 준비 여부를 판단한다(MOI-594).
 */
fun interface WorkerHeartbeat {
    fun beat()
}
