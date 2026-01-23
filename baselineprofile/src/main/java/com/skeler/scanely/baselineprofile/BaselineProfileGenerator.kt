package com.skeler.scanely.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Baseline Profile Generator for Scanly.
 * 
 * Generates AOT compilation hints for critical user journeys:
 * - App startup
 * - Navigation to key screens
 * - Common actions (gallery, camera, barcode)
 * 
 * Run: ./gradlew :baselineprofile:generateBaselineProfile
 */
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
            // Start the app
            pressHome()
            startActivityAndWait()
            
            // Wait for home screen to load
            device.waitForIdle()
            
            // Scroll through home screen options
            device.wait(Until.hasObject(By.text("Capture Photo")), 5000)
            
            // Navigate to Gallery flow (most common path)
            device.findObject(By.text("From Gallery"))?.click()
            device.waitForIdle()
            
            // Go back to home
            device.pressBack()
            device.waitForIdle()
            
            // Navigate to Barcode Scanner
            device.wait(Until.hasObject(By.text("Scan Barcode/QR")), 3000)
            device.findObject(By.text("Scan Barcode/QR"))?.click()
            device.waitForIdle()
            
            // Go back
            device.pressBack()
            device.waitForIdle()
            
            // Navigate to Settings
            device.findObject(By.desc("Settings"))?.click()
            device.waitForIdle()
            
            // Go back to home
            device.pressBack()
            device.waitForIdle()
            
            // Navigate to History
            device.wait(Until.hasObject(By.text("View Previous Extracts")), 3000)
            device.findObject(By.text("View Previous Extracts"))?.click()
            device.waitForIdle()
        }
    }
}
