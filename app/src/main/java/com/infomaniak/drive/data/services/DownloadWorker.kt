/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.data.services

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor.AutoCloseOutputStream
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.*
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.ProgressResponseBody
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.KDriveHttpClient
import com.infomaniak.drive.utils.MediaUtils
import com.infomaniak.drive.utils.MediaUtils.isMedia
import com.infomaniak.drive.utils.NotificationUtils.downloadProgressNotification
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import kotlinx.coroutines.*
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream


class DownloadWorker(private val context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val fileId = inputData.getInt(FILE_ID, 0)
        val userId = inputData.getInt(USER_ID, AccountUtils.currentUserId)
        val driveID = inputData.getInt(DRIVE_ID, AccountUtils.currentDriveId)
        val userDrive = UserDrive(userId, driveID)
        val file = FileController.getFileById(fileId, userDrive)
        val offlineFile = file?.getOfflineFile(context, userId)

        return coroutineScope {
            val job = async {
                if (file != null && offlineFile != null) initOfflineDownload(file, offlineFile, userDrive) else Result.failure()
            }
            job.invokeOnCompletion { exception ->
                when (exception) {
                    is CancellationException -> {
                        if (offlineFile != null && offlineFile.exists() && !file.isOffline) offlineFile.delete()
                        notifyDownloadCancelled(fileId)
                    }
                    else -> exception?.printStackTrace()
                }
            }
            job.await()
        }
    }

    private suspend fun initOfflineDownload(file: File, offlineFile: java.io.File, userDrive: UserDrive): Result =
        withContext(Dispatchers.IO) {
            val cacheFile = file.getCacheFile(context, userDrive)

            if (file.isOfflineAndIntact(offlineFile)) return@withContext Result.success()

            val firstUpdate = workDataOf(PROGRESS to 0, FILE_ID to file.id)
            setProgress(firstUpdate)

            if (offlineFile.exists()) offlineFile.delete()
            if (cacheFile.exists()) cacheFile.delete()

            val cancelPendingIntent = WorkManager.getInstance(applicationContext).createCancelPendingIntent(id)
            val cancelAction = NotificationCompat.Action(null, context.getString(R.string.buttonCancel), cancelPendingIntent)
            val downloadNotification = context.downloadProgressNotification().apply {
                setOngoing(true)
                setContentTitle(file.name)
                addAction(cancelAction)
                setForeground(ForegroundInfo(file.id, build()))
            }
            startOfflineDownload(file, downloadNotification, offlineFile, userDrive)
        }

    private suspend fun startOfflineDownload(
        file: File,
        downloadNotification: NotificationCompat.Builder,
        offlineFile: java.io.File,
        userDrive: UserDrive
    ): Result = withContext(Dispatchers.IO) {
        try {
            val lastUpdate = workDataOf(PROGRESS to 100, FILE_ID to file.id)
            val okHttpClient = KDriveHttpClient.getHttpClient(userDrive.userId, null)
            val response = downloadFileResponse(
                fileUrl = ApiRoutes.downloadFile(file),
                okHttpClient = okHttpClient
            ) { progress ->
                launch(Dispatchers.Main) {
                    setProgress(workDataOf(PROGRESS to progress, FILE_ID to file.id))
                }
                Log.d(TAG, "download $progress%")
                downloadNotification.apply {
                    setContentText("$progress%")
                    setProgress(100, progress, false)
                    // runBlocking for sync the progress
                    runBlocking { setForeground(ForegroundInfo(file.id, build())) }
                }
            }

            saveRemoteData(response, offlineFile) {
                launch(Dispatchers.Main) { setProgress(lastUpdate) }
                FileController.updateOfflineStatus(file.id, true)
                offlineFile.setLastModified(file.getLastModifiedInMilliSecond())
                if (file.isMedia()) MediaUtils.scanFile(context, offlineFile)
            }

            if (response.isSuccessful) Result.success()
            else Result.failure()

        } catch (e: Exception) {
            if (offlineFile.exists()) offlineFile.delete()
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun notifyDownloadCancelled(fileID: Int) {
        Intent().apply {
            action = DownloadReceiver.TAG
            putExtra(DownloadReceiver.CANCELLED_FILE_ID, fileID)
            LocalBroadcastManager.getInstance(context).sendBroadcast(this)
        }
    }

    companion object {
        const val TAG = "DownloadWorker"
        const val PROGRESS = "Progress"
        const val FILE_ID = "fileID"
        const val USER_ID = "userID"
        const val DRIVE_ID = "driveID"

        @Throws(Exception::class)
        fun downloadFileResponse(
            fileUrl: String,
            okHttpClient: OkHttpClient = HttpClient.okHttpClient,
            onProgress: (progress: Int) -> Unit
        ): Response {
            val request = Request.Builder().url(fileUrl).headers(HttpUtils.getHeaders(contentType = null)).get().build()

            return okHttpClient.newBuilder()
                .addNetworkInterceptor(downloadProgressInterceptor(onProgress)).build()
                .newCall(request).execute()
        }

        @Throws(Exception::class)
        fun saveRemoteData(
            response: Response,
            outputFile: java.io.File? = null,
            outputStream: AutoCloseOutputStream? = null,
            onFinish: (() -> Unit)? = null
        ) {
            Log.d(TAG, "save remote data to ${outputFile?.path}")
            BufferedInputStream(response.body?.byteStream()).use { input ->
                val stream = outputStream ?: outputFile?.outputStream()
                stream?.use { output ->
                    input.copyTo(output)
                    onFinish?.invoke()
                }
            }
        }

        @Throws(Exception::class)
        fun downloadProgressInterceptor(onProgress: (progress: Int) -> Unit) = Interceptor { chain: Interceptor.Chain ->
            val originalResponse = chain.proceed(chain.request())

            originalResponse.newBuilder()
                .body(ProgressResponseBody(originalResponse.body!!, object : ProgressResponseBody.ProgressListener {
                    override fun update(bytesRead: Long, contentLength: Long, done: Boolean) {
                        val progress = (bytesRead.toFloat() / contentLength.toFloat() * 100F).toInt()
                        onProgress(progress)
                    }
                })).build()
        }
    }
}