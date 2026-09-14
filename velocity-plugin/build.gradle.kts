plugins {
    java
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.5.0-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.13.4")
    testImplementation("org.slf4j:slf4j-api:2.0.17")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName = "PlanPeakMOTD-Velocity"
    manifest {
        attributes("Implementation-Version" to project.version)
    }
}
