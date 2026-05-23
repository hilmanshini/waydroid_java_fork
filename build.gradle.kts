plugins {
    kotlin("jvm") version "2.3.0"
}

group = "waydroid.java_port.hilman"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://github.com/jitsi/jitsi-maven-repository/raw/master/releases")
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    implementation("net.java.dev.jna:jna:5.14.0")

    implementation("com.github.hypfvieh:dbus-java-core:5.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.json:json:20251224")
    implementation("com.github.hypfvieh:dbus-java-transport-native-unixsocket:5.2.0")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnitPlatform()
}