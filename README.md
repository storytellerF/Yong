# Yong

[![](https://jitpack.io/v/storytellerF/Yong.svg)](https://jitpack.io/#storytellerF/Yong)

lint your kotlin uncaught exceptions. 

```groovy
maven { url 'https://jitpack.io' }
```

```groovy
implementation 'com.github.storytellerF:Yong:1.1'
```

支持处理的类型包括

1. Kotlin 通过@kotlin.jvm.Throws 抛出的异常
2. java 通过关键字throws 抛出的异常
3. 方法中使用代码`throw Exception()` 抛出的异常
4. 为了使用retrofit 而定义的接口方法
5. 使用`com/storyteller_f/yong/definition/Throws.kt` 注解的class 中的所有方法