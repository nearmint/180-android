package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import fr.thermostat6.app180.ui.screen.RecipeDetailScreen

/**
 * Recette tirée au sort, présentée par-dessus l'écran courant.
 *
 * Miroir de la `.sheet(item: $randomRecipe)` de l'iOS
 * (`apple/180/ContentView.swift:74-84`) : le tirage ouvre la **fiche complète**,
 * pas un aperçu. La feuille s'ouvre directement en pleine hauteur — une recette
 * lue à moitié cachée n'aurait aucun intérêt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RandomRecipeSheet(
    recipeId: Int,
    navController: NavController,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState       = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        RecipeDetailScreen(
            recipeId      = recipeId,
            navController = navController,
            onClose       = onDismiss
        )
    }
}
