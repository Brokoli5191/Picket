package app.brokoli5191.picket.core.parse

import app.brokoli5191.picket.core.model.AppOpEntry
import app.brokoli5191.picket.core.validation.Validators

/** `cmd role get-role-holders ROLE`: holders joined with ';' on one line (RoleShellCommand). */
object RoleHolders {
    fun parse(text: String): List<String> =
        text
            .trim()
            .split(';')
            .map { it.trim() }
            .filter { it.isNotEmpty() && Validators.isValidPackageName(it) }
}

/** `cmd appops` output (AppOpsService.Shell). */
object AppOpsParser {
    private const val NO_OPS = "No operations."
    private val entry = Regex("^([A-Z][A-Z0-9_]*): ([a-z]+)(.*)$")
    private val time = Regex("; time=(\\S+) ago")

    /** `query-op OP allow`: one package per line, or "No operations.". */
    fun parseQueryOp(text: String): List<String> =
        text
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != NO_OPS && Validators.isValidPackageName(it) }
            .toList()

    /**
     * `get PACKAGE [OP]`: `OP: mode[; time=+5m ago][; rejectTime=…][; duration=…| (running)]`.
     * The first uid-level entry carries a `Uid mode: ` prefix (only the first one is marked).
     */
    fun parseGet(text: String): List<AppOpEntry> =
        text
            .lineSequence()
            .map { it.trimEnd() }
            .mapNotNull { line ->
                val uid = line.startsWith("Uid mode: ")
                val match = entry.matchEntire(line.removePrefix("Uid mode: ").trim()) ?: return@mapNotNull null
                AppOpEntry(
                    op = match.groupValues[1],
                    mode = match.groupValues[2],
                    uidMode = uid,
                    lastAccess = time.find(match.groupValues[3])?.groupValues?.get(1),
                )
            }.toList()

    /** Mode of a single op from `get PACKAGE OP`; "No operations." means the op's default mode. */
    fun modeOf(
        text: String,
        op: String,
    ): String? {
        parseGet(text).firstOrNull { it.op == op }?.let { return it.mode }
        return text.lineSequence().firstOrNull { it.startsWith("Default mode: ") }?.let { "default" }
    }
}

/** `dumpsys device_policy` (DevicePolicyData.dump, OwnersData.dump). */
object DevicePolicyParser {
    data class Admin(
        val component: Component,
        val userId: Int,
        /** `device-owner`, `profile-owner` or null for a plain device admin. */
        val role: String?,
    )

    private val enabledHeader = Regex("^(\\s*)Enabled Device Admins \\(User (\\d+)")
    private val adminLine = Regex("^\\s*([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+):\\s*$")
    private val ownerAdmin = Regex("^\\s*admin=ComponentInfo\\{([^}]+)}")
    private val profileOwner = Regex("^\\s*Profile Owner \\(User (\\d+)\\)")

    fun parse(text: String): List<Admin> {
        val admins = mutableListOf<Admin>()
        val deviceOwners = mutableSetOf<Component>()
        val profileOwners = mutableSetOf<Pair<Component, Int>>()
        var adminUser: Int? = null
        var adminIndent = 0
        // Which owner block an "admin=ComponentInfo{…}" line belongs to: device owner (null user) or a profile owner.
        var ownerBlock: Int? = null
        var inDeviceOwner = false
        for (line in text.lineSequence()) {
            if (line.isBlank()) continue
            val indent = line.length - line.trimStart().length
            val header = enabledHeader.find(line)
            if (header != null) {
                adminUser = header.groupValues[2].toInt()
                adminIndent = header.groupValues[1].length
                continue
            }
            val currentUser = adminUser
            if (currentUser != null && indent > adminIndent) {
                adminLine.matchEntire(line)?.let { m ->
                    Component.parse(m.groupValues[1])?.let { admins += Admin(it, currentUser, null) }
                }
                continue
            }
            adminUser = null
            val trimmed = line.trim()
            val profile = profileOwner.find(line)
            when {
                trimmed.startsWith("Device Owner:") -> {
                    inDeviceOwner = true
                    ownerBlock = null
                }

                profile != null -> {
                    inDeviceOwner = false
                    ownerBlock = profile.groupValues[1].toInt()
                }

                else -> {
                    val component = ownerAdmin.find(line)?.let { Component.parse(it.groupValues[1]) } ?: continue
                    val user = ownerBlock
                    if (inDeviceOwner) {
                        deviceOwners += component
                    } else if (user !=
                        null
                    ) {
                        profileOwners += component to user
                    }
                    inDeviceOwner = false
                    ownerBlock = null
                }
            }
        }
        return admins.map { admin ->
            val role =
                when {
                    admin.component in deviceOwners -> "device-owner"
                    (admin.component to admin.userId) in profileOwners -> "profile-owner"
                    else -> null
                }
            admin.copy(role = role)
        }
    }
}

/** `dumpsys notification`: components of the "Live notification listeners (N):" section (bound right now). */
object NotificationDumpParser {
    private val live = Regex("^\\s*Live notification listeners \\(\\d+\\):")
    private val bound = Regex("^\\s*ComponentInfo\\{([^}]+)} \\(user (\\d+)\\)")

    fun liveListeners(text: String): List<Pair<Component, Int>> {
        val result = mutableListOf<Pair<Component, Int>>()
        var inSection = false
        var sectionIndent = 0
        for (line in text.lineSequence()) {
            val indent = line.length - line.trimStart().length
            if (live.containsMatchIn(line)) {
                inSection = true
                sectionIndent = indent
                continue
            }
            if (!inSection) continue
            if (line.isBlank() || indent <= sectionIndent) {
                inSection = false
                continue
            }
            bound.find(line)?.let { m ->
                Component.parse(m.groupValues[1])?.let {
                    result +=
                        it to m.groupValues[2].toInt()
                }
            }
        }
        return result
    }
}

/**
 * `dumpsys vpn_management` (Android 12+): per user `N: package`, then `Active package name: …` and
 * `Active vpn type: T`. Type -1 (TYPE_VPN_NONE) means not connected; `[Legacy VPN]` means no VPN app.
 */
object VpnParser {
    private val userLine = Regex("^\\s*(\\d+): (\\S+)\\s*$")
    private val type = Regex("^\\s*Active vpn type: (-?\\d+)")

    fun activeVpnPackages(text: String): List<String> {
        val result = mutableListOf<String>()
        var pending: String? = null
        for (line in text.lineSequence()) {
            userLine.matchEntire(line)?.let { m ->
                pending = m.groupValues[2].takeIf { Validators.isValidPackageName(it) }
                return@let
            }
            val t = type.find(line) ?: continue
            val pkg = pending
            if (pkg != null && t.groupValues[1].toInt() != -1) result += pkg
            pending = null
        }
        return result.distinct()
    }
}
