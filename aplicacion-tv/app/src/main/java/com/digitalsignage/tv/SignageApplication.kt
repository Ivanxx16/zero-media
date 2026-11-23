package com.digitalsignage.tv

import android.app.Application
import android.content.Intent
import com.digitalsignage.tv.app.di.AppContainer
import com.digitalsignage.tv.app.di.DefaultAppContainer
import com.digitalsignage.tv.core.common.logging.SignageLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SignageApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        SignageLogger.i("SignageApp", "=== 0Media Digital Signage Android TV Starting ===")

        container = DefaultAppContainer(this)
        setupCrashHandler()
        initializeBackgroundServices()
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            SignageLogger.e("CrashHandler", "CRITICAL UNCAUGHT EXCEPTION on thread ${thread.name}", throwable)

            // Attempt automatic recovery by relaunching MainActivity after crash
            try {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                SignageLogger.e("CrashHandler", "Failed to auto-restart activity", e)
            }

            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun initializeBackgroundServices() {
        applicationScope.launch {
            try {
                container.seedDefaultMediaAndCampaign()
                val environment = container.preferencesDataStore.getResolvedEnvironment()
                val identity = container.getDeviceIdentityUseCase()
                SignageLogger.i("SignageApp", "Hardware Fingerprint: ${identity.hardwareFingerprint}, Pairing Status: ${identity.pairingStatus}")

                if (identity.hasCredentials) {
                    SignageLogger.i("SignageApp", "Device has valid credentials. Connecting Socket.IO...")
                    container.socketManager.connect(environment)
                }
            } catch (e: Exception) {
                SignageLogger.e("SignageApp", "Initialization exception", e)
            }
        }
    }
}
