import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.springframework.boot") version "2.3.5.RELEASE"
  id("io.spring.dependency-management") version "1.0.10.RELEASE"
  kotlin("jvm") version "1.4.10"
  kotlin("plugin.spring") version "1.4.10"
}

group = "com.github.leomillon"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
  mavenCentral()
  jcenter()
  maven("https://jitpack.io")
}

dependencies {
  implementation("org.springframework.boot:spring-boot-starter-webflux")
  implementation("org.springframework.boot:spring-boot-starter-validation")
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
  implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

  implementation("org.apache.pdfbox:pdfbox:2.0.21")
  implementation("com.github.kenglxn.qrgen:javase:2.6.0")

  testImplementation("org.springframework.boot:spring-boot-starter-test") {
    exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
  }
  testImplementation("io.projectreactor:reactor-test")
}

tasks.withType<Test> {
  useJUnitPlatform()
  jvmArgs(
    "-Djunit.jupiter.testinstance.lifecycle.default=per_class",
    "-Duser.language=en"
  )
}

tasks.withType<KotlinCompile> {
  kotlinOptions {
    freeCompilerArgs = listOf("-Xjsr305=strict")
    jvmTarget = "11"
  }
}
