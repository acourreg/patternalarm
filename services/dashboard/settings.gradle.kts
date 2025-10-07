rootProject.name = "patternalarm-dashboard"

// Enable Gradle build cache for faster builds
buildCache {
    local {
        isEnabled = true
        directory = File(rootDir, ".gradle/build-cache")
    }
}