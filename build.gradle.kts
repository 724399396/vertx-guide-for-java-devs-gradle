allprojects {
  repositories {
    maven(url="http://maven.aliyun.com/nexus/content/groups/public/")
    mavenCentral()
  }
}

subprojects {
  version = "1.0"
}

