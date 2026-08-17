package org.jbnu.jdevops.jcodeportallogin.entity

import jakarta.persistence.*
import java.time.LocalDateTime

enum class StarterOverwritePolicy { PRESERVE_EXISTING, REPLACE_ALL }
enum class StarterArtifactStatus { UPLOADING, READY, FAILED, ARCHIVED }

@Entity
@Table(
    name = "starter_artifact",
    uniqueConstraints = [UniqueConstraint(columnNames = ["assignment_id", "version"])]
)
class StarterArtifact(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @ManyToOne(optional = false, fetch = FetchType.EAGER)
    @JoinColumn(name = "assignment_id", nullable = false)
    val assignment: Assignment,

    @Column(nullable = false)
    val version: Int,

    @Column(name = "artifact_key", nullable = false, unique = true, length = 255)
    val artifactKey: String,

    @Column(length = 64)
    var checksum: String? = null,

    @Column(name = "size_bytes", nullable = false)
    var sizeBytes: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "overwrite_policy", nullable = false, length = 24)
    val overwritePolicy: StarterOverwritePolicy,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    var status: StarterArtifactStatus = StarterArtifactStatus.UPLOADING,

    @Column(name = "last_error", columnDefinition = "TEXT")
    var lastError: String? = null,

    @Column(name = "uploaded_at", nullable = false, updatable = false)
    val uploadedAt: LocalDateTime = LocalDateTime.now()
)
