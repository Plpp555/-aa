# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.aa.launcher.**$$serializer { *; }
-keepclassmembers class com.aa.launcher.** { *** Companion; }
-keepclasseswithmembers class com.aa.launcher.** { kotlinx.serialization.KSerializer serializer(...); }

# Services referenced only from the manifest
-keep class com.aa.launcher.service.** { *; }
