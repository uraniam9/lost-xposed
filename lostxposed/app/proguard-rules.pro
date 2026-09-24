# The entry class is named by META-INF/xposed/java_init.list, which R8 does not rewrite
# unless told to. Without this the list points at a class that no longer exists.
-adaptresourcefilecontents META-INF/xposed/java_init.list

-keep class dev.lostxposed.entry.xposed.LostXposedEntry { *; }

# Hooker implementations are instantiated by us but invoked by the framework.
-keep class * implements io.github.libxposed.api.XposedInterface$Hooker { *; }

-keep class * implements dev.lostxposed.core.api.Injection { *; }

# SelfCheckFeature finds this by name, from a different module, so R8 must not rename it.
# Renaming it would make the app report "module not loaded" while the module ran fine.
-keep class dev.lostxposed.ModuleStatus { *; }

-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
