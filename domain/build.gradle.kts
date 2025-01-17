plugins {
    id("org.jetbrains.kotlin.jvm")
    id("com.commercehub.gradle.plugin.avro")
    jacoco
}

dependencies {
    compile(kotlin("stdlib-jdk8"))
    compile("org.apache.kafka:kafka-streams:2.0.1")

    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:${property("jacksonKotlinVersion")}")
    implementation("org.apache.avro:avro:1.9.1")

}
