# R8 rules for release builds. The manifest's services, receiver and activity are kept
# automatically, and Firebase, coroutines and Compose ship their own rules, so only
# CancelIt-specific needs go here.

# Keep line numbers in crash reports. Play Console de-obfuscates them with the mapping file
# that the release build uploads with the bundle.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Saved by name in the Smart keyword lists' storage keys.
-keep enum com.cancelit.app.ai.TermFacet { *; }
