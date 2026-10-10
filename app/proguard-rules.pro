# 应用本身没有反射入口，默认规则够用。以下是为可观测风险保留的。

# ---- kotlinx.serialization -------------------------------------------------
# @Serializable 类靠生成的 $$serializer 反射反序列化；类名与字段名必须保留，
# 否则 JSON 字段（如 prizegrades）匹配不上。
-keepattributes *Annotation*, InnerClasses
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

# ---- Koin -------------------------------------------------------------------
# Koin **不发布** R8 消费规则（jvm jar 与 android aar 里都没有 proguard.txt）。
# 依赖图在运行时靠 KClass 引用解析，混淆类名本身通常无害，但 R8 的"裁剪"可能
# 移除仅通过反射可达的内部类。这里整体保留 Koin 包，代价很小（几十 KB），
# 换来的是 DI 一定可用——启动阶段崩一次比省这几 KB 代价大得多。
-keep class org.koin.** { *; }
-keepclassmembers class org.koin.** { *; }
-keepclassmembers class org.koin.core.** { *; }

# ---- 应用自身的依赖注入图与 ViewModel ---------------------------------------
# 这些类型由 Koin 在运行时按 KClass 匹配，保留它们避免被重命名后匹配失败。
-keep class com.ssqpick.**Repository { *; }
-keep class com.ssqpick.**ViewModel { *; }
-keepclassmembers class com.ssqpick.**ViewModel {
    <init>(...);
}
-keep interface com.ssqpick.**Repository { *; }
-keep interface com.ssqpick.**ViewModel** { *; }

# ---- SQLDelight -------------------------------------------------------------
# 生成的 Query 类用字符串查询标识与表名，实体字段名与查询方法需保留
-keep class com.ssqpick.store.db.** { *; }
-keep class app.cash.sqldelight.** { *; }

# ---- ktor -------------------------------------------------------------------
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn io.ktor.**
