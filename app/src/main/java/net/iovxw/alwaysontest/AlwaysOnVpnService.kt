package net.iovxw.alwaysontest

import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileInputStream

class AlwaysOnVpnService : VpnService() {

    companion object {
        private const val TAG = "VpnService"

        private var instance: AlwaysOnVpnService? = null

        private val _serviceRunning = MutableStateFlow(false)
        val serviceRunning = _serviceRunning.asStateFlow()

        private val _tunnelEstablished = MutableStateFlow(false)
        val tunnelEstablished = _tunnelEstablished.asStateFlow()

        private val _alwaysOn = MutableStateFlow(false)
        val alwaysOn = _alwaysOn.asStateFlow()

        private val _lockdown = MutableStateFlow(false)
        val lockdown = _lockdown.asStateFlow()

        private val _startedBySystem = MutableStateFlow(false)
        val startedBySystem = _startedBySystem.asStateFlow()

        fun refreshState() {
            val svc = instance
            if (svc != null) {
                _alwaysOn.value = svc.isAlwaysOn
                _lockdown.value = svc.isLockdownEnabled
                _tunnelEstablished.value = svc.vpnInterface != null
                AppLog.d(TAG, "refreshState: isAlwaysOn=${svc.isAlwaysOn} isLockdownEnabled=${svc.isLockdownEnabled} tunnel=${svc.vpnInterface != null}")
            } else {
                AppLog.d(TAG, "refreshState: service instance is null")
            }
        }

        fun disconnectTunnel() {
            val svc = instance
            if (svc != null) {
                svc.closeTunnel()
                refreshState()
                AppLog.d(TAG, "disconnectTunnel: tunnel closed, service still running")
            } else {
                AppLog.d(TAG, "disconnectTunnel: no service instance")
            }
        }

        fun connectTunnel() {
            val svc = instance
            if (svc != null) {
                svc.establishTunnel()
                refreshState()
            } else {
                AppLog.d(TAG, "connectTunnel: no service instance")
            }
        }

        fun killService() {
            val svc = instance
            if (svc != null) {
                svc.closeTunnel()
                svc.stopSelf()
                AppLog.d(TAG, "killService: tunnel closed, stopSelf called")
            } else {
                AppLog.d(TAG, "killService: no service instance")
            }
        }
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var readerThread: Thread? = null

    private fun establishTunnel() {
        if (vpnInterface != null) {
            AppLog.d(TAG, "Tunnel already established")
            return
        }

        try {
            val builder = Builder()
                .addAddress("10.0.0.2", 32)
                .addRoute("0.0.0.0", 0)
                .setMtu(1500)
                .setSession("AlwaysOnTest")
                .addAllowedApplication(packageName)

            vpnInterface = builder.establish()
            _tunnelEstablished.value = vpnInterface != null
            AppLog.d(TAG, "establish() returned: fd=${vpnInterface?.fd}")

            vpnInterface?.let { pfd ->
                readerThread = Thread {
                    val buffer = ByteArray(32767)
                    val input = FileInputStream(pfd.fileDescriptor)
                    try {
                        while (true) {
                            val len = input.read(buffer)
                            if (len <= 0) break
                        }
                    } catch (_: Exception) { }
                }.apply {
                    name = "VpnReaderThread"
                    isDaemon = true
                    start()
                }
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "establish() failed", e)
        }
    }

    private fun closeTunnel() {
        readerThread?.interrupt()
        readerThread = null
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            AppLog.w(TAG, "Error closing tunnel: ${e.message}")
        }
        vpnInterface = null
        _tunnelEstablished.value = false
        AppLog.d(TAG, "Tunnel closed")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLog.newGroup()
        AppLog.d(TAG, "onStartCommand: action=${intent?.action} flags=$flags")
        instance = this
        _serviceRunning.value = true
        val isSystemStart = intent?.action == SERVICE_INTERFACE
        _startedBySystem.value = isSystemStart
        _alwaysOn.value = isAlwaysOn
        _lockdown.value = isLockdownEnabled
        AppLog.d(TAG, "systemStart=$isSystemStart isAlwaysOn=$isAlwaysOn lockdown=$isLockdownEnabled")

        if (intent?.getBooleanExtra("auto_connect", false) == true || isSystemStart) {
            establishTunnel()
        }

        return START_STICKY
    }

    override fun onRevoke() {
        AppLog.newGroup()
        AppLog.d(TAG, "onRevoke")
        closeTunnel()
        instance = null
        _serviceRunning.value = false
        _alwaysOn.value = false
        _lockdown.value = false
        stopSelf()
    }

    override fun onDestroy() {
        AppLog.newGroup()
        AppLog.d(TAG, "onDestroy")
        closeTunnel()
        instance = null
        _serviceRunning.value = false
        _alwaysOn.value = false
        _lockdown.value = false
        _startedBySystem.value = false
        _tunnelEstablished.value = false
        super.onDestroy()
    }
}
