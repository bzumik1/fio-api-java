plugins {
	`java-library`
	jacoco
	alias(libs.plugins.mavenPublish)
}

group = "io.github.bzumik1"
description = "Java client for the Fio banka REST API"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

val testJdk = providers.gradleProperty("testJdk").map(JavaLanguageVersion::of).orElse(JavaLanguageVersion.of(17))

repositories {
	mavenCentral()
}

dependencies {
	api(libs.jspecify)
	implementation(libs.jackson.databind)

	testImplementation(platform(libs.junit.bom))
	testImplementation(libs.junit.jupiter)
	testImplementation(libs.assertj)
	testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.compilerArgs.addAll(listOf("-Xlint:all,-module", "-Werror"))
}

tasks.withType<AbstractArchiveTask>().configureEach {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
}

tasks.withType<org.gradle.jvm.tasks.Jar>().configureEach {
	from(layout.projectDirectory.file("LICENSE")) {
		into("META-INF")
	}
}

tasks.jar {
	manifest {
		attributes(
			"Implementation-Title" to project.name,
			"Implementation-Version" to project.version,
			"Implementation-Vendor" to "Jakub Znamenáček",
			"Implementation-URL" to "https://github.com/bzumik1/fio-api-java"
		)
	}
}

tasks.javadoc {
	inputs.dir(layout.projectDirectory.dir("gradle/javadoc/jdk17")).withPathSensitivity(PathSensitivity.RELATIVE)
	options.encoding = "UTF-8"
	(options as StandardJavadocDocletOptions).apply {
		addBooleanOption("Xdoclint:all", true)
		addBooleanOption("Werror", true)
		linksOffline(
			"https://docs.oracle.com/en/java/javase/17/docs/api/",
			layout.projectDirectory.dir("gradle/javadoc/jdk17").asFile.path
		)
	}
}

tasks.test {
	useJUnitPlatform()
	javaLauncher = javaToolchains.launcherFor {
		languageVersion = testJdk
	}
	environment("FIO_API_TEST_TOKEN", "token-set-by-the-build-for-tests")
	maxHeapSize = "1g"
	testLogging {
		events("failed", "skipped")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		showStackTraces = true
	}
	finalizedBy(tasks.jacocoTestReport)
}

val moduleSmoke by sourceSets.creating

configurations.named(moduleSmoke.implementationConfigurationName) {
	extendsFrom(configurations.named("api").get(), configurations.named("implementation").get())
}

dependencies {
	moduleSmoke.implementationConfigurationName(files(tasks.jar))
}

val moduleSmokeTest by tasks.registering(JavaExec::class) {
	group = "verification"
	description = "Runs a consumer of the built jar on the module path"
	classpath = moduleSmoke.runtimeClasspath
	mainModule = "io.github.bzumik1.fio.smoke"
	mainClass = "io.github.bzumik1.fio.smoke.ModuleSmoke"
	val statement = layout.projectDirectory.file("src/test/resources/io/github/bzumik1/fio/periods-statement.json")
	inputs.file(statement)
	args(statement.asFile.path)
	javaLauncher = javaToolchains.launcherFor {
		languageVersion = testJdk
	}
}

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	reports {
		xml.required = true
		html.required = true
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)
	violationRules {
		rule {
			limit {
				counter = "LINE"
				minimum = "1.00".toBigDecimal()
			}
			limit {
				counter = "BRANCH"
				minimum = "1.00".toBigDecimal()
			}
		}
	}
}

tasks.assemble {
	dependsOn(tasks.named("plainJavadocJar"))
}

tasks.check {
	dependsOn(tasks.jacocoTestCoverageVerification, moduleSmokeTest)
}

mavenPublishing {
	publishToMavenCentral(automaticRelease = true)
	signAllPublications()
	coordinates(group.toString(), "fio-api", version.toString())
	pom {
		name = "fio-api"
		description = project.description
		url = "https://github.com/bzumik1/fio-api-java"
		inceptionYear = "2026"
		licenses {
			license {
				name = "MIT License"
				url = "https://opensource.org/license/mit"
				distribution = "repo"
			}
		}
		developers {
			developer {
				id = "bzumik1"
				name = "Jakub Znamenáček"
				url = "https://github.com/bzumik1"
			}
		}
		scm {
			url = "https://github.com/bzumik1/fio-api-java"
			connection = "scm:git:https://github.com/bzumik1/fio-api-java.git"
			developerConnection = "scm:git:ssh://git@github.com/bzumik1/fio-api-java.git"
		}
		issueManagement {
			system = "GitHub"
			url = "https://github.com/bzumik1/fio-api-java/issues"
		}
	}
}
