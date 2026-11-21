package com.sremonak.netspeedmeter.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class IconMode(val label: String) { TOTAL("Total"), DOWNLOAD("Download"), UPLOAD("Upload") }
enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }

data class AppSettings(
    val meterEnabled: Boolean = true,
    val useBits: Boolean = false,
    val iconMode: IconMode = IconMode.TOTAL,
    val updateIntervalMs: Long = 1000L,
    val startOnBoot: Boolean = true,
    val showOnLockScreen: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val pureBlack: Boolean = false,
    val preferSystemStats: Boolean = true,
    val dailyLimitMb: Long = 0L,
    val monthlyLimitMb: Long = 0L,
    val billingCycleDay: Int = 1,
    val onboardingDone: Boolean = false,
)

/** Small SharedPreferences backed settings store exposed as a [StateFlow]. */
class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val current: AppSettings get() = _state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings) {
        val next = transform(_state.value)
        save(next)
        _state.value = next
    }

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            meterEnabled = prefs.getBoolean("meterEnabled", d.meterEnabled),
            useBits = prefs.getBoolean("useBits", d.useBits),
            iconMode = enumOrDefault(prefs.getString("iconMode", null), d.iconMode),
            updateIntervalMs = prefs.getLong("updateIntervalMs", d.updateIntervalMs),
            startOnBoot = prefs.getBoolean("startOnBoot", d.startOnBoot),
            showOnLockScreen = prefs.getBoolean("showOnLockScreen", d.showOnLockScreen),
            themeMode = enumOrDefault(prefs.getString("themeMode", null), d.themeMode),
            dynamicColor = prefs.getBoolean("dynamicColor", d.dynamicColor),
            pureBlack = prefs.getBoolean("pureBlack", d.pureBlack),
            preferSystemStats = prefs.getBoolean("preferSystemStats", d.preferSystemStats),
            dailyLimitMb = prefs.getLong("dailyLimitMb", d.dailyLimitMb),
            monthlyLimitMb = prefs.getLong("monthlyLimitMb", d.monthlyLimitMb),
            billingCycleDay = prefs.getInt("billingCycleDay", d.billingCycleDay),
            onboardingDone = prefs.getBoolean("onboardingDone", d.onboardingDone),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putBoolean("meterEnabled", s.meterEnabled)
            .putBoolean("useBits", s.useBits)
            .putString("iconMode", s.iconMode.name)
            .putLong("updateIntervalMs", s.updateIntervalMs)
            .putBoolean("startOnBoot", s.startOnBoot)
            .putBoolean("showOnLockScreen", s.showOnLockScreen)
            .putString("themeMode", s.themeMode.name)
            .putBoolean("dynamicColor", s.dynamicColor)
            .putBoolean("pureBlack", s.pureBlack)
            .putBoolean("preferSystemStats", s.preferSystemStats)
            .putLong("dailyLimitMb", s.dailyLimitMb)
            .putLong("monthlyLimitMb", s.monthlyLimitMb)
            .putInt("billingCycleDay", s.billingCycleDay)
            .putBoolean("onboardingDone", s.onboardingDone)
            .apply()
    }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
