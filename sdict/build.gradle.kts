import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("com.android.library")
    kotlin("android")

    id("com.vanniktech.maven.publish") version "0.29.0"
}

android {
    namespace = "top.fumiama.sdict"
    compileSdk = 34

    defaultConfig {
        minSdk = 23

        consumerProguardFiles("consumer-rules.pro")
    }

    group = "top.fumiama"
    version = "0.1.1"

    mavenPublishing {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

        signAllPublications()

        coordinates(group.toString(), "sdict", version.toString())

        pom {
            name = "SimpleDict Library"
            description = "A simple protocal database[\"key\"]=\"value\" with tea encryption."
            inceptionYear = "2025"
            url = "https://github.com/fumiama/simple-dict-android"
            licenses {
                license {
                    name = "GNU General Public License v3.0"
                    url = "https://www.gnu.org/licenses/gpl-3.0.txt"
                    distribution = "https://www.gnu.org/licenses/gpl-3.0.txt"
                }
            }
            developers {
                developer {
                    id = "fumiama"
                    name = "源文雨"
                    url = "https://github.com/fumiama"
                }
            }
            scm {
                url = "https://github.com/fumiama/simple-dict-android"
                connection = "scm:git:git://github.com/fumiama/simple-dict-android.git"
                developerConnection = "scm:git:ssh://git@github.com/fumiama/simple-dict-android.git"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:1.8.22"))
}