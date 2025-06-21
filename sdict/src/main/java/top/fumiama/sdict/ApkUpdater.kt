package top.fumiama.sdict

import top.fumiama.sdict.io.Client
import top.fumiama.sdict.utils.Utils.toHexStr
import java.security.MessageDigest

/**
 * A base class for checking and downloading APK updates from a remote [SimpleKanban] server.
 *
 * This class uses a [SimpleKanban] instance to communicate with the server,
 * parse versioning messages, and fetch APK binaries. It provides overridable
 * callbacks for handling update events with MD5 checksum verification.
 *
 * Subclasses can override the lifecycle methods to customize update behavior.
 *
 * @param serverIP The IP address of the update server.
 * @param serverPort The port number of the update server.
 * @param password The password used to authenticate with the server.
 */
open class ApkUpdater(serverIP: String, serverPort: Int, password: String) {

    /** Client used for network communication with the server. */
    private val client = Client(serverIP, serverPort)

    /** Wrapper around the client to handle kanban protocol operations. */
    private val kanban = SimpleKanban(client, password)

    /**
     * Called when a newer app version or plain message is available from the server.
     *
     * The MD5 is only been provided when new APK is available.
     *
     * @param version The new version number.
     * @param message A APK changelog or developer notice.
     * @param md5 Optional MD5 checksum of the APK file, if provided.
     */
    open suspend fun onCheckNewVersion(version: Int, message: String, md5: String? = null) {}

    /**
     * Called when the current version is already the latest.
     *
     * @param version The current installed version.
     */
    open suspend fun onCheckLatestVersion(version: Int) {}

    /**
     * Called when APK downloading fails.
     *
     * @param cause The failure reason, either [UPDATE_FAIL_NETWORK] or [UPDATE_FAIL_FILE_CORRUPT].
     */
    open suspend fun onDownloadNewVersionFailed(cause: Int) {}

    /**
     * Called when the APK file is successfully downloaded and verified.
     *
     * @param data The binary contents of the downloaded APK file.
     */
    open suspend fun onDownloadNewVersionSuccess(data: ByteArray) {}

    /**
     * Checks with the server if a new version is available.
     *
     * Parses the message format to determine:
     * - If the current version is the latest (`"null"` response)
     * - If a newer version exists with/without an MD5 checksum
     *
     * Example response format:
     * ```
     * 5
     * Update message here
     * md5:xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
     * ```
     *
     * @param currentVersion The current installed version number.
     */
    suspend fun check(currentVersion: Int) {
        val msg = kanban[currentVersion]
        if (msg == "null") {
            onCheckLatestVersion(currentVersion)
            return
        }
        val verNum = msg.substringBefore('\n').toIntOrNull() ?: return
        if (!msg.contains("md5:")) {
            onCheckNewVersion(verNum, msg.substringAfter('\n'))
            return
        }
        onCheckNewVersion(
            verNum,
            msg.substringAfter('\n').substringBeforeLast('\n'),
            msg.substringAfterLast("md5:")
        )
    }

    /**
     * Downloads the APK binary and validates its MD5 checksum.
     *
     * If the checksum is valid, the update is considered successful and
     * [onDownloadNewVersionSuccess] is called. Otherwise,
     * [onDownloadNewVersionFailed] is invoked with an appropriate error code.
     *
     * @param md5 The expected MD5 hash of the APK file.
     * @param progressHandler Optional handler to track download progress.
     */
    suspend fun download(md5: String, progressHandler: Client.Progress) {
        client.progress = progressHandler
        try {
            kanban.fetch({ onDownloadNewVersionFailed(UPDATE_FAIL_NETWORK) }) {
                if (md5 == toHexStr(
                        MessageDigest.getInstance("MD5").digest(it)
                    )
                ) onDownloadNewVersionSuccess(it)
                else onDownloadNewVersionFailed(UPDATE_FAIL_FILE_CORRUPT)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            client.progress = null
        }
    }

    companion object {
        /** Error code indicating a network failure during the update process. */
        const val UPDATE_FAIL_NETWORK = 0

        /** Error code indicating MD5 mismatch after download. */
        const val UPDATE_FAIL_FILE_CORRUPT = 1
    }
}