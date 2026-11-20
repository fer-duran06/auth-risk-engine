package com.authrisk.infrastructure.persistence

import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.Role
import com.authrisk.domain.user.User
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class UserJpaEntity(
    @Id
    var id: UUID,
    @Column(nullable = false, unique = true, length = 254)
    var email: String,
    @Column(name = "password_hash", nullable = false)
    var passwordHash: String,
    @Column(nullable = false, length = 16)
    var role: String,
    @Column(name = "created_at", nullable = false)
    var createdAt: Instant,
) {
    fun toDomain() = User(id, Email.of(email), passwordHash, Role.valueOf(role), createdAt)
}

interface UserJpaRepository : JpaRepository<UserJpaEntity, UUID> {
    fun findByEmail(email: String): UserJpaEntity?

    /** Inserción atómica que ignora duplicados sin lanzar excepción (no marca la transacción para rollback). */
    @Modifying
    @Transactional
    @Query(
        nativeQuery = true,
        value = """
            INSERT INTO users (id, email, password_hash, role, created_at)
            VALUES (:id, :email, :passwordHash, :role, :createdAt)
            ON CONFLICT (email) DO NOTHING
        """,
    )
    fun insertIfAbsent(
        @Param("id") id: UUID,
        @Param("email") email: String,
        @Param("passwordHash") passwordHash: String,
        @Param("role") role: String,
        @Param("createdAt") createdAt: Instant,
    ): Int
}

@Component
class JpaUserRepositoryAdapter(private val jpa: UserJpaRepository) : UserRepository {

    override fun insertIfAbsent(user: User): Boolean =
        jpa.insertIfAbsent(user.id, user.email.value, user.passwordHash, user.role.name, user.createdAt) == 1

    override fun findByEmail(email: Email): User? = jpa.findByEmail(email.value)?.toDomain()

    override fun findById(id: UUID): User? = jpa.findById(id).orElse(null)?.toDomain()
}
