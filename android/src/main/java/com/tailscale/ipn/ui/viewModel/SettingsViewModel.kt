// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import androidx.lifecycle.viewModelScope
import com.tailscale.ipn.App
import com.tailscale.ipn.ui.localapi.Client
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.notifier.Notifier
import com.tailscale.ipn.ui.util.LoadingIndicator
import com.tailscale.ipn.ui.util.set
import java.net.InetSocketAddress
import java.net.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import libtailscale.Libtailscale

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
  enum class SocksHealthStatus {
    CHECKING,
    HEALTHY,
    UNREACHABLE,
    DISABLED,
    INVALID
  }

  // Display name for the logged in user
  val isAdmin: StateFlow<Boolean> = MutableStateFlow(false)
  // True if tailnet lock is enabled.  nil if not yet known.
  val tailNetLockEnabled: StateFlow<Boolean?> = MutableStateFlow(null)
  // True if tailscaleDNS is enabled. nil if not yet known.
  val corpDNSEnabled: StateFlow<Boolean?> = MutableStateFlow(null)

  val socksEndpoint: StateFlow<String> = MutableStateFlow(readStoredSocksEndpoint())
  val socksHealth: StateFlow<SocksHealthStatus> = MutableStateFlow(SocksHealthStatus.CHECKING)

  init {
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

    viewModelScope.launch {
      Notifier.state.collect { updateSocksHealth(it) }
    }

    viewModelScope.launch {
      while (true) {
        updateSocksHealth(Notifier.state.value)
        delay(10_000)
      }
    }
  }

  fun updateSocksEndpoint(endpoint: String) {
    val normalized = endpoint.trim().ifEmpty { DEFAULT_SOCKS_ENDPOINT }
    App.get().getEncryptedPrefs().edit().putString(SOCKS_PROXY_ENDPOINT_PREF_KEY, normalized).commit()
    Libtailscale.notifySocksProxyConfigChanged()
    socksEndpoint.set(normalized)
    updateSocksHealth(Notifier.state.value)
  }

  private fun updateSocksHealth(state: Ipn.State?) {
    if (state == null || state < Ipn.State.Starting) {
      socksHealth.set(SocksHealthStatus.DISABLED)
      return
    }

    val (host, port) = parseHostPort(socksEndpoint.value) ?: run {
      socksHealth.set(SocksHealthStatus.INVALID)
      return
    }

    try {
      Socket().use { socket ->
        socket.connect(InetSocketAddress(host, port), 1200)
      }
      socksHealth.set(SocksHealthStatus.HEALTHY)
    } catch (_: Exception) {
      socksHealth.set(SocksHealthStatus.UNREACHABLE)
    }
  }

  private fun readStoredSocksEndpoint(): String {
    return App.get()
        .getEncryptedPrefs()
        .getString(SOCKS_PROXY_ENDPOINT_PREF_KEY, DEFAULT_SOCKS_ENDPOINT)
        ?.trim()
        ?.ifEmpty { DEFAULT_SOCKS_ENDPOINT } ?: DEFAULT_SOCKS_ENDPOINT
  }

  private fun parseHostPort(endpoint: String): Pair<String, Int>? {
    val sep = endpoint.lastIndexOf(':')
    if (sep <= 0 || sep >= endpoint.length - 1) return null

    val host = endpoint.substring(0, sep).trim()
    val port = endpoint.substring(sep + 1).toIntOrNull() ?: return null
    if (port !in 1..65535) return null

    return host to port
  }

  companion object {
    const val DEFAULT_SOCKS_ENDPOINT = "127.0.0.1:1055"
    const val SOCKS_PROXY_ENDPOINT_PREF_KEY = "socks5proxyendpoint"
  }
}
