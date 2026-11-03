plugins {
    id("pafm.kmp-library")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
}

sqldelight {
    databases {
        create("PaperclipDatabase") {
            packageName.set("com.stainedhead.paperclipmobile.infrastructure.persistence.db")
            // Every released schema version is committed as <version>.db; the build checks that the .sq files and the
            // .sqm migrations still produce the same schema as those snapshots.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            verifyMigrations.set(true)
        }
    }
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(project(":shared:domain"))
            implementation(libs.sqldelight.runtime)
            implementation(libs.sqldelight.coroutines)
            implementation(libs.serialization.json)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.driver.native)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.driver.android)
            implementation(libs.androidx.sqlite.framework)
        }
        jvmMain.dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }
        commonTest.dependencies {
            implementation(project(":shared:testing"))
        }
        // The Android host test runs on the JVM, where the Android SQLite classes are stubs: it uses the JDBC driver.
        getByName("androidHostTest").dependencies {
            implementation(libs.sqldelight.driver.sqlite)
        }
    }
}
