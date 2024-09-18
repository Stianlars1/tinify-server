package dev.tinify.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
@CrossOrigin("*")
class TestController {
    @GetMapping
    fun test(): ResponseEntity<String> {
        return  ResponseEntity.ok().body("Hello World")
    }
}