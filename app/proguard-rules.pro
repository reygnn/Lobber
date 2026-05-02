# BouncyCastle und sshj nutzen Reflection und Service-Provider-Lookup, die
# R8 sonst abschneidet. Ohne diese Regeln verliert BC im Release-Build
# Algorithmen wie Ed25519, und der KeyFactory-Lookup landet stillschweigend
# auf Conscrypt — das sshj's hardcoded PKCS#8-Preamble ablehnt
# ("invalid EdDSA PKCS#8 key preamble").
-keep class org.bouncycastle.** { *; }
-keep class net.schmizz.sshj.** { *; }
-keep class com.hierynomus.sshj.** { *; }

# BC registriert Algorithmen über Reflection-Lookup auf Klassennamen aus
# Provider.put("KeyFactory.Ed25519", "..."). Diese Strings dürfen nicht
# obfuscated werden, sonst zeigt der Lookup ins Leere.
-dontwarn org.bouncycastle.**
-dontwarn net.schmizz.sshj.**
-dontwarn com.hierynomus.sshj.**

# slf4j-nop wird zur Laufzeit von sshj resolved.
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.**
