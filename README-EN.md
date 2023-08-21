# Yong

[![](https://jitpack.io/v/storytellerF/Yong.svg)](https://jitpack.io/#storytellerF/Yong)

[CN](README.md)

lint your kotlin uncaught exceptions. 

```groovy
maven { url 'https://jitpack.io' }
```

```groovy
implementation 'com.github.storytellerF:Yong:1.2'
```

support：

1. @kotlin.jvm.Throws in kotlin
2. java throws keyword
3. `throw Exception()` in code
4. retrofit annotation
5. method in class annotated by `com/storyteller_f/yong/definition/Throws.kt`