# Porcupine Wake Word Setup

This app now supports two wake word detection engines:

1. **STT Engine (Default)**: Uses Android's Speech-to-Text to listen for "Panda"
2. **Porcupine Engine**: Uses Picovoice's Porcupine library to listen for "Panda" (custom wake word)

## Setting up Porcupine Wake Word Detection

### 1. Get a Picovoice Access Key

1. Go to [Picovoice Console](https://console.picovoice.ai/)
2. Sign up or log in to your account
3. Navigate to the Access Keys section
4. Create a new access key or copy an existing one

### 2. Configure the Access Key

Add your Picovoice access key to the `local.properties` file in your project root:

```properties
# Add this line to your local.properties file
PICOVOICE_ACCESS_KEY=your_actual_access_key_here
```

### 3. Using the Wake Word Feature

1. Open the app
2. Select your preferred wake word engine:
   - **STT Engine**: Listens for "Panda" (works offline)
   - **Porcupine Engine**: Listens for "Panda" (more accurate, requires internet)
3. Tap "ENABLE WAKE WORD" to start listening
4. Say the wake word to activate the app

### 4. Custom Wake Words

The app is already configured to use a custom "Panda" wake word. The `Panda_en_android_v3_0_0.ppn` file is included in the assets folder.

To use different custom wake words:

1. Create a custom wake word in the [Picovoice Console](https://console.picovoice.ai/)
2. Download the `.ppn` file
3. Place it in `app/src/main/assets/`
4. Update the `PorcupineWakeWordDetector.kt` file to use the custom keyword path

Example:
```kotlin
porcupineManager = PorcupineManager.Builder()
    .setAccessKey(ACCESS_KEY)
    .setKeywordPaths(arrayOf("your_custom_wake_word.ppn"))
    .build(context, wakeWordCallback)
```

### 5. Troubleshooting

- **"Porcupine Access Key required"**: Make sure you've added the access key to `local.properties`
- **Wake word not detected**: Check your microphone permissions
- **Service not starting**: Ensure you have the required permissions (RECORD_AUDIO, INTERNET)

#### App Crashes When Starting Wake Word Service

If the app crashes when you try to enable the wake word service:

1. **Check Access Key Configuration:**
   - Ensure `local.properties` contains a valid Picovoice access key
   - The key should not be empty or contain placeholder text
   - Rebuild the project after adding the key

2. **Check Logs:**
   - Look for error messages containing "Porcupine" or "ACCESS_KEY"
   - The app will automatically fall back to STT-based detection if Porcupine fails

3. **Fallback Behavior:**
   - If Porcupine fails to initialize, the app will automatically use STT-based wake word detection
   - This ensures the wake word feature still works even without a valid Picovoice key

4. **Manual Fallback:**
   - If you don't have a Picovoice access key, select "STT Engine" in the UI
   - This will use the built-in Android speech recognition for wake word detection

### 6. Built-in Wake Words

Porcupine supports several built-in wake words. You can change the wake word by modifying the `PorcupineWakeWordDetector.kt` file:

```kotlin
// Available built-in keywords:
// - Porcupine.BuiltInKeyword.PORCUPINE
// - Porcupine.BuiltInKeyword.BUMBLEBEE
// - Porcupine.BuiltInKeyword.PICOVOICE
// - And many more...

.setKeywords(arrayOf(Porcupine.BuiltInKeyword.BUMBLEBEE))
```

## Security Note

Keep your Picovoice access key secure and never commit it to version control. The `local.properties` file is already in `.gitignore` to prevent accidental commits. 