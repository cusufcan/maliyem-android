package com.mercan.maliyem.ui.lock

import android.app.Activity
import android.app.KeyguardManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.fragment.app.FragmentActivity
import com.mercan.maliyem.R

/** Sistem doğrulamasını açar; Activity yeniden kurulursa aynı oturumun sonucunu dinler. */
internal class AppLockAuthentication(
    private val activity: FragmentActivity,
    private val onResult: (Boolean) -> Unit,
) {
    private val manager = BiometricManager.from(activity)
    private val keyguard = activity.getSystemService(KeyguardManager::class.java)
    private val screenLock = activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onResult(it.resultCode == Activity.RESULT_OK)
    }
    private val prompt = BiometricPrompt(activity, object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onResult(true)

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            if (Build.VERSION.SDK_INT == 28 && errorCode in screenLockFallbackErrors && hasDeviceCredential) {
                authenticateWithScreenLock()
            } else {
                onResult(false)
            }
        }
    })

    private val hasDeviceCredential get() = keyguard?.isDeviceSecure == true

    fun isAvailable(): Boolean = if (Build.VERSION.SDK_INT >= 30) {
        manager.canAuthenticate(BIOMETRIC_WEAK or DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    } else {
        hasDeviceCredential || manager.canAuthenticate(BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate() {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(activity.getString(R.string.app_lock_prompt_title))
        if (Build.VERSION.SDK_INT == 28 && hasDeviceCredential) {
            if (!activity.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
                authenticateWithScreenLock()
                return
            }
            // Core'daki eski parmak izi sorgusu artık boş döner. API 28'in sistem istemini
            // doğrudan dene ve PIN/desen geçişini açıkça yönet.
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK)
                .setNegativeButtonText(activity.getString(R.string.app_lock_use_screen_lock))
        } else if (Build.VERSION.SDK_INT >= 30 || hasDeviceCredential) {
            // API 26–27'de AndroidX bu birleşimi sistem PIN/desen ekranına yönlendirir.
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK)
                .setNegativeButtonText(activity.getString(R.string.add_cancel))
        }
        prompt.authenticate(builder.build())
    }

    @Suppress("DEPRECATION")
    private fun authenticateWithScreenLock() {
        val intent = keyguard?.createConfirmDeviceCredentialIntent(
            activity.getString(R.string.app_lock_prompt_title), null,
        )
        if (intent == null) onResult(false) else screenLock.launch(intent)
    }

    private companion object {
        val screenLockFallbackErrors = setOf(
            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_LOCKOUT,
            BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
        )
    }
}
