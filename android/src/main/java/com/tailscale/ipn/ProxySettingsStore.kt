// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn

import android.content.SharedPreferences
import java.util.Locale

/**
 * Persisted local proxy settings used to populate LocalAPI start options.
 */
data class ProxySettings(
    val socks5BindAddress: String? = null,
    val httpProxyAddress: String? = null,
)

class ProxySettingsStore(private val prefsProvider: () -> SharedPreferences) {
  companion object {
    private const val PREF_KEY_SOCKS5_BIND_ADDRESS = "socks5_bind_address"
    private const val PREF_KEY_HTTP_PROXY_ADDRESS = "http_proxy_address"

    private val localhostHosts = setOf("localhost", "127.0.0.1", "::1")
  }

  fun load(): ProxySettings {
    val prefs = prefsProvider()
    return ProxySettings(
        socks5BindAddress = prefs.getString(PREF_KEY_SOCKS5_BIND_ADDRESS, null),
        httpProxyAddress = prefs.getString(PREF_KEY_HTTP_PROXY_ADDRESS, null),
    )
  }

  fun save(settings: ProxySettings) {
    prefsProvider()
        .edit()
        .putString(PREF_KEY_SOCKS5_BIND_ADDRESS, settings.socks5BindAddress.normalizedOrNull())
        .putString(PREF_KEY_HTTP_PROXY_ADDRESS, settings.httpProxyAddress.normalizedOrNull())
        .apply()
  }

  fun clear() {
    prefsProvider()
        .edit()
        .remove(PREF_KEY_SOCKS5_BIND_ADDRESS)
        .remove(PREF_KEY_HTTP_PROXY_ADDRESS)
        .apply()
  }

  fun loadValidatedOrDefault(onInvalid: (Throwable) -> Unit = {}): ProxySettings {
    return validated(load()).getOrElse {
      onInvalid(it)
      ProxySettings()
    }
  }

  fun validated(settings: ProxySettings = load()): Result<ProxySettings> {
    val socksAddress = validateBindAddress(settings.socks5BindAddress, "SOCKS5")
    val httpAddress = validateBindAddress(settings.httpProxyAddress, "HTTP proxy")
    return if (socksAddress.isFailure) {
      Result.failure(socksAddress.exceptionOrNull()!!)
    } else if (httpAddress.isFailure) {
      Result.failure(httpAddress.exceptionOrNull()!!)
    } else {
      Result.success(
          ProxySettings(
              socks5BindAddress = socksAddress.getOrThrow(),
              httpProxyAddress = httpAddress.getOrThrow(),
          ))
    }
  }

  private fun validateBindAddress(input: String?, label: String): Result<String?> {
    val value = input.normalizedOrNull() ?: return Result.success(null)
    val hostPort = parseHostPort(value)
    if (hostPort == null) {
      return Result.failure(IllegalArgumentException("$label must be in host:port format"))
    }

    val host = hostPort.first.lowercase(Locale.US)
    val port = hostPort.second
    if (host !in localhostHosts) {
      return Result.failure(IllegalArgumentException("$label host must be localhost"))
    }
    if (port !in 1..65535) {
      return Result.failure(IllegalArgumentException("$label port must be between 1 and 65535"))
    }
    return Result.success(value)
  }

  private fun parseHostPort(value: String): Pair<String, Int>? {
    // Supports host:port and [::1]:port.
    return if (value.startsWith("[")) {
      val end = value.indexOf(']')
      if (end <= 1 || end + 2 >= value.length || value[end + 1] != ':') {
        null
      } else {
        val host = value.substring(1, end)
        val port = value.substring(end + 2).toIntOrNull() ?: return null
        Pair(host, port)
      }
    } else {
      val idx = value.lastIndexOf(':')
      if (idx <= 0 || idx >= value.lastIndex) {
        null
      } else {
        val host = value.substring(0, idx)
        val port = value.substring(idx + 1).toIntOrNull() ?: return null
        Pair(host, port)
      }
    }
  }

  private fun String?.normalizedOrNull(): String? {
    val normalized = this?.trim()
    return if (normalized.isNullOrEmpty()) null else normalized
  }
}
