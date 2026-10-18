package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

  @Test
  fun testAppNameResource() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("AirLink", appName)
  }

  @Test
  fun testDevicePairingPinGenerationAndVerification() {
    val pairing = DevicePairing.getInstance()
    val pin = pairing.generateNewPin()
    assertEquals(6, pin.length)
    assertTrue(pairing.verifyPin(pin))
    assertFalse(pairing.verifyPin("000000_invalid"))
  }
}
