// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ProxySettingsStoreTest {
  private lateinit var prefs: SharedPreferences
  private lateinit var editor: SharedPreferences.Editor
  private lateinit var store: ProxySettingsStore
  private val values = mutableMapOf<String, String?>()

  @Before
  fun setUp() {
    prefs = mock()
    editor = mock()

    whenever(prefs.edit()).thenReturn(editor)
    whenever(prefs.getString(anyString(), anyOrNull())).thenAnswer { invocation ->
      val key = invocation.arguments[0] as String
      values[key] ?: invocation.arguments[1] as String?
    }

    doAnswer { invocation ->
          values[invocation.arguments[0] as String] = invocation.arguments[1] as String?
          editor
        }
        .whenever(editor)
        .putString(anyString(), anyOrNull())

    doAnswer { invocation ->
          values.remove(invocation.arguments[0] as String)
          editor
        }
        .whenever(editor)
        .remove(anyString())

    whenever(editor.apply()).then {}

    store = ProxySettingsStore { prefs }
  }

  @Test
  fun `validated accepts localhost and loopback addresses`() {
    val result =
        store.validated(
            ProxySettings(socks5BindAddress = "localhost:1055", httpProxyAddress = "[::1]:8080"))

    assertTrue(result.isSuccess)
    assertEquals("localhost:1055", result.getOrThrow().socks5BindAddress)
    assertEquals("[::1]:8080", result.getOrThrow().httpProxyAddress)
  }

  @Test
  fun `validated rejects non-localhost host`() {
    val result = store.validated(ProxySettings(socks5BindAddress = "0.0.0.0:1055"))

    assertTrue(result.isFailure)
  }

  @Test
  fun `validated rejects missing port`() {
    val result = store.validated(ProxySettings(httpProxyAddress = "localhost"))

    assertTrue(result.isFailure)
  }

  @Test
  fun `save normalizes blank settings to null`() {
    store.save(ProxySettings(socks5BindAddress = "   ", httpProxyAddress = ""))

    assertNull(store.load().socks5BindAddress)
    assertNull(store.load().httpProxyAddress)
  }

  @Test
  fun `loadValidatedOrDefault returns defaults for invalid stored values`() {
    values["socks5_bind_address"] = "example.com:1080"

    val result = store.loadValidatedOrDefault()

    assertNull(result.socks5BindAddress)
    assertNull(result.httpProxyAddress)
  }

  @Test
  fun `clear removes stored values`() {
    store.save(
        ProxySettings(socks5BindAddress = "localhost:1080", httpProxyAddress = "localhost:8080"))

    store.clear()

    assertNull(store.load().socks5BindAddress)
    assertNull(store.load().httpProxyAddress)
  }
}
