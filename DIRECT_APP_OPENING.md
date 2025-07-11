# Direct App Opening Feature

## Overview

The Direct App Opening feature allows the agent to open apps directly using the Android package manager, bypassing the traditional UI navigation. This feature is designed for debugging purposes and can significantly speed up app opening operations.

## ⚠️ Important Notes

- **Play Store Compliance**: This feature uses the `QUERY_ALL_PACKAGES` permission, which is restricted by Google Play Store policies. It should be disabled for production releases.
- **Debug Only**: This feature is intended for debugging and development purposes only.
- **Permission Required**: The app requires the `QUERY_ALL_PACKAGES` permission to function.

## How It Works

1. **Traditional Method**: The agent searches for app icons/text on the screen and taps them
2. **Direct Method**: The agent uses the package manager to launch apps directly by package name

## Configuration

### Build Configuration

The feature is controlled by a build configuration flag in `app/build.gradle.kts`:

```kotlin
// For debug builds (development)
debug {
    buildConfigField("boolean", "ENABLE_DIRECT_APP_OPENING", "true")
}

// For release builds (production)
release {
    buildConfigField("boolean", "ENABLE_DIRECT_APP_OPENING", "false")
}
```

### Runtime Configuration

The feature is also controlled by the `AgentConfig.enableDirectAppOpening` flag:

```kotlin
val config = AgentConfigFactory.create(
    context = context,
    visionMode = visionMode,
    apiKey = apiKey,
    enableDirectAppOpening = BuildConfig.ENABLE_DIRECT_APP_OPENING
)
```

## Usage

When the feature is enabled, the agent can use the `Open_App` action:

```json
{
    "name": "Open_App",
    "arguments": {
        "app_name": "Chrome"
    }
}
```

## Implementation Details

### 1. AgentConfig Changes
- Added `enableDirectAppOpening` flag to control the feature
- Default value is `false` for safety

### 2. Finger Class Changes
- Added `openApp(packageName: String)` method
- Uses `PackageManager.getLaunchIntentForPackage()` to launch apps
- Includes proper error handling and logging

### 3. Operator Class Changes
- Updated `getAtomicActionSignatures()` to conditionally include `Open_App` action
- Enhanced `execute()` method to handle direct app opening
- Added fallback to traditional UI navigation if direct opening fails
- Added helper method `findPackageNameFromAppName()` to map app names to package names

### 4. Atomic Action Signatures
The `Open_App` action is only available when `enableDirectAppOpening` is `true`:

```kotlin
"Open_App" to AtomicActionSignature(
    listOf("app_name")
) { "Open the app named \"app_name\" directly using package manager. This is a debug feature that bypasses the traditional UI navigation." }
```

## Behavior

When the `enableDirectAppOpening` flag is enabled:
- The `Open_App` action becomes available to the agent
- The agent can directly open apps using package manager
- No fallback to traditional UI navigation is performed

When the flag is disabled:
- The `Open_App` action is completely unavailable
- The agent must use traditional UI navigation (Tap, Swipe, etc.) to open apps

## Error Handling

When the Open_App action is used:
- Missing app name: Prints "Missing app_name for Open_App action"
- Package name not found: Prints "Could not find package name for app"
- Launch failure: Prints "Failed to open app"

When the Open_App action is disabled:
- Action not available: The action is not included in the available actions list
- If somehow called: Prints "Open_App action is disabled"

## Security Considerations

- The `QUERY_ALL_PACKAGES` permission allows the app to see all installed packages
- This permission is restricted by Google Play Store for privacy reasons
- Only enable this feature for debugging and development builds
- Always set `ENABLE_DIRECT_APP_OPENING` to `false` for production releases

## Testing

To test the feature:

1. Set `ENABLE_DIRECT_APP_OPENING` to `true` in debug build
2. Give the agent an instruction like "Open Chrome"
3. The agent should use the `Open_App` action instead of searching for the Chrome icon

## Troubleshooting

- **Permission Denied**: Ensure `QUERY_ALL_PACKAGES` permission is granted
- **App Not Found**: Check if the app name matches exactly or try partial matches
- **Launch Failed**: Verify the app has a launch intent and is properly installed
- **Feature Not Working**: Check that `enableDirectAppOpening` is set to `true` in the configuration 