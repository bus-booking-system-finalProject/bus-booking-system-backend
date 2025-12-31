plugins {
	java
	id("org.springframework.boot")
	id("io.spring.dependency-management")
}

group = "com.booking"
version = "0.0.1-SNAPSHOT"
description = "Bus Booking System User Service"

repositories {
	mavenCentral()
}

dependencies {
	developmentOnly("org.springframework.boot:spring-boot-devtools")
    implementation("org.springframework.cloud:spring-cloud-starter-config")
    implementation("org.springframework.retry:spring-retry")
	implementation("org.springframework.boot:spring-boot-starter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("org.springframework.boot:spring-boot-starter-web") // For Spring Web (Controllers, REST)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa") // For JPA (Entities, Repositories)
    implementation("org.springframework.boot:spring-boot-starter-validation") // For @Valid validation on DTOs
    implementation("org.springframework.boot:spring-boot-starter-security") // For Password Hashing (BCrypt)

    implementation("org.springframework.boot:spring-boot-starter-mail")
    
    implementation("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client")
    runtimeOnly("org.postgresql:postgresql") // PostgreSQL Driver
    compileOnly("org.projectlombok:lombok") // (Optional) Lombok for less boilerplate code (getters/setters)
    annotationProcessor("org.projectlombok:lombok")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // --- JWT Dependencies ---
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.3")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // --- Google Oauth 2.0 ---
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:1.19.8"))
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
}