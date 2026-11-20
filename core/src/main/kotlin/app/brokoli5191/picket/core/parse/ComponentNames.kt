package app.brokoli5191.picket.core.parse

import app.brokoli5191.picket.core.validation.Validators

/** A parsed `ComponentName`, in the two flattened forms Android prints. */
data class Component(
    val packageName: String,
    /** Fully qualified class name. */
    val className: String,
) {
    /** `pkg/.Cls` when the class lives in the package, else `pkg/full.Cls` (ComponentName.flattenToShortString). */
    val short: String
        get() =
            if (className.startsWith("$packageName.")) {
                "$packageName/${className.substring(packageName.length)}"
            } else {
                "$packageName/$className"
            }

    val full: String get() = "$packageName/$className"

    companion object {
        /** Like `ComponentName.unflattenFromString`, plus validation; null for anything malformed. */
        fun parse(flattened: String): Component? {
            val value = flattened.trim()
            if (!Validators.isValidComponent(value)) return null
            val pkg = value.substringBefore('/')
            val cls = value.substringAfter('/')
            return Component(pkg, if (cls.startsWith(".")) pkg + cls else cls)
        }
    }
}

/** Parsers for the colon-separated lists stored in `Settings.Secure`. */
object SettingsLists {
    /** `settings get` prints the literal `null` for unset keys. */
    fun isUnset(value: String): Boolean = value.trim().let { it.isEmpty() || it == "null" }

    /** `enabled_accessibility_services`, `enabled_notification_listeners`: components separated by ':'. */
    fun components(value: String): List<Component> =
        if (isUnset(value)) emptyList() else value.trim().split(':').mapNotNull { Component.parse(it) }

    /** `enabled_notification_policy_access_packages`: package names separated by ':'. */
    fun packages(value: String): List<String> =
        if (isUnset(value)) {
            emptyList()
        } else {
            value
                .trim()
                .split(':')
                .map { it.trim() }
                .filter { Validators.isValidPackageName(it) }
        }

    /**
     * `enabled_input_methods`: `component;subtypeHash;subtypeHash:component2...`. Subtype hashes are
     * dropped; only the input method components are returned.
     */
    fun inputMethods(value: String): List<Component> {
        if (isUnset(value)) return emptyList()
        return value.trim().split(':').mapNotNull { Component.parse(it.substringBefore(';')) }
    }

    /** `default_input_method`: one component, possibly unset. */
    fun singleComponent(value: String): Component? = if (isUnset(value)) null else Component.parse(value)

    /** `0`/`1` flags like `accessibility_enabled`; unset counts as false. */
    fun flag(value: String): Boolean = value.trim() == "1"

    /**
     * Removes [component] from a colon-separated component list while keeping every other entry
     * byte for byte (order, short or full form). Used to disable one accessibility service.
     */
    fun withoutComponent(
        value: String,
        component: Component,
    ): String {
        if (isUnset(value)) return ""
        return value
            .trim()
            .split(':')
            .filter { entry -> Component.parse(entry)?.let { it == component } != true }
            .joinToString(":")
    }

    /** Appends [component] unless it is already present. */
    fun withComponent(
        value: String,
        component: Component,
    ): String {
        if (isUnset(value)) return component.short
        val entries = value.trim().split(':')
        val present = entries.any { Component.parse(it) == component }
        return if (present) value.trim() else (entries + component.short).joinToString(":")
    }
}
