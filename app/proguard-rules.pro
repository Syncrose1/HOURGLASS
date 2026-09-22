# HOURGLASS keeps nothing across the network and uses no reflection of its own, so the
# only rules needed are the ones the libraries cannot infer.

# Room generates implementations that are looked up by name.
-keep class androidx.room.RoomDatabase
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}

# Entities are read and written reflectively by Room's generated adapters.
-keep class com.hourglass.data.entity.** { *; }

# Kotlin coroutines internals referenced only from generated code.
-dontwarn kotlinx.coroutines.**
