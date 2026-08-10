package io.plady.moimyeon.core.domain.progress

interface RoomProgressManager {
    fun start(command: RoomProgressStartCommand): RoomProgressStartResult
}
