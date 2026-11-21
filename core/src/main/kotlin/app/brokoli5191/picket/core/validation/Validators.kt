package app.brokoli5191.picket.core.validation

/**
 * Validation for every value that can end up on a command line inside the privileged service.
 *
 * The rules are deliberately stricter than what Android accepts: anything that is not plain ASCII
 * identifier syntax is rejected, so shell metacharacters, whitespace, quotes, path separators and
 * control characters can never reach an argv element.
 */
object Validators {
    /** Android stores packages under their name on disk; PackageManager caps the name at 223 chars. */
    const val MAX_PACKAGE_NAME_LENGTH = 223

    /** uid = userId * 100000 + appId must fit into a signed 32-bit int. */
    const val MAX_USER_ID = 21_473

    private const val MAX_CLASS_NAME_LENGTH = 512

    private val segment = "[A-Za-z][A-Za-z0-9_]*"
    private val packageRegex = Regex("^$segment(\\.$segment)*$")
    private val permissionRegex = Regex("^$segment(\\.$segment)+$")
    private val javaSegment = "[A-Za-z_$][A-Za-z0-9_$]*"
    private val classRegex = Regex("^\\.?$javaSegment(\\.$javaSegment)*$")

    fun isValidPackageName(value: String): Boolean =
        value.length in 1..MAX_PACKAGE_NAME_LENGTH && packageRegex.matches(value)

    /** Runtime permission names, e.g. `android.permission.CAMERA` or `com.example.permission.FOO`. */
    fun isValidPermissionName(value: String): Boolean =
        value.length in 3..MAX_CLASS_NAME_LENGTH && permissionRegex.matches(value)

    fun isValidUserId(value: Int): Boolean = value in 0..MAX_USER_ID

    /**
     * Accepts `pkg/.Relative` and `pkg/fully.qualified.Class` (inner classes with `$` included),
     * the same shapes `ComponentName.unflattenFromString` produces.
     */
    fun isValidComponent(value: String): Boolean {
        val slash = value.indexOf('/')
        if (slash <= 0 || slash != value.lastIndexOf('/')) return false
        val pkg = value.substring(0, slash)
        val cls = value.substring(slash + 1)
        return isValidPackageName(pkg) && cls.length in 1..MAX_CLASS_NAME_LENGTH && classRegex.matches(cls)
    }

    fun requirePackageName(value: String): String {
        require(isValidPackageName(value)) { "Invalid package name: ${value.printable()}" }
        return value
    }

    fun requirePermissionName(value: String): String {
        require(isValidPermissionName(value)) { "Invalid permission name: ${value.printable()}" }
        return value
    }

    fun requireUserId(value: Int): Int {
        require(isValidUserId(value)) { "Invalid user id: $value" }
        return value
    }

    fun requireComponent(value: String): String {
        require(isValidComponent(value)) { "Invalid component name: ${value.printable()}" }
        return value
    }

    /** Renders rejected input safely for error messages and logs (no control characters, bounded). */
    internal fun String.printable(): String {
        val source = this
        return buildString {
            append('"')
            for (c in source.take(MAX_PRINTABLE)) {
                if (c.code in 0x20..0x7e) append(c) else append("\\u%04x".format(c.code))
            }
            if (source.length > MAX_PRINTABLE) append("...")
            append('"')
        }
    }

    private const val MAX_PRINTABLE = 80
}
