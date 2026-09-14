plugins {
    java
}

dependencies {
    compileOnly("dev.folia:folia-api:26.1.2.build.8-stable")
    compileOnly("com.djrapitops:plan-api:5.7-R0.2")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 25
}

tasks.processResources {
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    archiveBaseName = "PlanPeakBridge-Folia"
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}
