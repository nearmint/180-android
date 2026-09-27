package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.util.accent180
import androidx.compose.ui.graphics.Color

/** Option de tri générique (`apple/180/RecipeFilterSheet.swift:4-7`). */
data class RecipeSortOption(val id: String, val label: String)

/** Option de filtre taxonomique par slug (`RecipeFilterSheet.swift:10-16`). */
data class RecipeFilterOption(
    val slug: String,
    val name: String,
    val icon: ImageVector? = null
)

/** Sélection courante de la feuille. */
data class RecipeFilterSelection(
    val sortId: String,
    val seasonSlug: String? = null,
    val categorySlug: String? = null
)

/**
 * Feuille de filtres/tri **réutilisable**, présentée en bottom sheet.
 *
 * Miroir de `RecipeFilterSheet` (`apple/180/RecipeFilterSheet.swift:26-138`),
 * partagée entre « Toutes les recettes » et « Mon carnet de recettes ».
 *
 * Le composant ne connaît ni la source des données ni le mode de filtrage
 * (serveur ou local) : il manipule une [RecipeFilterSelection] locale et ne la
 * remonte qu'à la validation, via [onApply]. Fermer sans appliquer abandonne
 * les changements.
 *
 * Sélection **simple** par taxonomie, par slug : re-toucher l'option active la
 * désélectionne (`RecipeFilterSheet.swift:66`, `:76`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeFilterSheet(
    sortOptions: List<RecipeSortOption>,
    seasons: List<RecipeFilterOption>,
    categories: List<RecipeFilterOption>,
    selection: RecipeFilterSelection,
    defaultSortId: String,
    onApply: (RecipeFilterSelection) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember { mutableStateOf(selection) }

    val hasActiveFilter = draft.seasonSlug != null ||
        draft.categorySlug != null ||
        draft.sortId != defaultSortId

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {

            // ── Barre de titre ────────────────────────────────────────────────
            Row(
                modifier              = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment     = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text       = "Filtres",
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                TextButton(onClick = { onApply(draft) }) {
                    Text("Appliquer", fontWeight = FontWeight.SemiBold, color = Color.accent180)
                }
            }
            HorizontalDivider()

            LazyColumn(Modifier.fillMaxWidth()) {

                if (sortOptions.isNotEmpty()) {
                    item { SheetSectionTitle("Trier par") }
                    items(sortOptions.size) { index ->
                        val option = sortOptions[index]
                        SheetRow(
                            title   = option.label,
                            isOn    = draft.sortId == option.id,
                            onClick = { draft = draft.copy(sortId = option.id) }
                        )
                    }
                }

                if (seasons.isNotEmpty()) {
                    item { SheetSectionTitle("Saison") }
                    item {
                        SheetRow(
                            title   = "Toutes",
                            isOn    = draft.seasonSlug == null,
                            onClick = { draft = draft.copy(seasonSlug = null) }
                        )
                    }
                    items(seasons.size) { index ->
                        val season = seasons[index]
                        SheetRow(
                            title   = season.name,
                            icon    = season.icon,
                            isOn    = draft.seasonSlug == season.slug,
                            onClick = {
                                draft = draft.copy(
                                    seasonSlug = if (draft.seasonSlug == season.slug) null else season.slug
                                )
                            }
                        )
                    }
                }

                if (categories.isNotEmpty()) {
                    item { SheetSectionTitle("Type de plat") }
                    item {
                        SheetRow(
                            title   = "Tous",
                            isOn    = draft.categorySlug == null,
                            onClick = { draft = draft.copy(categorySlug = null) }
                        )
                    }
                    items(categories.size) { index ->
                        val category = categories[index]
                        SheetRow(
                            title   = category.name,
                            icon    = category.icon,
                            isOn    = draft.categorySlug == category.slug,
                            onClick = {
                                draft = draft.copy(
                                    categorySlug = if (draft.categorySlug == category.slug) null else category.slug
                                )
                            }
                        )
                    }
                }

                // Réinitialisation offerte seulement quand il y a quelque chose
                // à réinitialiser (`RecipeFilterSheet.swift:79-93`).
                if (hasActiveFilter) {
                    item {
                        HorizontalDivider(Modifier.padding(vertical = 8.dp))
                        TextButton(
                            onClick  = {
                                draft = RecipeFilterSelection(sortId = defaultSortId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text  = "Réinitialiser les filtres",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSectionTitle(title: String) {
    Text(
        text     = title,
        style    = MaterialTheme.typography.labelLarge,
        color    = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    )
}

@Composable
private fun SheetRow(
    title: String,
    icon: ImageVector? = null,
    isOn: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier          = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = Color.accent180,
                modifier           = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        if (isOn) {
            Icon(
                imageVector        = Icons.Filled.Check,
                contentDescription = null,
                tint               = Color.accent180,
                modifier           = Modifier.size(20.dp)
            )
        }
    }
    Spacer(Modifier.height(0.dp))
}
