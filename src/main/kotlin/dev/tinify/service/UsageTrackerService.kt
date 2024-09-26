package dev.tinify.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.tinify.Services
import dev.tinify.TinifyApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.system.ApplicationHome
import org.springframework.stereotype.Component
import java.io.File
import java.nio.file.Paths

const val SERVICE_USAGE_JSON = "service_usage.json"

@Component
class UsageTrackerService {
    private val logger: Logger = LoggerFactory.getLogger(UsageTrackerService::class.java)
    private lateinit var file: File
    private var usageData: MutableMap<String, Int> = mutableMapOf()

    init {
        file = getServiceUsageFilePath() // Get the path to store the JSON file
        loadUsageData()
    }

    // Determine the path to the service_usage.json file
    private fun getServiceUsageFilePath(): File {
        // Use ApplicationHome to determine the base path dynamically
        val home = ApplicationHome(TinifyApplication::class.java)
        val basePath = home.dir.absolutePath

        // Go one level up from the application's directory and into the "data" folder
        val parentPath = Paths.get(basePath).parent.toString()
        val dataDirectory = Paths.get(parentPath, "data").toFile()

        // Ensure the "data" directory exists
        if (!dataDirectory.exists()) {
            val created = dataDirectory.mkdirs()
            if (created) {
                logger.info("Data directory created at: ${dataDirectory.absolutePath}")
            } else {
                logger.error("Failed to create data directory at: ${dataDirectory.absolutePath}")
            }
        }

        // Return the file path for service_usage.json
        return File(dataDirectory, SERVICE_USAGE_JSON)
    }

    // Load usage data from file or initialize with zeros if file doesn't exist
    private fun loadUsageData() {
        if (file.exists()) {
            try {
                val mapper = jacksonObjectMapper()
                usageData = mapper.readValue(file)
                logger.info("Loaded service usage data: $usageData")
            } catch (e: Exception) {
                logger.error("Failed to read usage data, initializing to zero", e)
                initializeUsageData()
            }
        } else {
            initializeUsageData()
        }
    }

    // Initialize usage data for all services to zero
    private fun initializeUsageData() {
        usageData["resize"] = 0
        usageData["compress"] = 0
        usageData["crop"] = 0
        saveUsageData()
    }

    // Save the usage data to file
    private fun saveUsageData() {
        try {
            val mapper = jacksonObjectMapper()
            mapper.writeValue(file, usageData)
            logger.info("Service usage data saved: $usageData")
        } catch (e: Exception) {
            logger.error("Failed to save usage data", e)
        }
    }

    @Synchronized
    fun incrementServiceCount(serviceName: Services) {
        logger.info("Incrementing service count for: $serviceName")

        try {
            val serviceNameString = serviceName.toString()
            usageData[serviceNameString] = usageData.getOrDefault(serviceNameString, 0) + 1
            saveUsageData()
        } catch (e: Exception) {
            logger.error("Failed to increment service count for: $serviceName", e)
        }
    }

    @Synchronized
    fun getServiceCount(serviceName: Services): Int {
        val serviceNameString = serviceName.toString()
        return usageData.getOrDefault(serviceNameString, 0)
    }

    @Synchronized
    fun getAllUsageData(): Map<String, Int> {
        loadUsageData()
        return usageData
    }
}
