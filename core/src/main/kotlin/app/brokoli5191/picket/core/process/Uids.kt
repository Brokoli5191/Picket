package app.brokoli5191.picket.core.process

/** Android uid arithmetic (android.os.UserHandle / android.os.Process), without Android. */
object Uids {
    const val PER_USER_RANGE = 100_000
    const val ROOT = 0
    const val SYSTEM = 1000
    const val SHELL = 2000
    const val FIRST_APPLICATION = 10_000
    const val LAST_APPLICATION = 19_999
    const val FIRST_SDK_SANDBOX = 20_000
    const val LAST_SDK_SANDBOX = 29_999
    const val FIRST_APP_ZYGOTE_ISOLATED = 90_000
    const val LAST_APP_ZYGOTE_ISOLATED = 98_999
    const val FIRST_ISOLATED = 99_000
    const val LAST_ISOLATED = 99_999

    /** SDK sandbox uid = app uid + this offset (Process.toSdkSandboxUid). */
    private const val SDK_SANDBOX_OFFSET = FIRST_SDK_SANDBOX - FIRST_APPLICATION

    fun userId(uid: Int): Int = uid / PER_USER_RANGE

    fun appId(uid: Int): Int = uid % PER_USER_RANGE

    fun isApp(uid: Int): Boolean = appId(uid) in FIRST_APPLICATION..LAST_APPLICATION

    fun isIsolated(uid: Int): Boolean = appId(uid) in FIRST_APP_ZYGOTE_ISOLATED..LAST_ISOLATED

    fun isSdkSandbox(uid: Int): Boolean = appId(uid) in FIRST_SDK_SANDBOX..LAST_SDK_SANDBOX

    /** The app uid an SDK sandbox process belongs to. */
    fun appUidOfSdkSandbox(uid: Int): Int = uid - SDK_SANDBOX_OFFSET

    /** `u0_a123` style name as printed by ps and dumpsys, or the AID name for system uids. */
    fun displayName(uid: Int): String {
        val user = userId(uid)
        val app = appId(uid)
        return when {
            app in FIRST_APPLICATION..LAST_APPLICATION -> {
                "u${user}_a${app - FIRST_APPLICATION}"
            }

            app in FIRST_ISOLATED..LAST_ISOLATED -> {
                "u${user}_i${app - FIRST_ISOLATED}"
            }

            app in FIRST_APP_ZYGOTE_ISOLATED..LAST_APP_ZYGOTE_ISOLATED -> {
                "u${user}_ai${app - FIRST_APP_ZYGOTE_ISOLATED}"
            }

            app in FIRST_SDK_SANDBOX..LAST_SDK_SANDBOX -> {
                "u${user}_s${app - FIRST_SDK_SANDBOX}"
            }

            else -> {
                systemNames[app]?.let { if (user == 0) it else "u${user}_$it" } ?: uid.toString()
            }
        }
    }

    /** Well-known AIDs from system/core `android_filesystem_config.h` (subset seen in process lists). */
    private val systemNames =
        mapOf(
            0 to "root",
            1000 to "system",
            1001 to "radio",
            1002 to "bluetooth",
            1003 to "graphics",
            1004 to "input",
            1005 to "audio",
            1006 to "camera",
            1007 to "log",
            1010 to "wifi",
            1013 to "media",
            1017 to "keystore",
            1019 to "drm",
            1021 to "gps",
            1027 to "nfc",
            1036 to "logd",
            1037 to "shared_relro",
            1041 to "audioserver",
            1046 to "mediacodec",
            1047 to "cameraserver",
            1053 to "webview_zygote",
            1066 to "statsd",
            1068 to "secure_element",
            1069 to "lmkd",
            1072 to "gpu_service",
            1073 to "network_stack",
            2000 to "shell",
            9999 to "nobody",
        )
}
