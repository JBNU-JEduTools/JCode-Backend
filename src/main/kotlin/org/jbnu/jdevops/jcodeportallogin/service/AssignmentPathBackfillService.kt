package org.jbnu.jdevops.jcodeportallogin.service

import org.jbnu.jdevops.jcodeportallogin.config.GENERATOR_SCOPE_ATTRIBUTE
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentLifecycleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentPathBackfillStatus
import org.jbnu.jdevops.jcodeportallogin.entity.AssignmentScheduleStatus
import org.jbnu.jdevops.jcodeportallogin.entity.Course
import org.jbnu.jdevops.jcodeportallogin.entity.CourseStatus
import org.jbnu.jdevops.jcodeportallogin.entity.WorkspaceOperationAction
import org.jbnu.jdevops.jcodeportallogin.repo.AssignmentRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration
import java.time.LocalDateTime
import kotlin.system.exitProcess

enum class MissingNamespacePolicy {
    FAIL,
    ARCHIVE;

    companion object {
        fun parse(value: String): MissingNamespacePolicy = entries.firstOrNull {
            it.name.equals(value.trim(), ignoreCase = true)
        } ?: throw IllegalArgumentException("missing-namespace는 fail 또는 archive여야 합니다.")
    }
}

data class AssignmentPathBackfillResult(
    val registered: Int,
    val archived: Int,
    val missingNamespaces: List<String>
)

interface CourseNamespaceLookup {
    fun exists(course: Course): Boolean
}

@Component
class GeneratorCourseNamespaceLookup(
    @Qualifier("generatorBootstrapWebClient") private val generator: WebClient,
    @Value("\${workspace.lifecycle.request-timeout-seconds:90}") private val timeoutSeconds: Long
) : CourseNamespaceLookup {
    override fun exists(course: Course): Boolean {
        val namespace = course.namespaceKey ?: Course.namespaceKey(course.infrastructureKey, course.clss)
        val response = generator.get()
            .uri { builder ->
                builder.path("/api/namespace/{namespace}")
                    .queryParam("course_id", course.id)
                    .build(namespace)
            }
            .attribute(GENERATOR_SCOPE_ATTRIBUTE, "namespace:read")
            .retrieve()
            .bodyToMono(Map::class.java)
            .block(Duration.ofSeconds(timeoutSeconds))
            ?: throw IllegalStateException("Generator가 Namespace 상태를 반환하지 않았습니다.")
        return response["exists"] as? Boolean
            ?: throw IllegalStateException("Generator Namespace 상태 응답이 올바르지 않습니다.")
    }
}

@Service
class AssignmentPathBackfillService(
    private val assignmentRepository: AssignmentRepository,
    private val operationStore: WorkspaceOperationStore,
    private val namespaceLookup: CourseNamespaceLookup
) {
    fun execute(policy: MissingNamespacePolicy): AssignmentPathBackfillResult {
        val assignments = assignmentRepository.findByPathBackfillStatus(AssignmentPathBackfillStatus.PENDING)
        val activeCourses = assignments.map { it.course }
            .filter { it.status == CourseStatus.ACTIVE }
            .distinctBy { it.id }
        val namespaceExists = activeCourses.associate { course -> course.id to namespaceLookup.exists(course) }
        val missing = activeCourses.filter { namespaceExists[it.id] != true }
            .map { it.namespaceKey ?: Course.namespaceKey(it.infrastructureKey, it.clss) }
            .sorted()

        if (missing.isNotEmpty() && policy == MissingNamespacePolicy.FAIL) {
            throw IllegalStateException(
                "활성 강의 Namespace가 없습니다: ${missing.joinToString(", ")}. " +
                    "검토 후 --workspace.assignment-path-backfill.missing-namespace=archive를 명시하세요."
            )
        }

        var registered = 0
        var archived = 0
        val now = LocalDateTime.now()
        assignments.forEach { assignment ->
            val shouldRegister = assignment.course.status == CourseStatus.ACTIVE &&
                namespaceExists[assignment.course.id] == true
            if (shouldRegister) {
                operationStore.enqueueBackfillOnce(assignment.id, WorkspaceOperationAction.MIGRATE_ASSIGNMENT_PATH)
                if (assignment.scheduleStatus == AssignmentScheduleStatus.CLOSED) {
                    operationStore.enqueueBackfillOnce(assignment.id, WorkspaceOperationAction.ARCHIVE_FINAL_SUBMISSION)
                }
                assignment.pathBackfillStatus = AssignmentPathBackfillStatus.REGISTERED
                registered += 1
            } else {
                assignment.lifecycleStatus = AssignmentLifecycleStatus.ARCHIVED
                assignment.scheduleStatus = AssignmentScheduleStatus.ARCHIVED
                assignment.archivedAt = assignment.archivedAt ?: now
                assignment.lastError = null
                assignment.pathBackfillStatus = AssignmentPathBackfillStatus.ARCHIVED
                archived += 1
            }
            assignmentRepository.save(assignment)
        }
        return AssignmentPathBackfillResult(registered, archived, missing)
    }
}

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(
    name = ["workspace.assignment-path-backfill.enabled"],
    havingValue = "true"
)
class AssignmentPathBackfillRunner(
    private val service: AssignmentPathBackfillService,
    private val context: ConfigurableApplicationContext,
    @Value("\${workspace.assignment-path-backfill.missing-namespace:fail}") private val policy: String
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val exitCode = try {
            val result = service.execute(MissingNamespacePolicy.parse(policy))
            logger.info(
                "Assignment path backfill completed: registered={}, archived={}, missing={}",
                result.registered,
                result.archived,
                result.missingNamespaces
            )
            0
        } catch (error: Exception) {
            logger.error("Assignment path backfill failed", error)
            1
        }
        SpringApplication.exit(context, ExitCodeGenerator { exitCode })
        exitProcess(exitCode)
    }
}
