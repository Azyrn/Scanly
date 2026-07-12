package com.skeler.scanely.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// ./gradlew :baselineprofile:generateBaselineProfile
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generateBaselineProfile() {
        rule.collect(
            packageName = "com.skeler.scanely",
            includeInStartupProfile = true,
            stableIterations = 3,
            maxIterations = 10
        ) {
            pressHome()
            startActivityAndWait()

            device.waitForIdle()
            device.wait(Until.hasObject(By.text("Scan Document")), 5000)

            device.findObject(By.text("From Gallery"))?.click()
            device.waitForIdle()

            device.pressBack()
            device.waitForIdle()

            device.wait(Until.hasObject(By.text("Scan Barcode/QR")), 3000)
            device.findObject(By.text("Scan Barcode/QR"))?.click()
            device.waitForIdle()

            device.pressBack()
            device.waitForIdle()

            device.findObject(By.desc("Settings"))?.click()
            device.waitForIdle()

            device.pressBack()
            device.waitForIdle()

            device.wait(Until.hasObject(By.text("View Previous Extracts")), 3000)
            device.findObject(By.text("View Previous Extracts"))?.click()
            device.waitForIdle()
        }
    }
}
