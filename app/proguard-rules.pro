# ProGuard rules for Bitprix

# Data models - Keep classes and fields used for GSON serialization
-keep @com.google.gson.annotations.SerializedName class io.github.hypnoticHODL.bitprix.model.**
-keepclassmembers class io.github.hypnoticHODL.bitprix.model.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Retrofit 2
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-dontwarn retrofit2.**
-keep class retrofit2.Response
-keep class retrofit2.ServiceMethod { *; }
-keep class retrofit2.HttpServiceMethod { *; }
-keep class retrofit2.OkHttpCall { *; }
-keep class retrofit2.ParameterHandler { *; }
-keep class retrofit2.RequestBuilder { *; }
-keepclasseswithmembers interface * {
    @retrofit2.http.* <methods>;
}

# GSON
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn sun.misc.**
-keep class com.google.gson.stream.JsonReader { *; }
-keep class com.google.gson.stream.JsonWriter { *; }
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers class io.github.hypnoticHODL.bitprix.model.** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# MPAndroidChart
# Keep chart classes used by the app + data/model classes needed for rendering.
# Use targeted keeps instead of broad package wildcards to reduce R8 warnings.
-keep class com.github.mikephil.charting.charts.LineChart { *; }
-keep class com.github.mikephil.charting.charts.BarChart { *; }
-keep class com.github.mikephil.charting.data.LineData { *; }
-keep class com.github.mikephil.charting.data.LineDataSet { *; }
-keep class com.github.mikephil.charting.data.BarData { *; }
-keep class com.github.mikephil.charting.data.BarDataSet { *; }
-keep class com.github.mikephil.charting.data.Entry { *; }
-keep class com.github.mikephil.charting.data.BarEntry { *; }
-keep class com.github.mikephil.charting.components.XAxis { *; }
-keep class com.github.mikephil.charting.components.YAxis { *; }
-keep class com.github.mikephil.charting.components.Legend { *; }
-keep class com.github.mikephil.charting.components.MarkerView { *; }
-keep class com.github.mikephil.charting.formatter.IAxisValueFormatter { *; }
-keep class com.github.mikephil.charting.formatter.ValueFormatter { *; }
-keep class com.github.mikephil.charting.highlight.Highlight { *; }
-keep class com.github.mikephil.charting.listener.OnChartValueSelectedListener { *; }
-keep class com.github.mikephil.charting.utils.ViewPortHandler { *; }
-keep class com.github.mikephil.charting.utils.Transformer { *; }
-keepattributes RuntimeVisibleAnnotations
-dontwarn com.github.mikephil.charting.**

# OkHttp
-keepattributes Signature, *Annotation*
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase
-keep class okhttp3.internal.Internal { *; }
-keep class okhttp3.internal.connection.RealConnection { *; }
-keep class okhttp3.internal.http.RealInterceptorChain { *; }
-keep class okhttp3.internal.http.HttpCodec { *; }
-keep class okhttp3.internal.http.StreamAllocation { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepnames class kotlinx.coroutines.android.AndroidExceptionPreHandler {}
-keepnames class kotlinx.coroutines.android.AndroidDispatcherFactory {}
-dontwarn kotlinx.coroutines.**