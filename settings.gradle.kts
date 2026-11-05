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
        // COUI 17 本地构件仓：稳定副本优先，其次 ../coui 的构建产物目录。
        // 目录不存在时 Gradle 视作空仓库，不影响远端解析。
        listOf(
            "coui-local-repo",
            "coui/build/repository/local",
        ).forEach { path ->
            maven { url = uri(rootDir.parentFile.resolve(path)) }
        }
    }
}

rootProject.name = "BiliCO"
include(":app")
include(":data")
