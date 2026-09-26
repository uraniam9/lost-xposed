package dev.lostxposed

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import android.os.UserManager
import android.util.Log
import dev.lostxposed.core.config.ConfigContract
import dev.lostxposed.core.config.ConfigWriter

/**
 * Serves settings to hooked processes over Binder.
 *
 * This exists because nothing else works. Measured on this device: both framework channels
 * arrive empty in a hooked process, and reading the snapshot by path gets ENOENT even with
 * the whole directory path open and the file present: an app process's mount namespace does
 * not contain another app's data dir, so no permission or label can fix it. Binder is not
 * subject to either constraint.
 *
 * **Exported deliberately, and filtered because of it.** The readers are SystemUI, keyboards
 * and ordinary apps; none share this module's signature, so a signature-level permission
 * would lock out exactly the callers that need it. Instead the caller is identified from its
 * uid (which it cannot forge, unlike anything it passes as an argument) and served only the
 * settings that apply to it.
 *
 * Read-only. Writes arrive through the settings UI and [ConfigReceiver], both of which are
 * this app's own processes, so there is no reason to expose a mutation path at all.
 *
 * **Answers before the first unlock.** SystemUI starts while the phone is still locked and
 * asks once. Measured on a Nothing Phone (2): without Direct Boot the lookup failed with
 * `Unknown authority` while the phone was locked, and the clock stayed plain until SystemUI
 * was restarted by hand. Before unlock the preferences cannot be opened, so what gets
 * served then is the copy [ConfigWriter] keeps in device-protected storage.
 */
class ConfigContentProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null

        // getCallingUid, not an argument: the caller cannot forge its own uid, and a package
        // name passed in the call is just a claim.
        val uid = Binder.getCallingUid()

        if (method == ConfigContract.METHOD_REPORT_INCIDENT) {
            // Platform only. Otherwise any installed app could fabricate a bootloop that
            // never happened and scare somebody into turning features off.
            if (uid >= ConfigContract.FIRST_APPLICATION_UID) return null
            BootIncidents.record(context, extras)
            Log.w(TAG, "boot incident recorded from uid $uid")
            return Bundle.EMPTY
        }

        if (method != ConfigContract.METHOD_READ) return null
        // uid 1000 is shared by a dozen system packages. The one that asks is system_server,
        // and "android" is what the scope list calls it; the whole dozen would only be noise
        // on the status card.
        val packages = if (uid == Process.SYSTEM_UID) {
            setOf(SYSTEM_SERVER)
        } else {
            runCatching { context.packageManager.getPackagesForUid(uid) }
                .getOrNull().orEmpty().toSet()
        }

        // No copy yet means nothing to serve, and saying so beats an empty answer that
        // looks like a working channel.
        val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked != false
        val all = if (unlocked) {
            ConfigWriter.open(context).values()
        } else {
            ConfigWriter.beforeUnlock(context) ?: run {
                Log.w(TAG, "uid $uid asked before the first unlock, with no copy to serve yet")
                return null
            }
        }

        val visible = ConfigContract.visibleTo(
            all = all,
            packages = packages,
            privileged = uid < ConfigContract.FIRST_APPLICATION_UID,
        )

        Log.i(
            TAG,
            "served ${visible.size} settings to uid $uid ${packages.joinToString()}" +
                if (unlocked) "" else " before the first unlock",
        )
        ServedPackages.record(context, packages)
        lastServed = Served(
            packages = packages.ifEmpty { setOf("uid $uid") },
            keys = visible.size,
            atMillis = System.currentTimeMillis(),
        )

        return Bundle().apply { visible.forEach { (key, value) -> putString(key, value) } }
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    /** The most recent process to read settings through here. */
    data class Served(val packages: Set<String>, val keys: Int, val atMillis: Long)

    companion object {
        private const val TAG = "LostXposed"

        /** system_server's package name, as the framework manager lists it. */
        private const val SYSTEM_SERVER = "android"

        /**
         * Proof of delivery, for the UI to show.
         *
         * The settings app cannot look inside a hooked process, so for a long time it could
         * only say "settings might reach hooks" and hope. A read arriving here is the app
         * observing the transport work from its own side, which is the one piece of evidence
         * it can honestly report.
         */
        @Volatile
        var lastServed: Served? = null
            private set
    }
}
