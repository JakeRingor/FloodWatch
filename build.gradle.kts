// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    kotlin("plugin.serialization") version "2.0.21" apply false
    // Applied by :app only when app/google-services.json is present. Keeping it
    // conditional lets local builds work before Firebase has been configured.
    id("com.google.gms.google-services") version "4.4.4" apply false
}
