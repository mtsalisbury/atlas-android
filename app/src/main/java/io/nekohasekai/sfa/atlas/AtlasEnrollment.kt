package io.nekohasekai.sfa.atlas

import android.content.Context
import io.nekohasekai.libbox.Libbox
import io.nekohasekai.sfa.database.Profile
import io.nekohasekai.sfa.database.ProfileManager
import io.nekohasekai.sfa.database.Settings
import io.nekohasekai.sfa.database.TypedProfile
import java.io.File

/**
 * Orchestrates the whole "sign in and end up with a working, selected
 * profile" flow -- the actual replacement for SFA's generic "Add Profile ->
 * Local/Remote -> Name/URL" screen. A real signup never sees any of that;
 * signing in itself is what registers the device and produces a config.
 *
 * Same local-profile-file pattern NewProfileViewModel.createLocalProfile()
 * already uses (configs live under filesDir/configs/<id>.json) -- reusing
 * it rather than inventing a second convention.
 */
object AtlasEnrollment {
    /** Runs blocking network + file I/O. Call this off the main thread. */
    suspend fun signInAndEnroll(context: Context, email: String, password: String) {
        val token = AtlasApi.login(email, password)

        val deviceLabel = android.os.Build.MODEL.ifBlank { "android-device" }
        val configContent = AtlasApi.enrollNative(token, deviceLabel)
        Libbox.checkConfig(configContent)

        val fileID = ProfileManager.nextFileID()
        val configDirectory = File(context.filesDir, "configs").also { it.mkdirs() }
        val configFile = File(configDirectory, "$fileID.json")
        configFile.writeText(configContent)

        val profile = Profile(
            name = "Atlas",
            typed = TypedProfile().apply {
                type = TypedProfile.Type.Local
                path = configFile.path
            },
        ).apply {
            userOrder = ProfileManager.nextOrder()
        }
        ProfileManager.create(profile, andSelect = true)

        Settings.atlasPresenceToken = token
        Settings.atlasEmail = email
    }

    fun signOut() {
        Settings.atlasPresenceToken = ""
        Settings.atlasEmail = ""
    }
}
