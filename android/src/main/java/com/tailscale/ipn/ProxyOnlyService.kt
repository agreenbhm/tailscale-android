// Copyright (c) Tailscale Inc & AUTHORS
// SPDX-License-Identifier: BSD-3-Clause
package com.tailscale.ipn

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.tailscale.ipn.mdm.MDMSettings
import com.tailscale.ipn.ui.model.Ipn
import com.tailscale.ipn.ui.notifier.Notifier
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import libtailscale.Libtailscale

class ProxyOnlyService : Service(), libtailscale.IPNService {
  private val randomID: String = UUID.randomUUID().toString()
  private lateinit var app: App
  val scope = CoroutineScope(Dispatchers.IO)

  override fun onBind(intent: Intent?): IBinder? = null

  override fun id(): String {
    return randomID
  }

  override fun protect(fd: Int): Boolean {
    return true
  }

  override fun newBuilder(): VPNServiceBuilder {
    throw UnsupportedOperationException("VPN builder is unavailable in proxy-only mode")
  }

  override fun updateVpnStatus(status: Boolean) {
    app.getAppScopedViewModel().setVpnActive(status)
  }

  override fun onCreate() {
    super.onCreate()
    app = App.get()
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
      when (intent?.action) {
        IPNService.ACTION_STOP_VPN -> {
          app.setWantRunning(false)
          close()
          START_NOT_STICKY
        }
        IPNService.ACTION_RESTART_VPN -> {
          app.setWantRunning(false) {
            close()
            app.startVPN()
          }
          START_NOT_STICKY
        }
        else -> {
          showForegroundNotification()
          app.setWantRunning(true)
          Libtailscale.requestVPN(this)
          START_STICKY
        }
      }

  override fun close() {
    Notifier.setState(Ipn.State.Stopping)
    disconnectVPN()
    Libtailscale.serviceDisconnect(this)
  }

  override fun disconnectVPN() {
    stopSelf()
  }

  override fun onDestroy() {
    close()
    updateVpnStatus(false)
    super.onDestroy()
  }

  private fun showForegroundNotification(
      hideDisconnectAction: Boolean,
      exitNodeName: String? = null
  ) {
    startForeground(
        UninitializedApp.STATUS_NOTIFICATION_ID,
        UninitializedApp.get().buildStatusNotification(true, hideDisconnectAction, exitNodeName))
  }

  private fun showForegroundNotification() {
    scope.launch {
      val hideDisconnectAction = MDMSettings.forceEnabled.flow.first()
      val exitNodeName = UninitializedApp.getExitNodeName(Notifier.prefs.value, Notifier.netmap.value)
      showForegroundNotification(hideDisconnectAction.value, exitNodeName)
    }
  }

}
