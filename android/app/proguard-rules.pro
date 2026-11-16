# Keep Hilt generated entry points.
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Clerk SDK keeps its own consumer rules; this guard covers reflective auth models.
-keep class com.clerk.** { *; }
