// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause

package com.tailscale.ipn.ui.viewModel

import android.app.Application
import android.net.VpnService
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.tailscale.ipn.UninitializedApp
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.notifier.Notifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class VpnViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
  @Suppress("UNCHECKED_CAST")
  override fun <T : ViewModel> create(modelClass: Class<T>): T {
    if (modelClass.isAssignableFrom(VpnViewModel::class.java)) {
      return VpnViewModel(application) as T
    }
    throw IllegalArgumentException("Unknown ViewModel class")
  }
}

// Application context aware view model that tracks whether the VPN has been prepared. This must be
// application scoped because Tailscale might be toggled on and off outside of the activity
// lifecycle.
class VpnViewModel(application: Application) : AndroidViewModel(application) {
  // Whether the VPN is prepared. This is set to true if the VPN application is already prepared, or
  // if the user has previously consented to the VPN application. This is used to determine whether
  // a VPN permission launcher needs to be shown.
  val _vpnPrepared = MutableStateFlow(false)
  val vpnPrepared: StateFlow<Boolean> = _vpnPrepared
  // Whether a VPN interface has been established. This is set by net.updateTUN upon
  // VpnServiceBuilder.establish, and consumed by UI to reflect VPN state.
  val _vpnActive = MutableStateFlow(false)
  val vpnActive: StateFlow<Boolean> = _vpnActive
  val _proxyOnlyMode = MutableStateFlow(false)
  val proxyOnlyMode: StateFlow<Boolean> = _proxyOnlyMode
  val TAG = "VpnViewModel"

  init {
    _proxyOnlyMode.value = UninitializedApp.get().isProxyOnlyMode()
    prepareVpn()
  }

  private fun prepareVpn() {
    if (proxyOnlyMode.value) {
      setVpnPrepared(true)
      return
    }
    // Check if the user has granted permission yet.
    if (!vpnPrepared.value) {
      val vpnIntent = VpnService.prepare(getApplication())
      if (vpnIntent != null) {
        setVpnPrepared(false)
        Log.d(TAG, "VpnService.prepare returned non-null intent")
      } else {
        setVpnPrepared(true)
        Log.d(TAG, "VpnService.prepare returned null intent, VPN is already prepared")
      }
    }
  }

  fun setVpnActive(isActive: Boolean) {
    _vpnActive.value = isActive
  }

  fun setVpnPrepared(isPrepared: Boolean) {
    _vpnPrepared.value = isPrepared
  }

  fun setProxyOnlyMode(enabled: Boolean) {
    _proxyOnlyMode.value = enabled
    UninitializedApp.get().setProxyOnlyMode(enabled)

    val runningState = Notifier.state.value
    val shouldRestartService = runningState == Ipn.State.Running || runningState == Ipn.State.Starting

    if (enabled) {
      setVpnPrepared(true)
      setVpnActive(false)
    } else {
      prepareVpn()
    }

    if (shouldRestartService) {
      UninitializedApp.get().restartVPN()
    }
  }
}
