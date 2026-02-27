// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn

import android.app.Service
import kotlin.reflect.KClass

enum class RuntimeMode(val libtailscaleMode: Int, val serviceClass: KClass<out Service>) {
  Tun(0, IPNService::class),
  ProxyOnly(1, ProxyOnlyService::class),
  ;

  companion object {
    fun fromValue(value: Int): RuntimeMode {
      return entries.firstOrNull { it.libtailscaleMode == value } ?: Tun
    }
  }
}
