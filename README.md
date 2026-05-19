# Walle - Android Channel Package Builder

> **Project Origin**: This project is forked from [Meituan-Dianping Walle](https://github.com/Meituan-Dianping/walle), with modern upgrades and maintenance based on it.
> 
> **Original Project**: https://github.com/Meituan-Dianping/walle  
> **Technical Article**: [Meituan's New Generation Android Channel Package Tool](http://tech.meituan.com/2017/01/13/android-apk-v2-signature-scheme.html)

[![Release Version](https://img.shields.io/badge/release-2.0.13-blue.svg)](https://gitee.com/maxchou/walle/releases)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://raw.githubusercontent.com/Meituan-Dianping/walle/master/LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com/)

## 📖 Project Overview

Walle is a next-generation channel package builder for Android under the Android Signature V2 Scheme.

Walle adds custom channel information to the `APK Signature Block` section in APKs to generate channel packages, thereby improving the efficiency of channel package generation. It can be used as a standalone tool or deployed on an HTTP server to handle real-time channel package APK upgrade network requests.

## 🚀 Quick Start

We provide multiple usage methods:

* **Library Dependency Method** - Recommended, simple and easy to use
* **Gradle Plugin Method** - Convenient integration, automated packaging
* **Command Line Tool Method** - Flexible, supports custom requirements

### Method 1: Library Dependency (Recommended)

#### 1. Add JitPack Repository

Add to `settings.gradle` or `build.gradle` in the project root directory:

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

#### 2. Add Dependencies

Add to `build.gradle` in the App module:

```gradle
dependencies {
    implementation 'com.github.maxZhou7:walle:2.0.13'
}
```

#### 3. Get Channel Information

```java
import com.meituan.android.walle.WalleChannelReader;

// Get channel name
String channel = WalleChannelReader.getChannel(context);

// Get complete channel information
ChannelInfo channelInfo = WalleChannelReader.getChannelInfo(context);
if (channelInfo != null) {
    String channel = channelInfo.getChannel();
    Map<String, String> extraInfo = channelInfo.getExtraInfo();
}

// Get extra info by key
String buildTime = WalleChannelReader.get(context, "buildtime");
```

### Method 2: Gradle Plugin (Adapted for AGP 8.x)

#### 1. Configure Project-level build.gradle

Add to `build.gradle` file in the project root directory:

```gradle
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.maxZhou7:walle-plugin:2.0.13'
    }
}
```

#### 2. Configure App Module build.gradle

Apply plugin and add dependencies in the App module's `build.gradle`:

```gradle
apply plugin: 'walle'

dependencies {
    implementation 'com.github.maxZhou7:walle:2.0.13'
}
```

#### 3. Configure Plugin Parameters

```gradle
walle {
    // Specify output path for channel packages
    apkOutputFolder = new File("${project.buildDir}/outputs/channels")
    // Customize filename format for channel package APKs
    apkFileNameFormat = '${appName}-${packageName}-${channel}-${buildType}-v${versionName}-${versionCode}.apk'
    // Channel configuration file
    channelFile = new File("${project.getProjectDir()}/channel")
}
```

**Configuration Item Descriptions:**

- **apkOutputFolder**: Output path for channel packages, default is `${project.buildDir}/outputs/apk`
- **apkFileNameFormat**: Filename format for channel packages, supports variables:
  - `${projectName}` - Project name
  - `${appName}` - App module name
  - `${packageName}` - applicationId
  - `${buildType}` - buildType (release/debug)
  - `${channel}` - Channel name
  - `${versionName}` - Display version
  - `${versionCode}` - Internal version code
  - `${buildTime}` - Build time
  - `${fileSHA1}` - APK file SHA1
  - `${flavorName}` - productFlavors name
- **channelFile**: Path to channel configuration file, one channel per line, supports `#` comments

#### 4. Generate Channel Packages

```bash
# Generate all channel packages
./gradlew clean assembleReleaseChannels

# Generate channel packages for specific flavor
./gradlew clean assembleMeituanReleaseChannels

# Temporarily generate single channel
./gradlew clean assembleReleaseChannels -PchannelList=meituan

# Temporarily generate multiple channels
./gradlew clean assembleReleaseChannels -PchannelList=meituan,dianping

# Use temporary channel file
./gradlew clean assembleReleaseChannels -PchannelFile=/path/to/channel.txt
```

#### 5. Use configFile to Insert Extra Information

If you want to insert other information besides the channel, use `configFile`:

```gradle
walle {
    // Channel & extra info configuration file, mutually exclusive with channelFile
    configFile = new File("${project.getProjectDir()}/config.json")
}
```

`config.json` format example:

```json
{
  "channel": [
    {
      "channel": "meituan",
      "extra_info": {
        "buildtime": "20260101",
        "hash": "abc123"
      }
    },
    {
      "channel": "dianping",
      "extra_info": {
        "buildtime": "20260101"
      }
    }
  ]
}
```

Get extra information:

```java
ChannelInfo channelInfo = WalleChannelReader.getChannelInfo(context);
if (channelInfo != null) {
    String channel = channelInfo.getChannel();
    Map<String, String> extraInfo = channelInfo.getExtraInfo();
}

// Or get directly by key
String value = WalleChannelReader.get(context, "buildtime");
```

### Method 3: Command Line Tool

For detailed CLI usage instructions, please refer to: [Walle CLI Usage Instructions](walle-cli/README.md)

#### Basic Usage

```bash
# View APK channel information
java -jar walle-cli-all.jar show app-release.apk

# Write channel information
java -jar walle-cli-all.jar put -c meituan app-release.apk app-release-meituan.apk

# Batch write channels
java -jar walle-cli-all.jar batch -f channel.txt app-release.apk output/
```

## 📦 Generate Channel Packages

### Batch Generate Using CLI Tool

1. **Prepare channel configuration file** (`channel.txt`)
```
meituan
dianping
xiaomi
huawei
```

2. **Execute batch command**
```bash
java -jar walle-cli-all.jar batch -f channel.txt app-release.apk channels/
```

3. **Verify channel information**
```bash
java -jar walle-cli-all.jar show channels/app-release-meituan.apk
```

## 🔧 Advanced Usage

### Insert Extra Information

Besides channel information, you can also insert other custom information:

```bash
java -jar walle-cli-all.jar put \
  -c meituan \
  -e buildtime:20260101,hash:abc123 \
  app-release.apk \
  app-release-meituan.apk
```

### Read Extra Information

```java
// Get all extra info
Map<String, String> extraInfo = WalleChannelReader.getExtraInfo(context);

// Get specified extra info
String buildTime = WalleChannelReader.get(context, "buildtime");
```

## 📚 Module Descriptions

This project contains the following modules:

| Module | Maven Coordinates | Description |
|------|-----------|------|
| **library** | `com.github.maxZhou7:walle:2.0.13` | Android Library, provides channel information reading functionality |
| **plugin** | `com.github.maxZhou7:walle-plugin:2.0.13` | Gradle plugin (adapted for AGP 8.x) |
| **payload_reader** | `com.github.maxZhou7:payload_reader:2.0.13` | APK Signing Block reading module |
| **payload_writer** | `com.github.maxZhou7:payload_writer:2.0.13` | APK Signing Block writing module |
| **walle-cli** | - | Command line tool |
| app | - | Sample application |

### Reference Modules Individually

If you only need to use specific functionality, you can reference the corresponding modules individually.

#### 1. Use Reading Functionality Only

```gradle
dependencies {
    implementation 'com.github.maxZhou7:payload_reader:2.0.13'
}
```

Applicable scenarios: Only need to read channel information from APKs, no writing required.

#### 2. Use Writing Functionality Only

```gradle
dependencies {
    implementation 'com.github.maxZhou7:payload_writer:2.0.13'
}
```

Note: `payload_writer` will automatically depend on `payload_reader`.

Applicable scenarios: Need to write channel information to APKs on the server side or in tools.

#### 3. Complete Android Library

```gradle
dependencies {
    implementation 'com.github.maxZhou7:walle:2.0.13'
}
```

This is the most commonly used method, including reading functionality and automatically handling all dependencies.

Detailed documentation for each module:
- [Payload Reader Usage Instructions](payload_reader/README.md)
- [Payload Writer Usage Instructions](payload_writer/README.md)
- [Walle CLI Usage Instructions](walle-cli/README.md)

## ❓ FAQ

### 1. Why Choose Walle?

- **Fast Speed**: Based on APK Signature V2 Scheme, no need to re-sign or recompress
- **Good Compatibility**: Supports Android 7.0+ V2 signature scheme
- **High Flexibility**: Supports channel information and custom extra information

### 2. What Happens When Re-signing with apksigner?

Re-signing an APK with apksigner will cause channel information to be lost, requiring the channel information to be written again.

### 3. Compatibility with 360 Jiagu?

Please refer to: [360 Jiagu Incompatibility Issue](https://github.com/Meituan-Dianping/walle/wiki/360%E5%8A%A0%E5%9B%BA%E5%A4%B1%E6%95%88%EF%BC%9F)

### 4. Gradle Plugin Support

The Gradle plugin has been adapted for AGP 8.x and can be integrated using the following method:

```gradle
buildscript {
    dependencies {
        classpath 'com.github.maxZhou7:walle-plugin:2.0.13'
    }
}
```

For detailed usage instructions, please refer to "Method 2: Gradle Plugin" above.

## 🛠️ Build Project

If you want to build the project yourself:

```bash
# Clone the project
git clone https://gitee.com/maxchou/walle.git
cd walle

# Clean and build
./gradlew clean build

# Build CLI tool
./gradlew :walle-cli:shadowJar

# Publish to local Maven
./gradlew :payload_reader:publishToMavenLocal :library:publishToMavenLocal :plugin:publishToMavenLocal
```

## 🔄 Upgrade Notes

### Current Upgrade Content

This project has undergone comprehensive modernization upgrades based on the original Meituan Walle.

#### 1. **Build Tools Upgrade**
- **Gradle** upgraded to **8.13**
- **Android Gradle Plugin (AGP)** upgraded to **8.8.0**
- **Java version** upgraded to **Java 17**
- **Compile SDK** upgraded to **34**

#### 2. **Modern Dependency Management**
- Adopted **Version Catalog** (libs.versions.toml) for unified dependency version management
- Used modern **Plugin Management** configuration
- Used **Plugins DSL** instead of traditional buildscript approach

#### 3. **Code Standards Optimization**
- Replaced deprecated APIs (such as `buildDir` to `layout.buildDirectory`)
- Fixed `applicationIdSuffix` configuration issues
- Used `proguard-android-optimize.txt` instead of `proguard-android.txt`
- Updated all third-party dependencies to latest versions

#### 4. **Publishing Support**
- Supports publishing and distribution via **JitPack**
- Configured complete **Maven Publish** support
- Provides sources jar and javadoc jar

#### 5. **Module Optimization**
- Added Maven publishing support to `payload_reader` module
- Fixed multi-module dependency issues
- Optimized `jitpack.yml` build configuration

### Compatibility Notes

- **Minimum Support**: Android 5.0 (API 21)
- **Target Version**: Android 14 (API 34)
- **Java Version**: Java 17
- **Gradle Version**: 8.13+
- **AGP Version**: 8.8.0+

> ⚠️ **Note**: Due to API changes in AGP 8.x, the Gradle plugin functionality is temporarily disabled. It is recommended to use the CLI tool or Library dependency method.


## 🤝 Contribution Guidelines

This project welcomes contributions in any form:

- 🐛 Submit bug reports
- 💡 Propose new feature suggestions
- 📝 Improve documentation
- 🔧 Submit code fixes

## 📄 License

```
Copyright 2017 Meituan-Dianping
Copyright 2026 maxchou (forked version)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

## 🙏 Acknowledgments

- **Original Project**: [Meituan-Dianping Walle](https://github.com/Meituan-Dianping/walle)
- **Technical Support**: [Meituan Technical Team](http://tech.meituan.com/)
- **Reference Documents**: 
  - [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)
  - [Zip Format](https://en.wikipedia.org/wiki/Zip_(file_format))

---

**Current Maintainer**: [@maxchou](https://gitee.com/maxchou)  
**Project Address**: https://gitee.com/maxchou/walle  
**Original Project**: https://github.com/Meituan-Dianping/walle
