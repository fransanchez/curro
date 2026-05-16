package com.curro.app.data.apps

/**
 * Spanish colloquial app names → ordered candidate package lists (US-027 / SF-4.3).
 *
 * Resolution order in [com.curro.app.handler.OpenAppHandler]:
 *   1. Exact alias hit (case + accent-insensitive on the key, via [curroNormalize]).
 *   2. Substring `contains` against installed labels.
 *   3. Levenshtein ≤ 3 (only when query length ≥ 4).
 *
 * Order inside each value list matters: the **first installed** package wins. Pad each list
 * with realistic Xiaomi / Samsung / Google candidates so HyperOS variants also resolve without
 * requiring a code change.
 *
 * Key rule: keys are kept lowercase, accents preserved so [curroNormalize] canonicalises both
 * sides identically. Value package names are the raw identifiers sent to PackageManager.
 */
object ColloquialAppAliases {
    val byColloquialName: Map<String, List<String>> =
        mapOf(
            "whatsapp" to listOf("com.whatsapp"),
            "wasap" to listOf("com.whatsapp"),
            "guasap" to listOf("com.whatsapp"),
            "guasá" to listOf("com.whatsapp"),
            "la cámara" to listOf("com.android.camera", "com.android.camera2", "com.miui.camera"),
            "cámara" to listOf("com.android.camera", "com.android.camera2", "com.miui.camera"),
            "las fotos" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
            "fotos" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
            "la galería" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
            "galería" to listOf("com.miui.gallery", "com.google.android.apps.photos"),
            "el correo" to listOf("com.google.android.gm", "com.samsung.android.email.provider"),
            "correo" to listOf("com.google.android.gm", "com.samsung.android.email.provider"),
            "gmail" to listOf("com.google.android.gm"),
            "el teléfono" to listOf("com.google.android.dialer", "com.android.dialer", "com.android.contacts"),
            "teléfono" to listOf("com.google.android.dialer", "com.android.dialer"),
            "los contactos" to listOf("com.android.contacts", "com.google.android.contacts"),
            "contactos" to listOf("com.android.contacts", "com.google.android.contacts"),
            "los mensajes" to listOf("com.google.android.apps.messaging", "com.android.messaging"),
            "mensajes" to listOf("com.google.android.apps.messaging", "com.android.messaging"),
            "ajustes" to listOf("com.android.settings"),
            "los ajustes" to listOf("com.android.settings"),
            "configuración" to listOf("com.android.settings"),
            "youtube" to listOf("com.google.android.youtube"),
            "calculadora" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "la calculadora" to listOf("com.google.android.calculator", "com.android.calculator2"),
            "el reloj" to listOf("com.android.deskclock", "com.google.android.deskclock"),
            "reloj" to listOf("com.android.deskclock", "com.google.android.deskclock"),
            "el navegador" to listOf("com.android.chrome", "org.mozilla.firefox"),
            "chrome" to listOf("com.android.chrome"),
        )
}
