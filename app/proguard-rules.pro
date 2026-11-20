# Picket release rules. Library rules (Shizuku, kotlinx.serialization, Room, Compose) ship with the
# libraries themselves; :privileged contributes the UserService keep rules via consumer-rules.pro.

# Keep line numbers for readable stack traces in bug reports; hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
