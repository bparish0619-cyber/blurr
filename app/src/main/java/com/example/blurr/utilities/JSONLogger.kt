import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.io.File
import java.io.IOException
import java.time.Instant

/**
 * A utility for logging structured data to a file in JSON Lines format.
 * Each call to `log()` appends a new JSON object on a new line.
 * This class is thread-safe.
 *
 * @param filePath The path to the log file. The file will be created if it doesn't exist.
 */
class JSONLogger(filePath: String) {

    private val logFile = File(filePath)

    // A fairly permissive JSON configuration
    private val json = Json {
        prettyPrint = false // Set to true for readable, multi-line JSON in the file
        isLenient = true
        encodeDefaults = true
    }

    /**
     * Represents the structure of a single log entry.
     */
    @Serializable
    private data class LogEntry(
        val timestamp: String,
        val iteration: Long,
        val data: Map<String, String> // Using Map<String, String> for simplicity. See notes for other types.
    )

    init {
        // Ensure the parent directory exists
        logFile.parentFile?.mkdirs()
    }

    /**
     * Logs the iteration number and a map of data to the file.
     * This method is synchronized to prevent race conditions when called from multiple threads.
     *
     * @param iteration The current iteration number or count.
     * @param data A map containing the key-value data to log.
     */
    @Synchronized
    fun log(iteration: Long, data: Map<String, String>) {
        try {
            // Create the structured log entry
            val logEntry = LogEntry(
                timestamp = Instant.now().toString(),
                iteration = iteration,
                data = data
            )

            // Serialize the log entry object to a JSON string
            val jsonString = json.encodeToString(logEntry)

            // Append the JSON string as a new line to the file
            logFile.appendText("$jsonString\n")

        } catch (e: IOException) {
            System.err.println("Error writing to log file: ${e.message}")
            // Depending on your application's needs, you might want to handle this more gracefully
        }
    }
}