plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "dev	"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Required core module for TwelveMonkeys
    implementation("com.twelvemonkeys.imageio:imageio-core:3.11.0") // Core support

    // Optional specific format modules
    implementation("com.twelvemonkeys.imageio:imageio-tiff:3.11.0") // TIFF support
    //implementation("com.twelvemonkeys.imageio:imageio-webp:3.11.0") // WebP support
    //implementation("com.github.zakgof:webp4j:0.0.2")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
kotlin {
    jvmToolchain(jdkVersion = 21) // Ensure Java 21 compatibility
}
