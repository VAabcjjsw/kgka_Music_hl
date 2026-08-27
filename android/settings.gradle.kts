pluginManagement {
    val flutterSdkPath =
        run {
            val properties = java.util.Properties()
            file("local.properties").inputStream().use { properties.load(it) }
            val flutterSdkPath = properties.getProperty("flutter.sdk")
            require(flutterSdkPath != null) { "flutter.sdk not set in local.properties" }
            flutterSdkPath
        }

    includeBuild("$flutterSdkPath/packages/flutter_tools/gradle")

    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // ⚠️ 注意：不能使用 FAIL_ON_PROJECT_REPOS —— Flutter 官方 Gradle 插件
    // `dev.flutter.flutter-gradle-plugin` 在 apply 时会动态向项目的
    // repositories 注入一个 maven 仓库（用于拉取 flutter_embedding 等），
    // 在 FAIL_ON_PROJECT_REPOS 模式下会直接报错退出：
    //   "repository 'maven' was added by plugin 'dev.flutter.flutter-gradle-plugin'"
    // 使用 PREFER_SETTINGS 既能保证 settings 中声明的仓库优先匹配，
    // 又允许 Flutter 插件按需注入额外仓库。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    // Android Gradle Plugin 与 Kotlin 版本说明：
    // AGP 8.7.x + Kotlin 2.0.x 是 Flutter stable 频道（subosito/flutter-action@v2）
    // 广泛验证、兼容性最佳的组合。之前用的 AGP 8.11.1 / Kotlin 2.2.20 过于超前，
    // 会触发 Flutter 内部 "Starting AGP 9+, only the new DSL..." 告警并在部分
    // 版本组合下出现插件 apply 失败。
    id("com.android.application") version "8.7.4" apply false
    id("org.jetbrains.kotlin.android") version "2.0.20" apply false
}

include(":app")
