// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn

/**
 * Runtime mode for Android operation.
 *
 * VPN is the current default behavior and preserves existing functionality.
 * PROXY_ONLY is reserved for future work where Tailscale should run without
 * establishing an Android VPN interface.
 */
enum class TailscaleMode {
  VPN,
  PROXY_ONLY;

  companion object {
    fun fromStoredValue(value: String?): TailscaleMode {
      return values().firstOrNull { it.name == value } ?: VPN
    }
  }
}
