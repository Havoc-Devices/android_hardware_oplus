/*
 * Copyright (C) 2021-2022 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.settings.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.media.AudioManager
import android.media.AudioSystem
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.InputDevice
import android.view.KeyEvent
import com.android.internal.os.DeviceKeyHandler

import java.io.File

class KeyHandler(context: Context) : DeviceKeyHandler {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val vibrator = context.getSystemService(Vibrator::class.java)

    private val packageContext = context.createPackageContext(
        KeyHandler::class.java.getPackage()!!.name, 0
    )
    private val sharedPreferences
        get() = packageContext.getSharedPreferences(
            packageContext.packageName + "_preferences",
            Context.MODE_PRIVATE or Context.MODE_MULTI_PROCESS
        )

    private var wasMuted = false
    private val broadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val stream = intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1)
            val state = intent.getBooleanExtra(AudioManager.EXTRA_STREAM_VOLUME_MUTED, false)
            if (stream == AudioSystem.STREAM_MUSIC && state == false) {
                wasMuted = false
            }
        }
    }

    init {
        context.registerReceiver(
            broadcastReceiver,
            IntentFilter(AudioManager.STREAM_MUTE_CHANGED_ACTION)
        )
    }

    override fun handleKeyEvent(event: KeyEvent): KeyEvent? {
        if (event.action != KeyEvent.ACTION_DOWN) {
            return event
        }

        if (inputManager.getInputDevice(event.deviceId).name != "oplus,hall_tri_state_key") {
            return event
        }

        when (File("/proc/tristatekey/tri_state").readText().trim()) {
            "1" -> handleMode(POSITION_TOP)
            "2" -> handleMode(POSITION_MIDDLE)
            "3" -> handleMode(POSITION_BOTTOM)
        }

        return null
    }

    private fun vibrateIfNeeded(mode: Int) {
        when (mode) {
            AudioManager.RINGER_MODE_VIBRATE -> vibrator.vibrate(MODE_VIBRATION_EFFECT)
            AudioManager.RINGER_MODE_NORMAL -> vibrator.vibrate(MODE_NORMAL_EFFECT)
        }
    }

    private fun handleMode(mode: Int) {
        val muteMedia = Settings.System.getInt(getContentResolver(),
                Settings.System.ALERT_SLIDER_MUTE_MEDIA, 0) == 1

        when (mode) {
            AudioManager.RINGER_MODE_SILENT -> {
                audioManager.setRingerModeInternal(mode)
                if (muteMedia) {
                    audioManager.adjustVolume(AudioManager.ADJUST_MUTE, 0)
                    wasMuted = true
                }
            }
            AudioManager.RINGER_MODE_VIBRATE, AudioManager.RINGER_MODE_NORMAL -> {
                audioManager.setRingerModeInternal(mode)
                if (muteMedia && wasMuted) {
                    audioManager.adjustVolume(AudioManager.ADJUST_UNMUTE, 0)
                }
            }
        }
        vibrateIfNeeded(mode)
    }

    companion object {
        private const val TAG = "KeyHandler"

        // Slider key positions
        private const val POSITION_TOP = 0
        private const val POSITION_MIDDLE = 1
        private const val POSITION_BOTTOM = 2

        // Vibration effects
        private val MODE_NORMAL_EFFECT = VibrationEffect.get(VibrationEffect.EFFECT_HEAVY_CLICK)
        private val MODE_VIBRATION_EFFECT = VibrationEffect.get(VibrationEffect.EFFECT_DOUBLE_CLICK)
    }
}
