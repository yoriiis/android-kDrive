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
package com.infomaniak.drive.utils

import android.content.Context
import androidx.work.*
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.services.BulkOperationWorker

object BulkOperationsUtils {

    fun generateWorkerData(actionUuid: String, fileCount: Int, type: BulkOperationType): Data {
        return workDataOf(
            BulkOperationWorker.ACTION_UUID to actionUuid,
            BulkOperationWorker.TOTAL_FILES_KEY to fileCount,
            BulkOperationWorker.OPERATION_TYPE_KEY to type.toString()
        )
    }

    fun Context.launchBulkOperationWorker(workData: Data) {
        val bulkOperationWorkRequest: OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<BulkOperationWorker>()
                .setInputData(workData)
                .addTag(BulkOperationWorker.TAG)
                .build()

        WorkManager
            .getInstance(this)
            .enqueue(bulkOperationWorkRequest)
    }
}