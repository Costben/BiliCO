pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // COUI 17 本地构件仓：优先 ../coui 的最新构建产物，其次稳定副本兜底。
        // 目录不存在时 Gradle 视作空仓库，不影响远端解析。
        listOf(
            "coui/build/repository/local",
            "coui-local-repo",
        ).forEach { path ->
            maven { url = uri(rootDir.parentFile.resolve(path)) }
        }
    }
}

rootProject.name = "BiliCO"
include(":app")
include(":data")
