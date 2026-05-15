package com.curro.app.domain.catalog

/**
 * The Fase-1 (prototype MVP) function catalog (spec §5, §14, `function-catalog`
 * skill).
 *
 * **Order matters**: spec §14 implementation order — the first four validate
 * the architecture at zero risk, the last three touch sensitive permissions.
 *
 * Any change here MUST be mirrored in:
 *   1. The `function-catalog` skill (`.claude/skills/function-catalog/SKILL.md`).
 *   2. `docs/curro-spec-v1.0.md` §5.
 *
 * Use `/add-function <name>` to keep them aligned.
 */
object Fase1Catalog {
    private val tellTime =
        CatalogFunction(
            name = "tell_time",
            description = "Dice en voz alta la hora actual, el día de la semana y/o la fecha.",
            params =
                listOf(
                    CatalogParam(
                        name = "what",
                        type = ParamType.Enum(listOf("time", "date", "day", "all")),
                        required = false,
                        description = "qué información dar",
                        defaultValue = "all",
                    ),
                ),
            needsConfirmation = NeedsConfirmation.NO,
            voiceExamples =
                listOf(
                    "qué hora es",
                    "qué día es hoy",
                    "qué fecha es",
                    "dime el día",
                ),
        )

    private val openApp =
        CatalogFunction(
            name = "open_app",
            description = "Abre cualquier app instalada en el teléfono, identificada por nombre coloquial.",
            params =
                listOf(
                    CatalogParam(
                        name = "app_name",
                        type = ParamType.Str,
                        required = true,
                        description = "nombre coloquial de la app (\"las fotos\", \"el correo\", \"WhatsApp\")",
                    ),
                ),
            needsConfirmation = NeedsConfirmation.NO,
            voiceExamples =
                listOf(
                    "abre la cámara",
                    "abre WhatsApp",
                    "ponme las fotos",
                    "abre el correo",
                ),
        )

    private val calculate =
        CatalogFunction(
            name = "calculate",
            description = "Resuelve una operación matemática expresada en lenguaje natural y la lee en voz alta.",
            params =
                listOf(
                    CatalogParam(
                        name = "expression",
                        type = ParamType.Str,
                        required = true,
                        description = "operación en lenguaje natural",
                    ),
                ),
            needsConfirmation = NeedsConfirmation.NO,
            voiceExamples =
                listOf(
                    "cuánto es cuarenta y siete por ocho",
                    "calcula mil dividido entre veinticinco",
                    "cuánto suma quince y veintitrés",
                    "el veintiuno por ciento de doscientos",
                ),
        )

    private val help =
        CatalogFunction(
            name = "help",
            description = "Explica al usuario qué cosas puede hacer Curro.",
            params =
                listOf(
                    CatalogParam(
                        name = "topic",
                        type = ParamType.Str,
                        required = false,
                        description = "sobre qué quiere ayuda específicamente",
                    ),
                ),
            needsConfirmation = NeedsConfirmation.NO,
            voiceExamples =
                listOf(
                    "qué puedes hacer",
                    "ayuda",
                    "qué sabes hacer",
                    "cómo te pido cosas",
                ),
        )

    private val readLastWhatsApp =
        CatalogFunction(
            name = "read_last_whatsapp",
            description =
                "Lee en voz alta el último mensaje de WhatsApp recibido " +
                    "(opcionalmente de un remitente concreto).",
            params =
                listOf(
                    CatalogParam(
                        name = "sender",
                        type = ParamType.Str,
                        required = false,
                        description = "nombre del remitente",
                    ),
                ),
            needsConfirmation = NeedsConfirmation.NO,
            voiceExamples =
                listOf(
                    "léeme el último mensaje",
                    "qué dice Pepito",
                    "léeme lo de mi hija",
                    "tengo mensajes nuevos",
                ),
        )

    private val readAllUnreadWhatsApp =
        CatalogFunction(
            name = "read_all_unread_whatsapp",
            description = "Lee todos los mensajes de WhatsApp no leídos, agrupados por remitente.",
            params = emptyList(),
            needsConfirmation = NeedsConfirmation.NO,
            voiceExamples =
                listOf(
                    "léeme todos los mensajes",
                    "qué tengo sin leer",
                    "qué mensajes hay",
                ),
        )

    private val callContact =
        CatalogFunction(
            name = "call_contact",
            description = "Inicia una llamada telefónica a un contacto resuelto por nombre o alias.",
            params =
                listOf(
                    CatalogParam(
                        name = "contact",
                        type = ParamType.Str,
                        required = true,
                        description = "nombre del contacto o alias aprendido",
                    ),
                ),
            needsConfirmation = NeedsConfirmation.CONDITIONAL,
            voiceExamples =
                listOf(
                    "llama a Pepito",
                    "llámame a mi hija",
                    "ponme con el médico",
                    "marca el número de Carmen",
                ),
        )

    /**
     * The 7 Fase-1 functions, in spec §14 implementation order. The order is
     * load-bearing for the prompt rendering (the model sees them top-to-bottom).
     */
    val functions: List<CatalogFunction> =
        listOf(
            tellTime,
            openApp,
            calculate,
            help,
            readLastWhatsApp,
            readAllUnreadWhatsApp,
            callContact,
        )
}
