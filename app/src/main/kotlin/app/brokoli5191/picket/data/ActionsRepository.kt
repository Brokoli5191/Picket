package app.brokoli5191.picket.data

import app.brokoli5191.picket.core.action.Action
import app.brokoli5191.picket.core.action.ActionOutcome
import app.brokoli5191.picket.core.action.ActionPlan
import app.brokoli5191.picket.core.ipc.PayloadCodec
import app.brokoli5191.picket.core.ipc.Reply
import app.brokoli5191.picket.core.ipc.ServiceError
import app.brokoli5191.picket.data.journal.JournalDao
import app.brokoli5191.picket.data.journal.JournalEntry
import app.brokoli5191.picket.privileged.client.PrivilegedClient
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * Result of running a plan: the journal id it was recorded under (-1 if it never ran), the action
 * that ran, and what the service returned.
 */
data class Executed(
    val entryId: Long,
    val action: Action?,
    val reply: Reply<ActionOutcome>,
) {
    val success: Boolean get() = reply.value?.success == true

    /** Why it failed, in the service's or the command's own words. */
    val failure: String?
        get() {
            val outcome = reply.value ?: return reply.error?.message.orEmpty()
            return if (outcome.success) null else outcome.note ?: outcome.stderr.ifEmpty { outcome.stdout }
        }
}

/** An undo that has been planned and is waiting for the user to confirm it. */
data class PlannedUndo(
    val entryId: Long,
    val plan: ActionPlan,
)

/** Preview, run, journal and undo actions. Nothing runs without a plan the user has seen. */
class ActionsRepository(
    private val client: PrivilegedClient,
    private val journal: JournalDao,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Identifies this process's run; "undo all from this session" uses it. */
    val sessionId: String = UUID.randomUUID().toString()

    val entries: Flow<List<JournalEntry>> = journal.observeAll()

    suspend fun plan(action: Action): Reply<ActionPlan> = client.planAction(action)

    suspend fun planDisableAccessibility(
        packageName: String,
        userId: Int,
        component: String,
    ): Reply<ActionPlan> = client.planDisableAccessibility(packageName, userId, component)

    /** Runs a previewed plan and journals the result, successful or not. */
    suspend fun execute(
        plan: ActionPlan,
        undoOf: Long? = null,
    ): Executed {
        val reply = client.executePlan(plan)
        val outcome = reply.value
        val id =
            journal.insert(
                JournalEntry(
                    sessionId = sessionId,
                    timestampMs = now(),
                    packageName = plan.action.packageName,
                    actionJson = json.encodeToString(Action.serializer(), plan.action),
                    argv = plan.command.argv,
                    command = plan.command.display(),
                    risk = plan.risk.name,
                    previousState = plan.previousState,
                    inverseJson = plan.inverse?.let { json.encodeToString(Action.serializer(), it) },
                    success = outcome?.success == true,
                    exitCode = outcome?.exitCode ?: -1,
                    stdout = outcome?.stdout.orEmpty(),
                    stderr = outcome?.stderr.orEmpty(),
                    note = outcome?.note ?: reply.error?.message,
                    undoOfId = undoOf,
                ),
            )
        if (undoOf != null && outcome?.success == true) journal.markUndone(undoOf, id)
        return Executed(id, plan.action, reply)
    }

    /** Plans the inverse of a journal entry. The service re-reads the current state for it. */
    suspend fun planUndo(entryId: Long): Reply<ActionPlan> {
        val entry =
            journal.byId(entryId)
                ?: return Reply.failed(ServiceError(ServiceError.Kind.INVALID_ARGUMENT, "No such entry"))
        if (entry.undoneById !=
            null
        ) {
            return Reply.failed(ServiceError(ServiceError.Kind.INVALID_ARGUMENT, "Already undone"))
        }
        val inverse =
            entry.inverseJson?.let { json.decodeFromString(Action.serializer(), it) }
                ?: return Reply.failed(ServiceError(ServiceError.Kind.UNSUPPORTED, "This action has no inverse"))
        return client.planAction(inverse)
    }

    /**
     * One-tap undo: plans the stored inverse and runs it. The user saw the inverse when confirming
     * the original action, and the journal lists it next to every undoable entry.
     */
    suspend fun undo(entryId: Long): Executed {
        val planned = planUndo(entryId)
        val plan = planned.value ?: return Executed(-1, null, Reply.failed(planned.error ?: unknownError))
        return execute(plan, undoOf = entryId)
    }

    /** Plans the undo of every undoable entry of this session, newest first, for one confirmation. */
    suspend fun planSessionUndo(): Pair<List<PlannedUndo>, List<String>> {
        val planned = mutableListOf<PlannedUndo>()
        val failed = mutableListOf<String>()
        for (entry in journal.undoableInSession(sessionId)) {
            val reply = planUndo(entry.id)
            val plan = reply.value
            if (plan !=
                null
            ) {
                planned += PlannedUndo(entry.id, plan)
            } else {
                failed += "${entry.command}: ${reply.error?.message}"
            }
        }
        return planned to failed
    }

    /** Runs confirmed undo plans in order. Returns how many succeeded. */
    suspend fun executeUndos(plans: List<PlannedUndo>): Int =
        plans.count { execute(it.plan, undoOf = it.entryId).success }

    suspend fun exportJson(): String =
        exportJson.encodeToString(ListSerializer(JournalEntry.serializer()), journal.everything())

    private companion object {
        val json = PayloadCodec.json
        val exportJson = Json { prettyPrint = true }
        val unknownError = ServiceError(ServiceError.Kind.INTERNAL, "Unknown error")
    }
}
