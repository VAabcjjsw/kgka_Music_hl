// ===== Flutter / AGP / Kotlin 版本集中声明 =====
// 注意：以下两个版本号必须保持与下方 plugins {} block 中声明的版本一致。
// resolutionStrategy.eachPlugin 在某些路径下（例如 Flutter plugin-loader
// 或 includeBuild 合成的 apply 不带 version）拿不到 requested.version，
// 所以必须有硬编码兜底，而不是直接 error() 退出。
private const val AGP_VERSION = "8.6.1"
private const val KOTLIN_VERSION = "2.0.20"

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

    // 显式把 plugins {} block 中声明的 plugin id 映射到真实的 Maven artifact
    // 坐标，避免 Gradle 去 Google Maven 查找 plugin marker（例如
    // com.android.application.gradle.plugin:8.6.1.pom）。因为 marker 并非
    // 所有 AGP 版本都有上传，在 CI 环境经常出现 "plugin ... was not found"。
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "com.android.application",
                "com.android.library",
                "com.android.dynamic-feature",
                "com.android.test",
                "com.android.instantapp" -> {
                    // Flutter plugin-loader / includeBuild 在某些求值路径下（例如
                    // 根 build.gradle.kts 中 evaluationDependsOn(":app") 触发的早期
                    // 求值）会不带 version 信息地请求该 plugin；此时
                    // requested.version 为 null，因此必须用兜底常量而不是 error()。
                    val agpVersion = requested.version ?: AGP_VERSION
                    useModule("com.android.tools.build:gradle:$agpVersion")
                }
                "org.jetbrains.kotlin.android",
                "kotlin-android",
                "android",
                "org.jetbrains.kotlin.jvm",
                "kotlin",
                "jvm" -> {
                    val kotlinVersion = requested.version ?: KOTLIN_VERSION
                    useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinVersion")
                }
            }
        }
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
        // JitPack（主站 + 官方镜像，按顺序尝试）——用于 SuperLyricApi:3.4
        // 注意：JitPack 是首次请求时才在服务器端"懒构建"对应 GitHub tag/commit，
        // 第一次解析可能返回 404，等 60~120 秒后再请求即可拉到构建产物，
        // workflow 里对这种情况做了一次自动 sleep + refresh-dependencies 重试。
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://www.jitpack.io") }
    }
}

plugins {
    id("dev.flutter.flutter-plugin-loader") version "1.0.0"
    // Android Gradle Plugin 与 Kotlin 版本说明：
    // - AGP 8.6.x + Kotlin 2.0.x 是 Flutter stable（subosito/flutter-action@v2）
    //   与 Gradle 8.14（gradle-wrapper.properties 中版本）兼容性最佳的组合。
    // - 之前用 8.11.1 / 2.2.20 过于超前，触发 Flutter Fix "Starting AGP 9+..."。
    // - 之前降到 8.7.4 时在 CI 上遇到 plugin marker artifact 解析失败
    //   "could not resolve com.android.application.gradle.plugin:8.7.4"。
    //   现在通过 pluginManagement.resolutionStrategy 显式映射到
    //   com.android.tools.build:gradle:8.6.1，不再依赖 marker 发布情况。
    id("com.android.application") version AGP_VERSION apply false
    id("org.jetbrains.kotlin.android") version KOTLIN_VERSION apply false
}

include(":app")
