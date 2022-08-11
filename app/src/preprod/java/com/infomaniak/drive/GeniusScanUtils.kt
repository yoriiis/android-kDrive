/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.drive

import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object GeniusScanUtils {

    fun Context.initGeniusScanSdk() = true

    fun Context.startScanFlow(resultLauncher: ActivityResultLauncher<Intent>) {
        MaterialAlertDialogBuilder(this, R.style.DialogStyle)
            .setTitle(R.string.allErrorFeatureNotAvailableInFdroid)
            .setPositiveButton("Ok") { _: DialogInterface?, _: Int -> }
            .show()
    }

    fun Fragment.scanResultProcessing(intent: Intent, folderId: Int) = Unit

}
