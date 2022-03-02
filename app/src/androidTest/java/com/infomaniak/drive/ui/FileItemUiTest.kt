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
package com.infomaniak.drive.ui

import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.infomaniak.drive.utils.KDriveUiTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.*

/**
 * UI Tests relative to a file item (sharing, comments, details, activities)
 */
class FileItemUiTest : KDriveUiTest() {

    @BeforeEach
    override fun startApp() {
        super.startApp()
        getDeviceViewById("fileListFragment").click()
    }

    @Test
    @DisplayName("Check UI to create a folder then create a share link for it")
    fun testCreateFileShareLink() {
        val randomFolderName = "UI-Test-${UUID.randomUUID()}"
        val fileRecyclerView = UiScrollable(UiSelector().resourceId(getViewIdentifier("fileRecyclerView")))

        // Create the folder then returns to main view
        createPrivateFolder(randomFolderName)
        // Go to fileList view
        openFileShareDetails(fileRecyclerView, randomFolderName)

        device.apply {
            findObject(UiSelector().resourceId(getViewIdentifier("shareLinkSwitch"))).clickAndWaitForNewWindow()
            createPublicShareLink(UiScrollable(UiSelector().resourceId(getViewIdentifier("permissionsRecyclerView"))))
            pressBack()
            findObject(UiSelector().resourceId(getViewIdentifier("closeButton"))).clickAndWaitForNewWindow()
        }
        deleteFile(fileRecyclerView, randomFolderName)
    }
}
