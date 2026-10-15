package com.example.exambrowser

import android.app.admin.DeviceAdminReceiver

/** Dibutuhkan agar app dapat dijadikan Device Owner (kiosk penuh, tanpa prompt pin). */
class AdminReceiver : DeviceAdminReceiver()
