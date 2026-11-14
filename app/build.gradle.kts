import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// release 서명 정보는 저장소에 포함되지 않는 keystore.properties에서 읽는다.
// 파일이 없으면 release 빌드는 서명 없이(unsigned) 생성된다.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "com.carin.noraebang"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.carin.noraebang"
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // packageScope 재배치 최적화는 Pleos 에뮬레이터(Android 14)에서
            // kotlin.collections 패키지-private 클래스 접근 오류(IllegalAccessError)를 일으켜 제거함
            optimization {
                enable = true
            }
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    // JVM 단위 테스트 전용: android.jar의 org.json은 빈 스텁이라 MemoryCodecTest에 실제 구현이 필요하다 (APK 미포함)
    testImplementation("org.json:json:20240303")
    // Media3 - 음악 파일을 재생하는 플레이어
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    // Media3 - 차량 시스템과 재생 상태 및 명령을 연동
    implementation("androidx.media3:media3-session:1.11.1")
    // NaviHelper 실제 위치·목적지 데이터 테스트용
    implementation("ai.pleos.playground:NaviHelper:3.0.5")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}