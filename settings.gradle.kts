pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
		mavenCentral()
		gradlePluginPortal()
	}
}

plugins {
	id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
	create(rootProject) {
		versions("26.1.2", "26.2", "26.3")
		vcsVersion = "26.3"
	}
}

rootProject.name = "selective-rendering"
