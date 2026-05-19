# Walle (瓦力) - Android 渠道包打包工�?
> **项目来源**: 本项�?fork �?[美团点评 Walle](https://github.com/Meituan-Dianping/walle)，在此基础上进行了现代化升级和维护�?> 
> **原始项目**: https://github.com/Meituan-Dianping/walle  
> **技术文�?*: [美团Android新一代渠道包生成工具](http://tech.meituan.com/2017/01/13/android-apk-v2-signature-scheme.html)

[![Release Version](https://img.shields.io/badge/release-2.0.14-blue.svg)](https://github.com/maxZhou7/meituanwall/releases)
[![JitPack](https://jitpack.io/v/maxZhou7/meituanwall.svg)](https://jitpack.io/#maxZhou7/meituanwall)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://raw.githubusercontent.com/Meituan-Dianping/walle/master/LICENSE)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com/)

## 📖 项目简�?
Walle（瓦力）�?Android Signature V2 Scheme 签名下的新一代渠道包打包神器�?
瓦力通过�?Apk 中的 `APK Signature Block` 区块添加自定义的渠道信息来生成渠道包，从而提高了渠道包生成效率，可以作为单机工具来使用，也可以部署在HTTP服务器上来实时处理渠道包 Apk 的升级网络请求�?
## 🚀 快速开�?
我们提供了多种使用方式：

* **Library 依赖方式** - 推荐，简单易�?* **Gradle 插件方式** - 集成方便，自动化打包
* **命令行工具方�?* - 灵活，支持自定义需�?
### 方式一：Library 依赖（推荐）

> 💡 **提示**: 首次使用 JitPack 依赖前，建议访问 [JitPack](https://jitpack.io/#maxZhou7/meituanwall) 手动触发 `v2.0.14` 版本的构建，以确保依赖可用�?
#### 1. 添加 JitPack 仓库

在项目根目录�?`settings.gradle` �?`build.gradle` 中添加：

```gradle
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

#### 2. 添加依赖

�?App 模块�?`build.gradle` 中添加：

```gradle
dependencies {
    implementation 'com.github.maxZhou7:walle:2.0.14'
}
```

#### 3. 获取渠道信息

```java
import com.meituan.android.walle.WalleChannelReader;

// 获取渠道名称
String channel = WalleChannelReader.getChannel(context);

// 获取完整渠道信息
ChannelInfo channelInfo = WalleChannelReader.getChannelInfo(context);
if (channelInfo != null) {
    String channel = channelInfo.getChannel();
    Map<String, String> extraInfo = channelInfo.getExtraInfo();
}

// 根据 key 获取额外信息
String buildTime = WalleChannelReader.get(context, "buildtime");
```

### 方式二：Gradle 插件（已适配 AGP 8.x�?
#### 1. 配置项目�?build.gradle

在项目根目录�?`build.gradle` 文件中添加：

```gradle
buildscript {
    repositories {
        google()
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.maxZhou7:walle-plugin:2.0.14'
    }
}
```

#### 2. 配置 App 模块 build.gradle

�?App 模块�?`build.gradle` 中应用插件并添加依赖�?
```gradle
apply plugin: 'walle'

dependencies {
    implementation 'com.github.maxZhou7:walle:2.0.14'
}
```

#### 3. 配置插件参数

```gradle
walle {
    // 指定渠道包的输出路径
    apkOutputFolder = new File("${project.buildDir}/outputs/channels")
    // 定制渠道包的APK的文件名�?    apkFileNameFormat = '${appName}-${packageName}-${channel}-${buildType}-v${versionName}-${versionCode}.apk'
    // 渠道配置文件
    channelFile = new File("${project.getProjectDir()}/channel")
}
```

**配置项说明：**

- **apkOutputFolder**: 渠道包输出路径，默认�?`${project.buildDir}/outputs/apk`
- **apkFileNameFormat**: 渠道包文件名格式，支持变量：
  - `${projectName}` - 项目名字
  - `${appName}` - App模块名字
  - `${packageName}` - applicationId
  - `${buildType}` - buildType (release/debug)
  - `${channel}` - 渠道名称
  - `${versionName}` - 显示版本�?  - `${versionCode}` - 内部版本�?  - `${buildTime}` - 编译时间
  - `${fileSHA1}` - APK文件SHA1
  - `${flavorName}` - productFlavors�?- **channelFile**: 渠道配置文件路径，每行一个渠道，支持 `#` 注释

#### 4. 生成渠道�?
```bash
# 生成所有渠道包
./gradlew clean assembleReleaseChannels

# 生成指定 flavor 的渠道包
./gradlew clean assembleMeituanReleaseChannels

# 临时生成单个渠道
./gradlew clean assembleReleaseChannels -PchannelList=meituan

# 临时生成多个渠道
./gradlew clean assembleReleaseChannels -PchannelList=meituan,dianping

# 使用临时渠道文件
./gradlew clean assembleReleaseChannels -PchannelFile=/path/to/channel.txt
```

#### 5. 使用 configFile 插入额外信息

如果想插入除渠道外的其他信息，使�?`configFile`�?
```gradle
walle {
    // 渠道&额外信息配置文件，与channelFile互斥
    configFile = new File("${project.getProjectDir()}/config.json")
}
```

`config.json` 格式示例�?
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

获取额外信息�?
```java
ChannelInfo channelInfo = WalleChannelReader.getChannelInfo(context);
if (channelInfo != null) {
    String channel = channelInfo.getChannel();
    Map<String, String> extraInfo = channelInfo.getExtraInfo();
}

// 或直接根据key获取
String value = WalleChannelReader.get(context, "buildtime");
```

### 方式三：命令行工�?
详细 CLI 使用说明请参考：[Walle CLI 使用说明](walle-cli/README.md)

#### 基本用法

```bash
# 查看 APK 渠道信息
java -jar walle-cli-all.jar show app-release.apk

# 写入渠道信息
java -jar walle-cli-all.jar put -c meituan app-release.apk app-release-meituan.apk

# 批量写入渠道
java -jar walle-cli-all.jar batch -f channel.txt app-release.apk output/
```

## 📦 生成渠道�?
### 使用 CLI 工具批量生成

1. **准备渠道配置文件** (`channel.txt`)
```
meituan
dianping
xiaomi
huawei
```

2. **执行批量命令**
```bash
java -jar walle-cli-all.jar batch -f channel.txt app-release.apk channels/
```

3. **验证渠道信息**
```bash
java -jar walle-cli-all.jar show channels/app-release-meituan.apk
```

## 🔧 高级用法

### 插入额外信息

除了渠道信息，还可以插入其他自定义信息：

```bash
java -jar walle-cli-all.jar put \
  -c meituan \
  -e buildtime:20260101,hash:abc123 \
  app-release.apk \
  app-release-meituan.apk
```

### 读取额外信息

```java
// 获取所有额外信�?Map<String, String> extraInfo = WalleChannelReader.getExtraInfo(context);

// 获取指定的额外信�?String buildTime = WalleChannelReader.get(context, "buildtime");
```

## 📚 模块说明

本项目包含以下模块：

| 模块 | Maven 坐标 | 说明 |
|------|-----------|------|
| **library** | `com.github.maxZhou7:walle:2.0.14` | Android Library，提供渠道信息读取功�?|
| **plugin** | `com.github.maxZhou7:walle-plugin:2.0.14` | Gradle 插件（已适配 AGP 8.x）|
| **payload_reader** | `com.github.maxZhou7:payload_reader:2.0.14` | APK Signing Block 读取模块 |
| **payload_writer** | `com.github.maxZhou7:payload_writer:2.0.14` | APK Signing Block 写入模块 |
| **walle-cli** | - | 命令行工�?|
| app | - | 示例应用 |

### 单独引用模块

如果你只需要使用特定的功能，可以单独引用对应的模块�?
#### 1. 只使用读取功�?
```gradle
dependencies {
    implementation 'com.github.maxZhou7:payload_reader:2.0.14'
}
```

适用场景：只需要读�?APK 中的渠道信息，不需要写入�?
#### 2. 只使用写入功�?
```gradle
dependencies {
    implementation 'com.github.maxZhou7:payload_writer:2.0.14'
}
```

注意：`payload_writer` 会自动依�?`payload_reader`�?
适用场景：需要在服务端或工具中写入渠道信息到 APK�?
#### 3. 完整 Android Library

```gradle
dependencies {
    implementation 'com.github.maxZhou7:walle:2.0.14'
}
```

这是最常用的方式，包含了读取功能，并且自动处理了所有依赖�?
各模块的详细文档�?- [Payload Reader 使用说明](payload_reader/README.md)
- [Payload Writer 使用说明](payload_writer/README.md)
- [Walle CLI 使用说明](walle-cli/README.md)

## �?常见问题

### 1. 为什么选择 Walle�?
- **速度�?*: 基于 APK Signature V2 Scheme，无需重新签名和压�?- **兼容性好**: 支持 Android 7.0+ �?V2 签名方案
- **灵活性高**: 支持渠道信息和自定义额外信息

### 2. 使用 apksigner 重新签名会怎样�?
使用 apksigner 重新�?Apk 签名会导致渠道信息丢失，需要再次写入渠道信息�?
### 3. �?360 加固的兼容性？

请参考：[360加固失效问题](https://github.com/Meituan-Dianping/walle/wiki/360%E5%8A%A0%E5%9B%BA%E5%A4%B1%E6%95%88%EF%BC%9F)

### 4. Gradle 插件支持

Gradle 插件已适配 AGP 8.x，可以使用以下方式集成：

```gradle
buildscript {
    dependencies {
        classpath 'com.github.maxZhou7:walle-plugin:2.0.14'
    }
}
```

详细使用说明请参考上面的"方式二：Gradle 插件"�?
### 5. JitPack 依赖问题�?01 Unauthorized�?
如果�?JitPack 获取依赖时遇�?`401 Unauthorized` 错误�?
**解决方案 1：手动触�?JitPack 构建**

1. 访问 [JitPack](https://jitpack.io/#maxZhou7/meituanwall)
2. 输入版本号：`v2.0.14`
3. 点击 "Get it" 按钮触发构建
4. 等待构建完成（通常需要几分钟�?5. 查看构建日志确认成功状�?
**解决方案 2：确认仓库为公开状�?*

确保你的 GitHub 仓库 https://github.com/maxZhou7/meituanwall 设置�?**Public（公开�?*。私有仓库需要认证�?
**解决方案 3：清�?Gradle 缓存**

```bash
# 清除 Gradle 缓存
./gradlew clean
rm -rf ~/.gradle/caches/modules-2/files-2.1/com.github.maxZhou7

# 重新同步项目
./gradlew --refresh-dependencies
```

**解决方案 4：使用其他版�?*

如果 v2.0.14 尚未构建，可以尝试使用之前已构建的版本，�?`v2.0.12`�?
## 🛠�?构建项目

如果你想自己构建项目�?
```bash
# 克隆项目
git clone https://github.com/maxZhou7/meituanwall.git
cd walle

# 清理并构�?./gradlew clean build

# 构建 CLI 工具
./gradlew :walle-cli:shadowJar

# 发布到本�?Maven
./gradlew :payload_reader:publishToMavenLocal :library:publishToMavenLocal :plugin:publishToMavenLocal
```

## 🔄 升级说明

### 本次升级内容

本项目在美团原版 Walle 的基础上进行了全面的现代化升级�?
#### 1. **构建工具升级**
- Gradle 升级�?**8.13**
- Android Gradle Plugin (AGP) 升级�?**8.8.0**
- Java 版本升级�?**Java 17**
- Compile SDK 升级�?**34**

#### 2. **依赖管理现代�?*
- 采用 **Version Catalog** (libs.versions.toml) 统一管理依赖版本
- 使用现代化的 **Plugin Management** 配置
- 使用 **Plugins DSL** 替代传统 buildscript 方式

#### 3. **代码规范优化**
- 替换已废弃的 API（如 `buildDir` �?`layout.buildDirectory`�?- 修复 `applicationIdSuffix` 配置问题
- 使用 `proguard-android-optimize.txt` 替代 `proguard-android.txt`
- 更新所有第三方依赖到最新版�?
#### 4. **发布支持**
- 支持通过 **JitPack** 发布和分�?- 配置完整�?Maven Publish 支持
- 提供 sources jar �?javadoc jar

#### 5. **模块优化**
- �?`payload_reader` 模块添加 Maven 发布支持
- 修复多模块依赖问�?- 优化 `jitpack.yml` 构建配置

### 兼容性说�?
- **最低支�?*: Android 5.0 (API 21)
- **目标版本**: Android 14 (API 34)
- **Java 版本**: Java 17
- **Gradle 版本**: 8.13+
- **AGP 版本**: 8.8.0+

> ⚠️ **注意**: 由于 AGP 8.x �?API 变化，Gradle 插件功能暂时禁用，推荐使�?CLI 工具�?Library 依赖方式使用�?

## 🤝 贡献指南

本项目欢迎任何形式的贡献�?
- 🐛 提交 Bug 报告
- 💡 提出新功能建�?- 📝 改进文档
- 🔧 提交代码修复

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

## 🙏 致谢

- **原始项目**: [美团点评 Walle](https://github.com/Meituan-Dianping/walle)
- **技术支�?*: [美团技术团队](http://tech.meituan.com/)
- **参考文�?*: 
  - [APK Signature Scheme v2](https://source.android.com/security/apksigning/v2.html)
  - [Zip Format](https://en.wikipedia.org/wiki/Zip_(file_format))

---

**当前维护�?*: [@maxZhou7](https://github.com/maxZhou7)  
**项目地址**: https://github.com/maxZhou7/meituanwall  
**原始项目**: https://github.com/Meituan-Dianping/walle
