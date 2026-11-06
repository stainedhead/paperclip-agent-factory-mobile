# R8 rules for the release build (ADR-132). Every rule has a reason; add one only for a failure you reproduced.

# The PRD (Security) requires Log calls out of release builds: no diagnostic text leaves through logcat.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}
