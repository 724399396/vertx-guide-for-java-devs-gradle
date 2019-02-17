import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar


plugins {
  java
  application
  id("com.github.johnrengelman.shadow") version "4.0.3"
}

val vertxVersion = "3.6.3"
val junitVersion = "5.3.2"

dependencies {
  implementation("io.vertx:vertx-core:$vertxVersion")

  implementation("io.vertx:vertx-web:$vertxVersion")
  implementation("io.vertx:vertx-web-templ-freemarker:$vertxVersion")
  implementation("com.github.rjeschke:txtmark:0.13")

  implementation("io.vertx:vertx-jdbc-client:$vertxVersion")
  implementation("org.hsqldb:hsqldb:2.3.4")

  implementation("ch.qos.logback:logback-classic:1.2.3")

  implementation("io.vertx:vertx-service-proxy:$vertxVersion")
  compileOnly("io.vertx:vertx-codegen:$vertxVersion")
  annotationProcessor("io.vertx:vertx-codegen:$vertxVersion:processor")

  implementation("io.vertx:vertx-web-client:$vertxVersion")

  implementation("io.vertx:vertx-auth-jdbc:$vertxVersion")
  implementation("io.vertx:vertx-auth-jwt:$vertxVersion")

  testImplementation("io.vertx:vertx-junit5:$vertxVersion")
  testImplementation("io.vertx:vertx-web-client:$vertxVersion")
  testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")
}

java {
  sourceCompatibility = JavaVersion.VERSION_1_8
}

application {
  mainClassName = "io.vertx.core.Launcher"
}

val mainVerticleName = "io.vertx.wiki.MainVerticle"
val watchForChange = "src/**/*.java"
val doOnChange = "${projectDir}/gradlew classes"

logger.info(configurations.toString())

tasks {
  compileJava {
    targetCompatibility = "1.8"
    sourceCompatibility = "1.8"

    dependsOn("codeGen")
  }
  
  register("codeGen", JavaCompile::class) {
    group = "build"
    description = "generate vertx service code"
    source = sourceSets.getByName("main").java
    classpath = configurations.getByName("compileClasspath")
    destinationDir = project.file("src/main/generated")
    options.annotationProcessorGeneratedSourcesDirectory = project.file("src/main/generated")
    options.annotationProcessorPath = configurations.getByName("compileClasspath")
    options.compilerArgs = listOf(
      "-proc:only",
      "-processor", "io.vertx.codegen.CodeGenProcessor",
      "-Acodegen.output=${project.projectDir}/src/main"
    )
  }

  test {
    useJUnitPlatform()
  }

  clean {
    delete("src/main/generated")
  }

  getByName<JavaExec>("run") {
    args = listOf("run", mainVerticleName, "--redeploy=${watchForChange}", "--launcher-class=${application.mainClassName}", "--on-redeploy=${doOnChange}")
  }

  withType<ShadowJar> {
    classifier = "fat"
    manifest {
      attributes["Main-Verticle"] = mainVerticleName
    }
    mergeServiceFiles {
      include("META-INF/services/io.vertx.core.spi.VerticleFactory")
    }
  }
}

sourceSets {
  getByName("main").java.srcDirs("src/main/generated")
}

