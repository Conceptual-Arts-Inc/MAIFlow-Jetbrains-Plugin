import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.testing.Test

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.changelog")
    id("org.jetbrains.intellij.platform")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions.jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
}

// IntelliJ's PathClassLoader sets java.system.class.loader, which makes the
// JVM's default CDS archive unusable for the test worker. Disable CDS so the
// test output stays clean without changing IntelliJ's classloading behavior.
tasks.withType<Test>().configureEach {
    jvmArgs("-Xshare:off")
}

tasks.withType<JavaExec>().configureEach {
    // IntelliJ's PathClassLoader makes the default CDS archive unusable.
    // This also covers buildSearchableOptions, which is a separate JavaExec task.
    jvmArgs("-Xshare:off")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        // 2025.3.5 can fail during Java plugin startup while evaluating accepted
        // language levels. 2025.3.6 contains the patched 253 platform runtime.
        intellijIdea("2025.3.6")
        testFramework(TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here, for example:
        // bundledPlugin("com.intellij.java")
    }
}
