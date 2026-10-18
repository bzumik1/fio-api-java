plugins {
	`java-library`
	jacoco
	id("com.vanniktech.maven.publish") version "0.37.0"
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
	api("org.jspecify:jspecify:1.0.0")
	implementation("tools.jackson.core:jackson-databind:3.1.2")

	testImplementation(platform("org.junit:junit-bom:6.0.3"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testImplementation("org.assertj:assertj-core:3.27.7")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
	options.encoding = "UTF-8"
	options.compilerArgs.addAll(listOf("-Xlint:all,-module", "-Werror"))
}

tasks.withType<AbstractArchiveTask>().configureEach {
	isPreserveFileTimestamps = false
	isReproducibleFileOrder = true
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
	options.encoding = "UTF-8"
	(options as StandardJavadocDocletOptions).apply {
		addBooleanOption("Xdoclint:all", true)
		addBooleanOption("Werror", true)
		links("https://docs.oracle.com/en/java/javase/17/docs/api/")
	}
}

tasks.test {
	useJUnitPlatform()
	javaLauncher = javaToolchains.launcherFor {
		languageVersion = testJdk
	}
	environment("FIO_API_TEST_TOKEN", "token-set-by-the-build-for-tests")
	maxHeapSize = "512m"
	finalizedBy(tasks.jacocoTestReport)
}

val moduleSmoke by sourceSets.creating

configurations[moduleSmoke.implementationConfigurationName].extendsFrom(configurations.api.get(), configurations.implementation.get())

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
	}
}
