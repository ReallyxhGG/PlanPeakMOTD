plugins {
    base
}

allprojects {
    group = "cn.xhgg.planpeakmotd"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://repo.playeranalytics.net/releases")
    }
}

subprojects {
    apply(plugin = "java")

    providers.gradleProperty("externalBuildRoot").orNull?.let { externalRoot ->
        layout.buildDirectory.set(file("$externalRoot/${project.name}"))
    }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = 21
        options.compilerArgs.add("-Xlint:deprecation")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
