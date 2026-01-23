plugins {
    // Detekt plugin incompatible with AGP 8.13 - using CLI in CI instead
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
