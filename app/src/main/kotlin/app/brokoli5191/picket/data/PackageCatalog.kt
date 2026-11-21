package app.brokoli5191.picket.data

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import app.brokoli5191.picket.core.process.Uids
import java.util.concurrent.ConcurrentHashMap

/** Static facts about an installed package, as far as the (unprivileged) app can see them. */
data class AppInfo(
    val packageName: String,
    val label: String,
    val uid: Int,
    val isSystem: Boolean,
    val isUpdatedSystem: Boolean,
    /** Package that installed it (`com.android.vending`, ...); null for preinstalled or unknown. */
    val installer: String?,
)

/**
 * Package lookups backed by [PackageManager], cached per process. Needs QUERY_ALL_PACKAGES to see
 * other apps; only the current user's packages are visible without INTERACT_ACROSS_USERS.
 */
class PackageCatalog(
    private val packageManager: PackageManager,
) {
    private val infoCache = ConcurrentHashMap<String, AppInfo>()
    private val uidCache = ConcurrentHashMap<Int, List<String>>()

    fun packagesForUid(uid: Int): List<String> =
        uidCache.getOrPut(uid) {
            // PackageManager answers for the calling user; map other users' uids onto user 0's appId.
            val sameUserUid = Uids.appId(uid)
            packageManager.getPackagesForUid(sameUserUid)?.sorted().orEmpty()
        }

    fun uidOf(
        packageName: String,
        userId: Int,
    ): Int? = info(packageName)?.uid?.let { userId * Uids.PER_USER_RANGE + Uids.appId(it) }

    fun info(packageName: String): AppInfo? {
        infoCache[packageName]?.let { return it }
        val appInfo = applicationInfo(packageName) ?: return null
        val info =
            AppInfo(
                packageName = packageName,
                label = appInfo.loadLabel(packageManager).toString(),
                uid = appInfo.uid,
                isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                isUpdatedSystem = appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
                installer = installerOf(packageName),
            )
        infoCache[packageName] = info
        return info
    }

    /** The package that best represents [uid] in lists: the one whose name a process carries, else the first. */
    fun primaryPackage(
        uid: Int,
        processNames: Collection<String> = emptyList(),
    ): String? {
        val packages = packagesForUid(uid)
        return packages.firstOrNull { pkg -> processNames.any { it == pkg || it.startsWith("$pkg:") } }
            ?: packages.firstOrNull()
    }

    fun invalidate() {
        infoCache.clear()
        uidCache.clear()
    }

    private fun applicationInfo(packageName: String): ApplicationInfo? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                packageManager.getApplicationInfo(packageName, 0)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        }

    private fun installerOf(packageName: String): String? =
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                packageManager.getInstallSourceInfo(packageName).installingPackageName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getInstallerPackageName(packageName)
            }
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: SecurityException) {
            null
        }
}
