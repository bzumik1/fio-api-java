plugins {
	`java-library`
	jacoco
}

group = "io.github.bzumik1"
version = "1.0.0-SNAPSHOT"
description = "Java client for the Fio banka REST API"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
	withSourcesJar()
	withJavadocJar()
}

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
	finalizedBy(tasks.jacocoTestReport)
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
	dependsOn(tasks.jacocoTestCoverageVerification)
}
