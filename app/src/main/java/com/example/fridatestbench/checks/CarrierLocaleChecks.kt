package com.example.fridatestbench.checks

import android.content.Context
import android.os.LocaleList
import android.telephony.TelephonyManager
import java.util.Currency
import java.util.Locale
import java.util.TimeZone

data class CarrierLocaleSnapshot(
    val simState: String,
    val simOperatorName: String?,
    val simOperatorCode: String?,
    val mobileCountryCode: String?,
    val deviceLanguage: String,
    val timeZone: String,
    val inferredCurrencyCode: String?,
)

/**
 * Collects carrier and locale signals without requesting phone-state permission.
 * Values can be absent on Wi-Fi-only devices, devices without a default SIM, or
 * when the carrier does not publish the corresponding SIM metadata.
 */
class CarrierLocaleChecks(private val context: Context) {

    fun collect(): CarrierLocaleSnapshot {
        val telephony = context.getSystemService(TelephonyManager::class.java)
        val operatorCode = telephony.safeRead { simOperator }.nonBlankOrNull()
        val simCountry = telephony.safeRead { simCountryIso }.nonBlankOrNull()

        return CarrierLocaleSnapshot(
            simState = telephony.safeRead { simStateLabel(simState) } ?: "Unavailable",
            simOperatorName = telephony.safeRead { simOperatorName }.nonBlankOrNull(),
            simOperatorCode = operatorCode,
            mobileCountryCode = mccFromOperator(operatorCode),
            deviceLanguage = primaryLocale().toLanguageTag(),
            timeZone = TimeZone.getDefault().id,
            inferredCurrencyCode = currencyForCountry(simCountry),
        )
    }

    private fun primaryLocale(): Locale {
        val locales = LocaleList.getDefault()
        return if (locales.isEmpty) Locale.getDefault() else locales[0]
    }

    private fun simStateLabel(state: Int): String = when (state) {
        TelephonyManager.SIM_STATE_ABSENT -> "Absent"
        TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network locked"
        TelephonyManager.SIM_STATE_PIN_REQUIRED -> "PIN required"
        TelephonyManager.SIM_STATE_PUK_REQUIRED -> "PUK required"
        TelephonyManager.SIM_STATE_READY -> "Ready"
        TelephonyManager.SIM_STATE_NOT_READY -> "Not ready"
        TelephonyManager.SIM_STATE_PERM_DISABLED -> "Permanently disabled"
        TelephonyManager.SIM_STATE_CARD_IO_ERROR -> "Card I/O error"
        TelephonyManager.SIM_STATE_CARD_RESTRICTED -> "Card restricted"
        TelephonyManager.SIM_STATE_UNKNOWN -> "Unknown"
        else -> "Unknown ($state)"
    }

    private inline fun <T> TelephonyManager?.safeRead(block: TelephonyManager.() -> T): T? {
        if (this == null) return null
        return runCatching { block() }.getOrNull()
    }

    private fun String?.nonBlankOrNull(): String? = this?.takeIf { it.isNotBlank() }

    companion object {
        internal fun mccFromOperator(operatorCode: String?): String? {
            return operatorCode
                ?.takeIf { it.length >= 5 && it.all(Char::isDigit) }
                ?.take(3)
        }

        internal fun currencyForCountry(countryIso: String?): String? {
            val country = countryIso
                ?.trim()
                ?.uppercase(Locale.ROOT)
                ?.takeIf { it.length == 2 }
                ?: return null

            return runCatching {
                Currency.getInstance(Locale.Builder().setRegion(country).build()).currencyCode
            }.getOrNull()
        }
    }
}
