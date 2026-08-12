// Versions are pinned to what is already in the local Gradle cache so the first
// build does not depend on resolving a plugin from the network.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.24" apply false
}
