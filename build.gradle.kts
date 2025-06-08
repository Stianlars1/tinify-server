plugins {
    kotlin("jvm") version "2.0.20"
    kotlin("plugin.spring") version "2.0.20"
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.6"
}

group = "dev	"

version = "0.0.1-SNAPSHOT"

java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories { mavenCentral() }

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Required core module for TwelveMonkeys
    // implementation("com.twelvemonkeys.imageio:imageio-webp:3.11.0") // WebP support
    // implementation("com.github.zakgof:webp4j:0.0.2")
    implementation("org.sejda.imageio:webp-imageio:0.1.6")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")


    implementation("org.bytedeco:javacpp:1.5.9")
    implementation("org.bytedeco:javacv:1.5.9")
    implementation("org.bytedeco:opencv:4.7.0-1.5.9")
    implementation("com.twelvemonkeys.imageio:imageio-core:3.10.1")
    implementation("com.twelvemonkeys.imageio:imageio-jpeg:3.10.1")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.10.1")
    implementation("com.twelvemonkeys.imageio:imageio-tiff:3.11.0") // TIFF support

    // For direct PNG quantization
    implementation("ar.com.hjg:pngj:2.1.0")
}

kotlin {
    jvmToolchain(jdkVersion = 21) // Ensure Java 21 compatibility
}

tasks.withType<JavaExec> {
    jvmArgs =
        listOf(
            "-Xms2048m", // Initial heap size (2GB)
            "-Xmx4096m", // Max heap size (4GB)
            "-XX:+UseG1GC", // Use G1 garbage collector
            "-XX:MaxGCPauseMillis=200", // GC pause time goal
            "-XX:+ParallelRefProcEnabled", // Parallel reference processing
            "-XX:+UnlockExperimentalVMOptions", // Enable experimental VM optimizations
            "-XX:+UseNUMA", // Enable NUMA (useful on multi-core CPUs)
            "-XX:+UseLargePages", // Enable large memory pages
        )
}
