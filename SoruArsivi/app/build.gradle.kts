import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Sabit imza anahtari.
 *
 * Android bir uygulamanin ustune ancak **ayni anahtarla** imzalanmis bir
 * APK'yi kurdurur. Varsayilan debug anahtari her makinede ayri ayri
 * uretiliyor ve GitHub Actions her calismada sifirdan bir sanal makine
 * actigi icin her derleme farkli imzalaniyordu; sonuc olarak her yeni APK
 * "mevcut paketle cakisiyor" deyip kurulmuyor, uygulamayi silmek gerekiyor,
 * arsiv de siliniyordu.
 *
 * Ayrintilar ve kendi anahtarini uretme adimlari: keystore/signing.properties
 */
val signingProps = Properties().apply {
    val f = rootProject.file("keystore/signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigningKey = signingProps.getProperty("storeFile")
    ?.let { rootProject.file(it).exists() } == true

if (!hasSigningKey) {
    logger.warn(
        "UYARI: keystore/signing.properties bulunamadi. APK varsayilan debug " +
            "anahtariyla imzalanacak; telefondaki mevcut kurulumun uzerine kurulamaz."
    )
}

android {
    namespace = "com.emre.bilbakalim.arsiv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.emre.bilbakalim.arsiv"
        minSdk = 26
        targetSdk = 35
        versionCode = 12
        versionName = "2.1"
    }

    signingConfigs {
        if (hasSigningKey) {
            create("ortak") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    // Debug ve release ayni anahtarla imzalanir; boylece hangisini kurmus
    // olursan ol digerine gecebilirsin.
    val ortakImza = signingConfigs.findByName("ortak") ?: signingConfigs.getByName("debug")

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = ortakImza
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = ortakImza
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.compose)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons)
    debugImplementation(libs.androidx.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    // org.json Android çatısının parçası ve birim testlerde boş taklidi
    // hata fırlatıyor; testlerde gerçeğini kullanıyoruz.
    testImplementation(libs.json.jvm)
}
