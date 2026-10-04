package `in`.family.maa

import android.app.Activity
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.widget.Toast

/** A request is never treated as a grant. Call isDefaultHome again on resume. */
object HomeRoleController {
    fun isDefaultHome(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = activity.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME)) {
                return roles.isRoleHeld(RoleManager.ROLE_HOME)
            }
        }
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = activity.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == activity.packageName
    }

    fun request(activity: Activity) {
        if (Build.VERSION.SDK_INT >= 29) {
            val roles = activity.getSystemService(RoleManager::class.java)
            if (roles != null && roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME)) {
                try {
                    activity.startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
                    return
                } catch (_: RuntimeException) { /* OEMs may not provide the role request UI. */ }
            }
        }
        for (action in listOf(Settings.ACTION_HOME_SETTINGS, Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
            try {
                activity.startActivity(Intent(action))
                return
            } catch (_: RuntimeException) { /* Try the next supported system destination. */ }
        }
        Toast.makeText(activity, AppLanguage.text(activity, R.string.platform_home_unavailable), Toast.LENGTH_LONG).show()
    }
}
