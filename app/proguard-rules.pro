# 应用本身没有反射入口，保留默认即可。以下保留项均为可安全删减的通用规则，
# 主要为避免 R8 误删 Compose/Kotlin 元数据造成的疑难崩溃。
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# kotlinx.serialization
-keepclassmembers class com.ssqpick.** {
    *** Companion;
}
-keepclasseswithmembers class com.ssqpick.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class com.ssqpick.**
-keepclassmembers class com.ssqpick.** {
    static <1>$Companion Companion;
}

# SQLDelight 生成的接口按名字使用
-keep class app.cash.sqldelight.** { *; }

# ktor CIO/OkHttp 引擎
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
