package com.aa.launcher.core

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build

/** 端末の種類に関する判定。 */
object DeviceInfo {
    /**
     * 折りたたみ端末か (ヒンジ角度センサーがある)。Android 11 以降の折りたたみ端末が備える。
     * それより前の端末やセンサーを公開しない端末は false (画面の縦横比でも判定する: isTwoPane)。
     */
    fun isFoldable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_HINGE_ANGLE)) return true
        val sensors = context.getSystemService(SensorManager::class.java) ?: return false
        return sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) != null
    }
}
