# Walle 项目 Kotlin 支持说明

## 概述

Walle 项目已成功添加 Kotlin 支持，允许在项目中混合使用 Java 和 Kotlin 代码。

**最新更新（2026-05）**：项目已升级到 AGP 9.0.0 + Gradle 9.1.0，通过禁用 AGP 内置 Kotlin 支持解决了 `BaseVariant` API 兼容性问题。

### ⚠️ 重要说明：为什么必须禁用 AGP 9.0 内置 Kotlin

AGP 9.0 默认内置了 Kotlin 2.2.10，但在构建时会出现以下错误：

```
Failed to apply plugin 'com.android.internal.application'.
Could not create an instance of type org.jetbrains.kotlin.gradle.plugin.mpp.KotlinAndroidTarget.
ClassNotFoundException: com.android.build.gradle.api.BaseVariant
```

**根本原因**：
- AGP 9.0 移除了旧的 `BaseVariant` API，完全迁移到新的 `androidComponents` API
- 但 KGP 2.2.10（AGP 9.0 内置版本）在初始化 `KotlinAndroidTarget` 时仍然尝试加载 `BaseVariant`
- 这是 **Kotlin Gradle Plugin 内部的实现问题**，不是项目代码的问题

**解决方案**：
在 `gradle.properties` 中添加：
```properties
android.builtInKotlin=false
```

这样可以禁用 AGP 9.0 的内置 Kotlin 支持，避免 KGP 初始化时的 `BaseVariant` 依赖问题。

## 配置详情

### 1. 版本配置

在 `gradle/libs.versions.toml` 中配置了 Kotlin 版本：

```toml
[versions]
agp = "9.0.0"
kotlin = "2.2.10"  # 与 AGP 9.0 内置版本一致

[plugins]
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }

[libraries]
kotlin-stdlib = { module = "org.jetbrains.kotlin:kotlin-stdlib", version.ref = "kotlin" }
```

### 2. 关键配置

#### gradle.properties

```properties
# Disable AGP 9.0 built-in Kotlin support to avoid BaseVariant compatibility issues
# This is required because KGP 2.2.10 (bundled with AGP 9.0) still uses deprecated BaseVariant API internally
android.builtInKotlin=false
```

**说明**：
- AGP 9.0 默认内置了 Kotlin 2.2.10，但 KGP 2.2.10 在初始化时仍依赖已移除的 `BaseVariant` API
- 通过禁用内置支持，我们可以手动控制 Kotlin 插件的使用，避免构建失败
- 这是一个**必要的配置**，不是可选的优化

#### 根项目 build.gradle

```groovy
plugins {
    // AGP 9.0 has built-in Kotlin support
    alias(libs.plugins.kotlin.jvm) apply false  // Still needed for non-Android modules
}

subprojects {
    // ... 其他配置
    
    // Configure Kotlin tasks
    tasks.withType(org.jetbrains.kotlin.gradle.tasks.KotlinCompile).configureEach {
        kotlinOptions {
            jvmTarget = "17"
            freeCompilerArgs += "-Xjsr305=strict"
        }
    }
}
```

### 3. 模块配置

#### Android 模块（app、library）

**当前状态**：由于禁用了 AGP 内置 Kotlin，Android 模块需要手动配置 Kotlin 支持。

**app/build.gradle**:
```groovy
plugins {
    alias(libs.plugins.android.application)
    // 如需启用 Kotlin，可添加：
    // alias(libs.plugins.kotlin.android)
}

android {
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}
```

**library/build.gradle**:
```groovy
plugins {
    alias(libs.plugins.android.library)
    // 如需启用 Kotlin，可添加：
    // alias(libs.plugins.kotlin.android)
    id 'maven-publish'
}
```

**注意**：如果需要在 Android 模块中使用 Kotlin，可以取消注释 `kotlin-android` 插件。但由于我们禁用了 AGP 内置 Kotlin，可能需要额外的配置来解决兼容性问题。

#### JVM 模块（payload_reader、payload_writer、plugin、walle-cli）

这些模块已完全支持 Kotlin：

**payload_reader/build.gradle**:
```groovy
plugins {
    id 'java-library'
    alias(libs.plugins.kotlin.jvm)
    id 'maven-publish'
}

kotlin {
    jvmToolchain(17)
}
```

**payload_writer/build.gradle**:
```groovy
plugins {
    id 'java-library'
    alias(libs.plugins.kotlin.jvm)
    id 'maven-publish'
}

kotlin {
    jvmToolchain(17)
}
```

**plugin/build.gradle**:
```groovy
plugins {
    id 'groovy'
    alias(libs.plugins.kotlin.jvm)
    id 'maven-publish'
    id 'java-gradle-plugin'
}

dependencies {
    implementation libs.kotlin.stdlib
    // ... 其他依赖
}
```

**walle-cli/build.gradle**:
```groovy
plugins {
    id 'java-library'
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

kotlin {
    jvmToolchain(17)
}
```

## 使用示例

### 在 JVM 模块中使用 Kotlin

已在 `payload_reader` 模块中创建了 Kotlin 扩展示例：

**文件位置**: `payload_reader/src/main/java/com/meituan/android/walle/PairExtensions.kt`

```kotlin
package com.meituan.android.walle

/**
 * 将 Pair 转换为可读字符串
 */
internal fun <A, B> Pair<A, B>.toReadableString(): String {
    return "Pair(first=${first}, second=${second})"
}

/**
 * 创建字符串 Pair 的便捷函数
 */
internal fun stringPair(first: String, second: String): kotlin.Pair<String, String> {
    return kotlin.Pair(first, second)
}
```

### 在 Java 代码中调用 Kotlin

Kotlin 代码可以无缝地与现有的 Java 代码互操作：

```java
// Java 代码中可以调用 Kotlin 扩展函数
import com.meituan.android.walle.PairExtensionsKt;

Pair<String, String> pair = new Pair<>("key", "value");
String readable = PairExtensionsKt.toReadableString(pair);
```

## 编译验证

运行以下命令验证 Kotlin 支持：

```bash
# 编译所有 Kotlin 代码
./gradlew compileKotlin

# 编译特定模块的 Kotlin 代码
./gradlew :payload_reader:compileKotlin
./gradlew :payload_writer:compileKotlin
./gradlew :plugin:compileKotlin
./gradlew :walle-cli:compileKotlin

# 查看所有 Kotlin 相关任务
./gradlew tasks --all | grep kotlin
```

## 注意事项

1. **AGP 9.0 内置 Kotlin 问题**：
   - AGP 9.0 默认内置 Kotlin 2.2.10
   - 但内置实现的某些部分仍依赖已移除的 `BaseVariant` API
   - 解决方案：在 `gradle.properties` 中设置 `android.builtInKotlin=false`

2. **类型冲突**：项目中有自定义的 `com.meituan.android.walle.Pair` 类，在 Kotlin 中使用标准库的 `kotlin.Pair` 时需要使用完全限定名以避免冲突。

3. **可见性修饰符**：Kotlin 文件的默认可见性是 `public`，如果扩展的是包私有类型，需要使用 `internal` 修饰符。

4. **混合编译**：Java 和 Kotlin 代码可以共存于同一模块中，Gradle 会自动处理编译顺序。

5. **Walle 插件兼容性**：Walle Gradle 插件已更新以支持 AGP 9.0 的新 Variant API（`androidComponents.onVariants()`）。

## 技术栈

- **Kotlin 版本**: 2.2.10
- **AGP 版本**: 9.0.0
- **Gradle 版本**: 9.1.0
- **JVM 目标**: 17
- **JDK 版本**: 17（必需）

## 升级历史

### v2.1.0 (2026-05)
- ✅ 升级 AGP 从 8.7.0 到 9.0.0
- ✅ 升级 Gradle 从 8.13 到 9.1.0
- ✅ 升级 Kotlin 从 1.9.22 到 2.2.10
- ✅ 禁用 AGP 内置 Kotlin 以解决 BaseVariant 兼容性问题
- ✅ 更新 Walle 插件以支持 AGP 9.0 新 API
- ✅ JVM 模块完全支持 Kotlin
- ⚠️ Android 模块可选择性启用 Kotlin（需额外配置）
