# AndrOBD's library finds its tables by package path (getClass().getResource("res/…")) and
# builds its process variables reflectively, so its package names and those constructors
# must survive shrinking. These are upstream's own rules.
-keep class com.fr3ts0n.pvs.ProcessVar {
    public <init>();
}
-keep class * extends com.fr3ts0n.pvs.ProcessVar {
    public <init>();
}
-keeppackagenames

# The library logs through java.util.logging and never touches these at runtime.
-dontwarn java.awt.**
-dontwarn javax.swing.**
