# Manifest and exported component fixtures

The app declares the requested permissions and enables `allowBackup`,
`debuggable`, and `usesCleartextTraffic`. Debugging is enabled for both debug
and release builds. The existing backup rules remain in place.

The following components are exported without caller permissions for testing:

| Component | Minimal behavior |
| --- | --- |
| `.components.ExportedTestActivity` | Displays a fixed test message. |
| `.components.ExportedTestService` | Logs a start and stops itself; bound callers receive a basic Binder. |
| `.components.ExportedTestReceiver` | Logs receipt of a broadcast. |
| `.components.ExportedTestProvider` | Returns metadata and supports reading/writing a single dummy file. |

The provider has `grantUriPermissions="true"` and exposes
`content://com.example.fridatestbench.testprovider/sample.txt`.
It rejects other URIs and does not expose the app's other files.
Its sample file is created automatically on provider initialization.
Writing with mode `w` replaces its contents; mode `wa` appends.

With the app installed, these commands exercise the components from another
process (add `-s DEVICE_SERIAL` after `adb` if multiple devices are connected):

```powershell
adb shell am start -n com.example.fridatestbench/.components.ExportedTestActivity
adb shell am startservice -n com.example.fridatestbench/.components.ExportedTestService
adb shell am broadcast -n com.example.fridatestbench/.components.ExportedTestReceiver -a com.example.fridatestbench.action.TEST_BROADCAST
adb shell content query --uri content://com.example.fridatestbench.testprovider/sample.txt
adb shell content read --uri content://com.example.fridatestbench.testprovider/sample.txt
adb shell "printf 'Sample test data\n' | content write --uri content://com.example.fridatestbench.testprovider/sample.txt"
adb logcat -d -s ExportedTestService:I ExportedTestReceiver:I
```

Launch the test activity before starting the service so the app is in the
foreground. No component uses the previously declared permissions or sends
network requests.
