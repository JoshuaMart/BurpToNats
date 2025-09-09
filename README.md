# BurpToNats

This extension intercepts HTTP requests processed by Burp Suite and publishes them to a NATS messaging queue.

## Features

- **Real-time HTTP Request Streaming**: Automatically forwards all HTTP requests to NATS
- **Configurable NATS Connection**: Support for custom NATS server URLs via configuration files
- **Seamless Integration**: Non-intrusive - requests continue normal processing in Burp Suite
- **Error Handling**: Graceful handling of NATS connection failures with detailed logging

## Prerequisites

- **Java 21+**: Required for building and running the extension
- **Gradle**: Build automation tool (can be installed via gradle wrapper)

## Building the Extension

### 1. Initialize Gradle Wrapper (First Time Setup)

If you don't have Gradle installed system-wide, you can use the Gradle wrapper:

```bash
gradle wrapper
```

This creates the `gradlew` script that downloads and manages Gradle automatically.

### 2. Build the JAR

Use the Gradle wrapper to build the extension:

```bash
./gradlew build
```

Or on Windows:

```batch
gradlew.bat build
```

The compiled JAR file will be generated at:
```
build/libs/BurpToNats-1.0.0.jar
```

### 3. Clean Build (Optional)

To perform a clean build:

```bash
./gradlew clean build
```

## NATS Configuration

The extension supports configurable NATS server URLs through properties files. It searches for configuration in the following locations (in order):

1. `~/nats.properties`
2. `~/.burp/nats.properties`

### Configuration File Format

Create a `nats.properties` file with:

```properties
natsUrl=nats://your-nats-server:4222
```

If no configuration file is found, the extension defaults to `nats://localhost:4222`.
