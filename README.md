# OpeNLogin

A practical, secure and friendly authentication plugin

### Features

SQLite or MySQL/MariaDB storage, remember-me sessions, brute-force
protection, and in-game admin tooling (force-login, force-unregister,
force-changepassword). See [docs/features.md](docs/features.md) for
configuration and usage, and [docs/lang.md](docs/lang.md) for the available
translations.

### For development:

#### Gradle:
```
repositories {
    maven { 
        url = uri('https://repo.nickuc.com/maven-releases/') 
    }
}

dependencies {
    compileOnly('com.nickuc.openlogin:openlogin-universal:1.3')
}
```

#### Maven:
```xml
<repositories>
  <repository>
    <id>nickuc-repo</id>
    <url>https://repo.nickuc.com/maven-releases/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.nickuc.openlogin</groupId>
    <artifactId>openlogin-universal</artifactId>
    <version>1.3</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```
