plugins {
	id("java")
	id("maven-publish")
	id("net.fabricmc.fabric-loom") version "1.18-SNAPSHOT"
}

// ==========================================================================================
// 这份脚本是"所有版本节点共用"的：Stonecutter 会对 settings.gradle.kts 里声明的每个版本
// 各跑一遍，stonecutter.current.version 就是当前节点的 MC 版本。
// ==========================================================================================

/** 每个 MC 版本各自不同的依赖坐标。新增版本时在这里加一行，再去 settings.gradle.kts 声明。 */
data class McDeps(
	val fabricApi: String,
	val fabricRendererApi: String,
	val malilib: String,
	val modmenu: String,
	val sodium: String,
	val minecraftDependency: String,
)

val versionDeps = mapOf(
	"26.1.2" to McDeps(
		fabricApi = "0.155.3+26.1.2",
		fabricRendererApi = "13.0.8+c80601bb4c",
		malilib = "0.28.12",
		modmenu = "18.0.2",
		sodium = "mc26.1.2-0.9.2-fabric",
		minecraftDependency = "~26.1",
	),
	"26.2" to McDeps(
		fabricApi = "0.161.0+26.2",
		fabricRendererApi = "14.1.4+2b0d8a229e",
		malilib = "0.29.6",
		modmenu = "20.0.3",
		sodium = "mc26.2-0.9.2-fabric",
		minecraftDependency = "~26.2",
	),
	"26.3" to McDeps(
		fabricApi = "0.161.0+26.3",
		fabricRendererApi = "17.0.15+79385d0b5d",
		malilib = "0.30.2",
		modmenu = "21.0.0",
		sodium = "mc26.3-0.9.2-fabric",
		minecraftDependency = "~26.3",
	),
)

val minecraftVersion: String = stonecutter.current.version
val deps: McDeps = requireNotNull(versionDeps[minecraftVersion]) {
	"build.gradle.kts 的 versionDeps 里没有 $minecraftVersion 的依赖坐标"
}

version = property("version") as String
group = property("group") as String

base {
	archivesName = "${rootProject.name}-mc$minecraftVersion"
}

loom {
	splitEnvironmentSourceSets()


	mods {
		register("selective-rendering") {
			sourceSet(sourceSets["main"])
			sourceSet(sourceSets["client"])
		}
	}
}

repositories {
	maven("https://maven.terraformersmc.com/") { name = "Terraformers" }
	maven("https://api.modrinth.com/maven") { name = "Modrinth" }
	mavenCentral()
}

dependencies {
	"minecraft"("com.mojang:minecraft:$minecraftVersion")
	implementation("net.fabricmc:fabric-loader:${property("loader_version")}")

	implementation("maven.modrinth:malilib:${deps.malilib}")

	val configScreen = project.hasProperty("configScreen")
	listOf(
		"net.fabricmc.fabric-api:fabric-api:${deps.fabricApi}",
		"com.terraformersmc:modmenu:${deps.modmenu}",
	).forEach {
		if (configScreen) implementation(it) else compileOnly(it)
	}

	compileOnly("net.fabricmc.fabric-api:fabric-renderer-api-v1:${deps.fabricRendererApi}")

	compileOnly("maven.modrinth:sodium:${deps.sodium}")
}

val malilibDependency = ">=${deps.malilib}"

tasks.processResources {
	val projectVersion = project.version.toString()
	val minecraftDependency = deps.minecraftDependency
	val malilibDependency = malilibDependency

	inputs.property("version", projectVersion)
	inputs.property("minecraft_dependency", minecraftDependency)
	inputs.property("malilib_dependency", malilibDependency)

	filesMatching("fabric.mod.json") {
		expand(
			"version" to projectVersion,
			"minecraft_dependency" to minecraftDependency,
			"malilib_dependency" to malilibDependency,
		)
	}
}

tasks.withType<JavaCompile>().configureEach {
	options.release = 25
	options.encoding = "UTF-8"
}

java {
	withSourcesJar()

	sourceCompatibility = JavaVersion.VERSION_25
	targetCompatibility = JavaVersion.VERSION_25
}

tasks.jar {
	val projectName = rootProject.name
	inputs.property("projectName", projectName)

	from(rootDir.resolve("LICENSE")) {
		rename { "${it}_$projectName" }
	}
}

publishing {
	publications {
		create<MavenPublication>("mavenJava") {
			from(components["java"])
		}
	}
}
