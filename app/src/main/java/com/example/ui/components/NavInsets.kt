package com.example.ui.components

/**
 * Rotas cujo cabeçalho próprio já aplica statusBarsPadding. NÃO remover rotas desta lista.
 * Toda tela nova com OtakuScreenHeader (ex.: edit_profile) deve ser adicionada aqui.
 */
fun routeHasOwnTopInset(route: String?): Boolean {
    if (route == null) return false
    return NavDestination.entries.any { it.route == route } || route == "settings" || route == "image_crop"
}
