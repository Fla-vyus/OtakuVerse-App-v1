package com.example.data.model

enum class AdultMode(val id: String, val title: String) {
    OFF("off", "Desativado (Censurado)"),
    INCLUDE("include", "Incluir no catálogo"),
    ONLY("only", "Apenas conteúdo adulto (18+)");

    companion object {
        fun fromId(id: String?): AdultMode {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OFF
        }
    }
}
