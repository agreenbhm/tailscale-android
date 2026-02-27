// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProxySettingsStoreTest {
  private lateinit var prefs: SharedPreferences
  private lateinit var store: ProxySettingsStore
  private val values = mutableMapOf<String, String?>()

  @Before
  fun setUp() {
    prefs = InMemorySharedPreferences(values)

    store = ProxySettingsStore { prefs }
  }

  private class InMemorySharedPreferences(private val values: MutableMap<String, String?>) :
      SharedPreferences {
    override fun getString(key: String?, defValue: String?): String? {
      if (key == null) return defValue
      return values[key] ?: defValue
    }

    override fun edit(): SharedPreferences.Editor = Editor(values)

    private class Editor(private val values: MutableMap<String, String?>) : SharedPreferences.Editor {
      override fun putString(key: String?, value: String?): SharedPreferences.Editor {
        if (key != null) values[key] = value
        return this
      }

      override fun remove(key: String?): SharedPreferences.Editor {
        if (key != null) values.remove(key)
        return this
      }

      override fun apply() = Unit

      override fun clear(): SharedPreferences.Editor = unsupported()

      override fun putStringSet(
          key: String?,
          values: MutableSet<String>?
      ): SharedPreferences.Editor = unsupported()

      override fun putInt(key: String?, value: Int): SharedPreferences.Editor = unsupported()

      override fun putLong(key: String?, value: Long): SharedPreferences.Editor = unsupported()

      override fun putFloat(key: String?, value: Float): SharedPreferences.Editor = unsupported()

      override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor = unsupported()

      override fun commit(): Boolean = unsupported()

      private fun unsupported(): Nothing =
          throw UnsupportedOperationException("Not needed in ProxySettingsStore tests")
    }

    override fun getAll(): MutableMap<String, *> = values

    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        defValues

    override fun getInt(key: String?, defValue: Int): Int = defValue

    override fun getLong(key: String?, defValue: Long): Long = defValue

    override fun getFloat(key: String?, defValue: Float): Float = defValue

    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue

    override fun contains(key: String?): Boolean = key != null && values.containsKey(key)

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?
    ) = Unit
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
