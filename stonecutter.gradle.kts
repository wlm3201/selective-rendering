plugins {
	id("dev.kikugie.stonecutter")
}

stonecutter active "26.3"

val collectedDir = layout.buildDirectory.dir("libs")
val modVersion = property("version") as String
val versionProjects = subprojects.toList()

val cleanCollected = tasks.register<Delete>("cleanCollected") {
	delete(collectedDir)
}

tasks.register<Copy>("buildAndCollect") {
	group = "build"
	description = "Builds every version and collects the jars into build/libs"

	versionProjects.forEach { dependsOn("${it.path}:build") }
	dependsOn(cleanCollected)

	from(versionProjects.map { it.layout.buildDirectory.dir("libs") }) {
		include("*-$modVersion.jar")
	}
	into(collectedDir)
}
