package io.plady.moimyeon.storage.db.core

import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.MappedSuperclass
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

// admin 모듈의 엔티티는 db-core 의 @EntityScan 범위(이 패키지)에 두어
// core-api 런타임 조립 시 같은 EntityManagerFactory 로 함께 로드된다
@MappedSuperclass
abstract class AdminBaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0

    @CreationTimestamp
    val createdAt: LocalDateTime = LocalDateTime.MIN

    @UpdateTimestamp
    val updatedAt: LocalDateTime = LocalDateTime.MIN
}
