// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.App
import com.tailscale.ipn.TailscaleMode
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.notifier.Notifier
import com.tailscale.ipn.ui.util.LoadingIndicator
import com.tailscale.ipn.ui.util.set
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class SettingsNav(
    val onNavigateToBugReport: () -> Unit,
    val onNavigateToAbout: () -> Unit,
    val onNavigateToDNSSettings: () -> Unit,
    val onNavigateToSplitTunneling: () -> Unit,
    val onNavigateToTailnetLock: () -> Unit,
    val onNavigateToSubnetRouting: () -> Unit,
    val onNavigateToMDMSettings: () -> Unit,
    val onNavigateToManagedBy: () -> Unit,
    val onNavigateToUserSwitcher: () -> Unit,
    val onNavigateToPermissions: () -> Unit,
    val onNavigateBackHome: () -> Unit,
    val onBackToSettings: () -> Unit,
)

class SettingsViewModel : IpnViewModel() {
  // Display name for the logged in user
  val isAdmin: StateFlow<Boolean> = MutableStateFlow(false)
  // True if tailnet lock is enabled.  nil if not yet known.
  val tailNetLockEnabled: StateFlow<Boolean?> = MutableStateFlow(null)
  // True if tailscaleDNS is enabled. nil if not yet known.
  val corpDNSEnabled: StateFlow<Boolean?> = MutableStateFlow(null)

  // True when app is in proxy-only mode (no Android VPN preparation/permission path).
  val isProxyOnlyMode: StateFlow<Boolean> = MutableStateFlow(false)

  // SOCKS5 server listen address for proxy-only mode.
  val socks5ServerAddress: StateFlow<String> = MutableStateFlow("")

  init {
    isProxyOnlyMode.set(App.get().tailscaleMode() == TailscaleMode.PROXY_ONLY)
    socks5ServerAddress.set(App.get().getSocks5ServerAddress())
    viewModelScope.launch {
      Notifier.netmap.collect { netmap -> isAdmin.set(netmap?.SelfNode?.isAdmin ?: false) }
    }

    Client(viewModelScope).tailnetLockStatus { result ->
      result.onSuccess { status -> tailNetLockEnabled.set(status.Enabled) }

      LoadingIndicator.stop()
    }

    viewModelScope.launch {
      Notifier.prefs.collect {
        it?.let { corpDNSEnabled.set(it.CorpDNS) } ?: run { corpDNSEnabled.set(null) }
      }
    }
  }

  fun setProxyOnlyMode(enabled: Boolean) {
    val modeChanged = isProxyOnlyMode.value != enabled
    val mode = if (enabled) TailscaleMode.PROXY_ONLY else TailscaleMode.VPN
    App.get().setTailscaleMode(mode)
    isProxyOnlyMode.set(enabled)
    if (modeChanged) {
      App.get().restartVPN()
    }
  }


  fun setSocks5ServerAddress(address: String): Boolean {
    val trimmed = address.trim()
    if (!isValidHostPort(trimmed)) {
      return false
    }
    if (socks5ServerAddress.value == trimmed) {
      return true
    }
    App.get().setSocks5ServerAddress(trimmed)
    socks5ServerAddress.set(trimmed)
    if (isProxyOnlyMode.value) {
      App.get().restartVPN()
    }
    return true
  }

  private fun isValidHostPort(value: String): Boolean {
    val idx = value.lastIndexOf(':')
    if (idx <= 0 || idx == value.length - 1) {
      return false
    }
    val host = value.substring(0, idx)
    val port = value.substring(idx + 1).toIntOrNull() ?: return false
    if (port !in 1..65535) {
      return false
    }
    return host == "127.0.0.1" || host == "localhost" || host == "::1"
  }

}
