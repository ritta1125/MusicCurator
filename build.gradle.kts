plugins {
    id("java")
    id("application")
}

group = "com.musicCurator"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // OkHttp for HTTP requests
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson for JSON parsing
    implementation("com.google.code.gson:gson:2.10.1")

    // Google API Client
    implementation("com.google.api-client:google-api-client:2.2.0")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.34.1")
    implementation("com.google.apis:google-api-services-youtube:v3-rev20231011-2.0.0")
    implementation("com.google.http-client:google-http-client-gson:1.43.3")

    // Logging
    implementation("org.slf4j:slf4j-simple:2.0.9")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
}

application {
    mainClass.set("com.musicCurator.Main")
}

tasks.test {
    useJUnit()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

// Task to create config from template
tasks.register("createConfig") {
    doLast {
        val template = file("config.properties.template")
        val config = file("src/main/resources/config.properties")
        if (!config.exists() && template.exists()) {
            template.copyTo(config)
            println("Created config.properties from template. Please update with your credentials.")
        }
    }
}

// Run createConfig before build
tasks.build {
    dependsOn("createConfig")
}
