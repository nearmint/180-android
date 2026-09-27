package fr.thermostat6.app180.ui.layout

import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.compositionLocalOf

/**
 * Classe de largeur de la fenêtre, mise à disposition de tout l'arbre.
 *
 * Calculée une seule fois dans `MainActivity` — l'API officielle a besoin d'une
 * `Activity` — puis lue partout où la mise en page en dépend. Une seule source :
 * un écran qui recalculerait son propre seuil finirait par diverger des autres.
 *
 * Correspondance avec l'iOS : `horizontalSizeClass == .regular`
 * (`apple/180/ContentView.swift:30`, `RecipeDetailView.swift:26`) couvre tout ce
 * qui n'est pas un téléphone en portrait, soit ici tout ce qui n'est pas
 * [WindowWidthSizeClass.Compact] — c'est le sens de [isRegularWidth].
 */
val LocalWindowWidthClass = compositionLocalOf { WindowWidthSizeClass.Compact }

/**
 * `true` dès que la fenêtre dépasse la largeur d'un téléphone en portrait.
 *
 * Pilote les trois bascules de mise en page : navigation latérale plutôt que
 * barre basse, détail en deux colonnes, et hero décodé en 800 px
 * (`RecipeDetailView.swift:116`).
 */
val WindowWidthSizeClass.isRegularWidth: Boolean
    get() = this != WindowWidthSizeClass.Compact
