package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Ginger Dash", appName)
  }

  @Test
  fun `index_html asset is present and contains game canvas`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val assetManager = context.assets
    val content = assetManager.open("index.html").bufferedReader().use { it.readText() }
    org.junit.Assert.assertTrue(content.contains("Ginger Dash"))
    org.junit.Assert.assertTrue(content.contains("gameCanvas"))
  }
}
