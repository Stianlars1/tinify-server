package dev.tinify.controller

import dev.tinify.service.UsageTrackerService
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/usage")
class UsageController(private val usageTrackerService: UsageTrackerService) {
    private val logger = LoggerFactory.getLogger(UsageController::class.java)

    @GetMapping("/all")
    fun getAllUsage(): ResponseEntity<Map<String, Int>> {
        logger.debug("\n\n== USAGE == ")
        logger.debug("Incoming GET request on /usage/all")

        return ResponseEntity.ok(usageTrackerService.getAllUsageData())
    }
}
