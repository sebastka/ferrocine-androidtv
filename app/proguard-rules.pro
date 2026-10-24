# kotlinx.serialization: keep the generated serializers of the API models.
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class io.github.sebastka.ferrocine.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class io.github.sebastka.ferrocine.** {
    kotlinx.serialization.KSerializer serializer(...);
}
