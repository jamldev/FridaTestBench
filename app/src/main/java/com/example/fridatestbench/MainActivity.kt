package com.example.fridatestbench

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.browser.customtabs.CustomTabsIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.fridatestbench.checks.CarrierLocaleChecks
import com.example.fridatestbench.checks.CarrierLocaleSnapshot
import com.example.fridatestbench.checks.DeviceSecurityChecks
import com.example.fridatestbench.checks.DeviceSecuritySnapshot
import com.example.fridatestbench.checks.PlayIntegrityChecks
import com.example.fridatestbench.checks.PlayIntegritySnapshot
import com.example.fridatestbench.crypto.EncryptionSnapshot
import com.example.fridatestbench.crypto.EncryptionUtils
import com.example.fridatestbench.crypto.stringfog.StringDecryptCheck
import com.example.fridatestbench.network.NetworkOperations
import com.example.fridatestbench.network.NetworkOperationsSnapshot
import com.example.fridatestbench.storage.LocalDataOperations
import com.example.fridatestbench.storage.LocalDataSnapshot
import com.example.fridatestbench.startup.StartupOperations
import com.example.fridatestbench.startup.StartupOperationsSnapshot
import com.example.fridatestbench.ui.theme.FridaTestBenchTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DCL_PAYLOAD = "super secret DCL PAYLOAD"
private const val SAVED_URL = "https://bet365.com"
private const val GOOGLE_URL = "https://www.google.com"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val diagnosticsModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return DiagnosticsViewModel(applicationContext) as T
                }
            },
        )[DiagnosticsViewModel::class.java]

        setContent {
            FridaTestBenchTheme {
                TestBenchApp(
                    diagnostics = diagnosticsModel.diagnostics,
                    onRun = diagnosticsModel::run,
                )
            }
        }
    }
}

private class DiagnosticsViewModel(private val context: Context) : ViewModel() {
    private val playIntegrityChecks = PlayIntegrityChecks(context)
    var diagnostics by mutableStateOf(AppDiagnosticsState())
        private set

    init {
        // The ViewModel survives activity recreation, so launch tests run only once.
        // Log URL fixtures on launch; opening URLs still requires a button press.
        DiagnosticBenchmark.entries.forEach(::run)
        UrlLoadingLog.entries.forEach { it.log() }
    }

    fun run(benchmark: DiagnosticBenchmark) {
        Log.d(benchmark.name, benchmark.logSentence)
        when (benchmark) {
            DiagnosticBenchmark.PLAY_INTEGRITY -> viewModelScope.runBenchmark(
                onStateChange = { diagnostics = diagnostics.copy(playIntegrity = it) },
            ) {
                playIntegrityChecks.collect()
            }

            DiagnosticBenchmark.SECURITY -> viewModelScope.runBenchmark(
                onStateChange = { diagnostics = diagnostics.copy(security = it) },
            ) {
                DeviceSecurityChecks(context).collect()
            }

            DiagnosticBenchmark.NETWORK -> viewModelScope.runBenchmark(
                onStateChange = { diagnostics = diagnostics.copy(network = it) },
            ) {
                NetworkOperations().run()
            }

            DiagnosticBenchmark.STARTUP -> viewModelScope.runBenchmark(
                onStateChange = { diagnostics = diagnostics.copy(startup = it) },
            ) {
                StartupOperations(context).run()
            }

            DiagnosticBenchmark.CARRIER_LOCALE -> viewModelScope.runBenchmark(
                onStateChange = { diagnostics = diagnostics.copy(carrierLocale = it) },
            ) {
                CarrierLocaleChecks(context).collect()
            }

            DiagnosticBenchmark.ENCRYPTION -> viewModelScope.runBenchmark(
                onStateChange = { diagnostics = diagnostics.copy(encryption = it) },
            ) {
                EncryptionUtils().runRoundTrips(DCL_PAYLOAD)
            }

            DiagnosticBenchmark.STRING_DECRYPTION -> viewModelScope.runBenchmark(
                onStateChange = { diagnostics = diagnostics.copy(stringDecryption = it) },
            ) {
                StringDecryptCheck().apply {
                    decryptStrings()
                    decryptAesStrings()
                    decryptCustomXorStrings()
                }
            }

            DiagnosticBenchmark.LOCAL_DATA -> viewModelScope.runBenchmark(
                onStateChange = { diagnostics = diagnostics.copy(localData = it) },
            ) {
                LocalDataOperations(context).runRoundTrips(
                    payload = DCL_PAYLOAD,
                    url = SAVED_URL,
                )
            }
        }
    }
}

private data class BenchmarkState<T>(
    val result: T? = null,
    val isRunning: Boolean = false,
    val error: String? = null,
)

private data class AppDiagnosticsState(
    val playIntegrity: BenchmarkState<PlayIntegritySnapshot> = BenchmarkState(),
    val startup: BenchmarkState<StartupOperationsSnapshot> = BenchmarkState(),
    val security: BenchmarkState<DeviceSecuritySnapshot> = BenchmarkState(),
    val carrierLocale: BenchmarkState<CarrierLocaleSnapshot> = BenchmarkState(),
    val encryption: BenchmarkState<EncryptionSnapshot> = BenchmarkState(),
    val stringDecryption: BenchmarkState<StringDecryptCheck> = BenchmarkState(),
    val localData: BenchmarkState<LocalDataSnapshot> = BenchmarkState(),
    val network: BenchmarkState<NetworkOperationsSnapshot> = BenchmarkState(),
)

private enum class DiagnosticBenchmark(val logSentence: String) {
    PLAY_INTEGRITY("Curious robots quietly collect purple pebbles beneath glowing stars tonight."),
    SECURITY("Tiny dragons carefully guard silver lanterns beside ancient castle gates."),
    NETWORK("Friendly otters happily deliver secret letters across calm rivers daily."),
    STARTUP("Sleepy astronauts slowly unpack fresh sandwiches inside their moonbase kitchen."),
    CARRIER_LOCALE("Bright parrots eagerly translate strange greetings for distant island travelers."),
    ENCRYPTION("Clever foxes secretly hide golden keys beneath snowy forest branches."),
    STRING_DECRYPTION("Amber leaves drift slowly past the old stone garden wall."),
    LOCAL_DATA("Patient squirrels neatly store crunchy acorns inside hollow wooden towers."),
}

private enum class UrlLoadingLog(val tag: String, val logSentence: String) {
    WEB_VIEW(
        "UrlLoading.WebView",
        "Playful penguins proudly paint colorful pictures near frozen mountain lakes.",
    ),
    ACTION_VIEW(
        "UrlLoading.ActionView",
        "Gentle whales softly sing cheerful melodies beneath sparkling ocean waves.",
    ),
    CUSTOM_TABS(
        "UrlLoading.CustomTabs",
        "Brave rabbits swiftly chase floating bubbles through sunny meadow paths.",
    );

    fun log() {
        Log.d(tag, logSentence)
    }
}

private fun <T> CoroutineScope.runBenchmark(
    onStateChange: (BenchmarkState<T>) -> Unit,
    operation: suspend () -> T,
) {
    launch {
        onStateChange(BenchmarkState(isRunning = true))
        val result = runCatching {
            withContext(Dispatchers.IO) { operation() }
        }
        onStateChange(
            result.fold(
                onSuccess = { BenchmarkState(result = it) },
                onFailure = { error ->
                    if (error is CancellationException) throw error
                    BenchmarkState(error = error.message ?: error.javaClass.simpleName)
                },
            ),
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun TestBenchApp(
    diagnostics: AppDiagnosticsState,
    onRun: (DiagnosticBenchmark) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(title = { Text("Device Test Bench") })
        },
    ) { innerPadding ->
        DiagnosticsScreen(
            diagnostics = diagnostics,
            onRun = onRun,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

@Composable
private fun DiagnosticsScreen(
    diagnostics: AppDiagnosticsState,
    onRun: (DiagnosticBenchmark) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column {
            Text(
                text = "Runtime diagnostics",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Tests run once on launch. Use the buttons to rerun them. URL loading runs only when pressed.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SecurityCard(
            state = diagnostics.security,
            onRun = { onRun(DiagnosticBenchmark.SECURITY) },
        )
        PlayIntegrityCard(
            state = diagnostics.playIntegrity,
            onRun = { onRun(DiagnosticBenchmark.PLAY_INTEGRITY) },
        )
        UrlLoadingCard()
        NetworkRequestsCard(
            state = diagnostics.network,
            onRun = { onRun(DiagnosticBenchmark.NETWORK) },
        )
        StartupOperationsCard(
            state = diagnostics.startup,
            onRun = { onRun(DiagnosticBenchmark.STARTUP) },
        )
        CarrierLocaleCard(
            state = diagnostics.carrierLocale,
            onRun = { onRun(DiagnosticBenchmark.CARRIER_LOCALE) },
        )
        EncryptionCard(
            state = diagnostics.encryption,
            onRun = { onRun(DiagnosticBenchmark.ENCRYPTION) },
        )
        StringDecryptionCard(
            state = diagnostics.stringDecryption,
            onRun = { onRun(DiagnosticBenchmark.STRING_DECRYPTION) },
        )
        LocalDataCard(
            state = diagnostics.localData,
            onRun = { onRun(DiagnosticBenchmark.LOCAL_DATA) },
        )

        Text(
            text = "This is an instrumentation test bench. The fixed AES key and IV are intentionally insecure, root/emulator results are heuristic, and currency is inferred from the SIM country.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PlayIntegrityCard(
    state: BenchmarkState<PlayIntegritySnapshot>,
    onRun: () -> Unit,
) {
    BenchmarkCard(title = "Google Play Integrity", state = state, onRun = onRun) {
            snapshot,
            pendingValue,
        ->
        val unavailable = snapshot?.configurationMessage?.let { "Not configured" } ?: pendingValue
        snapshot?.configurationMessage?.let {
            BenchmarkStatus(it)
            Spacer(modifier = Modifier.height(8.dp))
        }
        DiagnosticRow("MEETS_STRONG_INTEGRITY", snapshot?.meetsStrongIntegrity?.toString() ?: unavailable)
        DiagnosticRow("MEETS_DEVICE_INTEGRITY", snapshot?.meetsDeviceIntegrity?.toString() ?: unavailable)
        DiagnosticRow(
            "MEETS_BASIC_INTEGRITY",
            snapshot?.meetsBasicIntegrity?.toString() ?: unavailable,
            showDivider = false,
        )
        BenchmarkStatus("BASIC and STRONG require additional device labels enabled in Play Console.")
    }
}

@Composable
private fun NetworkRequestsCard(
    state: BenchmarkState<NetworkOperationsSnapshot>,
    onRun: () -> Unit,
) {
    BenchmarkCard(title = "HTTPS GET requests", state = state, onRun = onRun) {
            snapshot,
            pendingValue,
        ->
        val transports = listOf(
            NetworkOperations.TRANSPORT_URL_CONNECTION,
            NetworkOperations.TRANSPORT_TLS_SOCKET,
        )
        transports.forEachIndexed { index, transport ->
            val request = snapshot?.requests?.getOrNull(index)
            Text(
                text = "${index + 1}. ${request?.transport ?: transport}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            DiagnosticRow(
                "GET",
                request?.url ?: if (snapshot == null) pendingValue else NetworkOperations.API_URL,
                code = true,
            )
            DiagnosticRow(
                label = "HTTP status",
                value = request?.let { it.statusCode?.toString() ?: "Failed" } ?: pendingValue,
            )
            DiagnosticRow(
                label = "URL payload JSON",
                value = request?.let {
                    it.urlPayloadJson?.toString(2)
                        ?: "Error: ${it.error ?: "Unknown error"}"
                } ?: pendingValue,
                showDivider = false,
                code = true,
            )
            if (index != transports.lastIndex) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun UrlLoadingCard() {
    val context = LocalContext.current

    DiagnosticCard(title = "URL loading") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    UrlLoadingLog.WEB_VIEW.log()
                    context.startActivity(Intent(context, GoogleWebViewActivity::class.java))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Google via WebView.loadUrl")
            }
            Button(
                onClick = {
                    UrlLoadingLog.ACTION_VIEW.log()
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GOOGLE_URL)))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Google via Intent.ACTION_VIEW")
            }
            Button(
                onClick = {
                    UrlLoadingLog.CUSTOM_TABS.log()
                    CustomTabsIntent.Builder()
                        .build()
                        .launchUrl(context, Uri.parse(GOOGLE_URL))
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Google via CustomTabsIntent.launchUrl")
            }
        }
    }
}

@Composable
private fun StartupOperationsCard(
    state: BenchmarkState<StartupOperationsSnapshot>,
    onRun: () -> Unit,
) {
    BenchmarkCard(title = "Startup operations", state = state, onRun = onRun) {
            snapshot,
            pendingValue,
        ->
        DiagnosticRow("JSON object", snapshot?.fillerJson ?: pendingValue, code = true)
        DiagnosticRow("Opened file", snapshot?.fileName ?: pendingValue, code = true)
        DiagnosticRow("File contents", snapshot?.fileContents ?: pendingValue, code = true)
        DiagnosticRow("System.load", snapshot?.systemLoadResult ?: pendingValue, code = true)
        DiagnosticRow(
            "System.loadLibrary",
            snapshot?.systemLoadLibraryResult ?: pendingValue,
            code = true,
        )
        DiagnosticRow("DCL local file", snapshot?.dexPayloadFileName ?: pendingValue, code = true)
        DiagnosticRow(
            "DexClassLoader",
            snapshot?.dexClassLoaderResult ?: pendingValue,
            code = true,
        )
        DiagnosticRow("loadClass", snapshot?.loadClassResult ?: pendingValue, code = true)
        DiagnosticRow(
            "reflect.Method",
            snapshot?.reflectMethodResult ?: pendingValue,
            showDivider = false,
            code = true,
        )
    }
}

@Composable
private fun SecurityCard(
    state: BenchmarkState<DeviceSecuritySnapshot>,
    onRun: () -> Unit,
) {
    BenchmarkCard(title = "Security and system", state = state, onRun = onRun) {
            snapshot,
            pendingValue,
        ->
        DiagnosticRow(
            "Battery",
            snapshot?.let { it.batteryPercentage?.let { percentage -> "$percentage%" } ?: "Unavailable" }
                ?: pendingValue,
        )
        DiagnosticRow(
            "Root indicators",
            snapshot?.let { if (it.rootDetected) "Detected" else "Not detected" } ?: pendingValue,
        )
        DiagnosticRow(
            "Emulator indicators",
            snapshot?.let { if (it.emulatorDetected) "Detected" else "Not detected" }
                ?: pendingValue,
        )
        DiagnosticRow(
            "Development settings",
            snapshot?.let { enabledLabel(it.developmentSettingsEnabled) } ?: pendingValue,
        )
        DiagnosticRow(
            "ADB",
            snapshot?.let { enabledLabel(it.adbEnabled) } ?: pendingValue,
            showDivider = false,
        )
    }
}

@Composable
private fun CarrierLocaleCard(
    state: BenchmarkState<CarrierLocaleSnapshot>,
    onRun: () -> Unit,
) {
    BenchmarkCard(title = "Carrier and locale", state = state, onRun = onRun) {
            snapshot,
            pendingValue,
        ->
        DiagnosticRow("SIM state", snapshot?.simState ?: pendingValue)
        DiagnosticRow(
            "SIM operator",
            snapshot?.let { it.simOperatorName ?: "Unavailable" } ?: pendingValue,
        )
        DiagnosticRow(
            "SIM operator code",
            snapshot?.let { it.simOperatorCode ?: "Unavailable" } ?: pendingValue,
            code = true,
        )
        DiagnosticRow(
            "MCC",
            snapshot?.let { it.mobileCountryCode ?: "Unavailable" } ?: pendingValue,
            code = true,
        )
        DiagnosticRow("Device language", snapshot?.deviceLanguage ?: pendingValue)
        DiagnosticRow("Time zone", snapshot?.timeZone ?: pendingValue)
        DiagnosticRow(
            "Inferred currency",
            snapshot?.let { it.inferredCurrencyCode ?: "Unavailable" } ?: pendingValue,
            showDivider = false,
        )
    }
}

@Composable
private fun EncryptionCard(
    state: BenchmarkState<EncryptionSnapshot>,
    onRun: () -> Unit,
) {
    BenchmarkCard(title = "Encryption utilities", state = state, onRun = onRun) {
            snapshot,
            pendingValue,
        ->
        DiagnosticRow("Plaintext", snapshot?.plaintext ?: pendingValue, code = true)
        DiagnosticRow("Base64 encoded", snapshot?.base64Encoded ?: pendingValue, code = true)
        DiagnosticRow("Base64 decoded", snapshot?.base64Decoded ?: pendingValue, code = true)
        DiagnosticRow(
            "AES-256 ciphertext",
            snapshot?.aesEncryptedBase64 ?: pendingValue,
            code = true,
        )
        DiagnosticRow("AES-256 decrypted", snapshot?.aesDecrypted ?: pendingValue, code = true)
        DiagnosticRow("AES key (Base64)", snapshot?.aesKeyBase64 ?: pendingValue, code = true)
        DiagnosticRow(
            "AES IV (Base64)",
            snapshot?.aesIvBase64 ?: pendingValue,
            showDivider = false,
            code = true,
        )
    }
}

@Composable
private fun StringDecryptionCard(
    state: BenchmarkState<StringDecryptCheck>,
    onRun: () -> Unit,
) {
    BenchmarkCard(title = "String decryption", state = state, onRun = onRun) { result, pendingValue ->
        val style = MaterialTheme.typography.bodyMedium
        DecryptedStringGroup("StringFog (XOR)", result, pendingValue) { check ->
            Text(text = check.stringFogFirst, style = style)
            Text(text = check.stringFogSecond, style = style)
            Text(text = check.stringFogThird, style = style)
            Text(text = check.stringFogFourth, style = style)
            Text(text = check.stringFogFifth, style = style)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        DecryptedStringGroup("AES-256 (GCM)", result, pendingValue) { check ->
            Text(text = check.aesFirst, style = style)
            Text(text = check.aesSecond, style = style)
            Text(text = check.aesThird, style = style)
            Text(text = check.aesFourth, style = style)
            Text(text = check.aesFifth, style = style)
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        DecryptedStringGroup("Custom XOR", result, pendingValue) { check ->
            Text(text = check.xorFirst, style = style)
            Text(text = check.xorSecond, style = style)
            Text(text = check.xorThird, style = style)
            Text(text = check.xorFourth, style = style)
            Text(text = check.xorFifth, style = style)
        }
    }
}

@Composable
private fun DecryptedStringGroup(
    title: String,
    result: StringDecryptCheck?,
    pendingValue: String,
    content: @Composable (StringDecryptCheck) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        if (result == null) {
            BenchmarkStatus(pendingValue)
        } else {
            content(result)
        }
    }
}

@Composable
private fun LocalDataCard(
    state: BenchmarkState<LocalDataSnapshot>,
    onRun: () -> Unit,
) {
    BenchmarkCard(title = "Local persistence", state = state, onRun = onRun) {
            snapshot,
            pendingValue,
        ->
        DiagnosticRow("File", snapshot?.fileName ?: pendingValue, code = true)
        DiagnosticRow("File write", snapshot?.fileWrittenValue ?: pendingValue, code = true)
        DiagnosticRow("File read", snapshot?.fileReadValue ?: pendingValue, code = true)
        DiagnosticRow("Preferences", snapshot?.preferencesName ?: pendingValue, code = true)
        DiagnosticRow("Preference key", snapshot?.preferenceKey ?: pendingValue, code = true)
        DiagnosticRow(
            "Preference read",
            snapshot?.let { it.preferenceReadValue ?: "Unavailable" } ?: pendingValue,
            code = true,
        )
        DiagnosticRow("SQLite database", snapshot?.databaseName ?: pendingValue, code = true)
        DiagnosticRow(
            "SQLite read",
            snapshot?.databaseReadValues?.joinToString("\n") ?: pendingValue,
            showDivider = false,
            code = true,
        )
    }
}

@Composable
private fun <T> BenchmarkCard(
    title: String,
    state: BenchmarkState<T>,
    onRun: () -> Unit,
    content: @Composable (snapshot: T?, pendingValue: String) -> Unit,
) {
    DiagnosticCard(title = title) {
        Button(
            onClick = onRun,
            enabled = !state.isRunning,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                when {
                    state.isRunning -> "Running…"
                    state.result != null -> "Run again"
                    else -> "Run test"
                },
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (state.error != null) {
            BenchmarkStatus("Failed: ${state.error}")
            Spacer(modifier = Modifier.height(8.dp))
        }
        val pendingValue = when {
            state.isRunning -> "Running…"
            state.error != null -> "No result"
            else -> "Not run yet"
        }
        content(state.result, pendingValue)
    }
}

@Composable
private fun BenchmarkStatus(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DiagnosticCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    showDivider: Boolean = true,
    code: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            fontFamily = if (code) FontFamily.Monospace else FontFamily.Default,
        )
    }
    if (showDivider) {
        HorizontalDivider()
    }
}

private fun enabledLabel(enabled: Boolean): String = if (enabled) "Enabled" else "Disabled"
