package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import fr.thermostat6.app180.ui.theme.Dimens

/**
 * En-tête de section — miroir de `railHeader` / `SectionHeader`
 * (`apple/180/HomeView.swift:324-355`, `:600-620`).
 *
 * Titre en **Oswald** (`titleLarge`), jamais en Playfair : le serif est réservé
 * à l'éditorial (constat C5). Le lien de droite est **« Voir tout » suivi d'un
 * chevron**, en accent (constat C12) ; sans le chevron, le lien ne se lit pas
 * comme une ouverture.
 *
 * Le titre porte le `weight` : sans lui, un titre long poussait « Voir tout »
 * hors de la ligne et le libellé se cassait sur trois lignes.
 *
 * @param onSeeAll `null` pour un en-tête sans lien (le titre seul).
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier              = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.screenMargin, vertical = Dimens.sectionHeaderSpacing),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.sectionHeaderSpacing)
    ) {
        Text(
            text     = title,
            style    = MaterialTheme.typography.titleLarge,
            color    = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            SeeAllLink(onSeeAll)
        }
    }
}

/** « Voir tout › » — Oswald 14 accent + chevron (`HomeView.swift:350-355`). */
@Composable
private fun SeeAllLink(onClick: () -> Unit) {
    Row(
        modifier              = Modifier.clickable(onClick = onClick),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.seeAllChevronSpacing)
    ) {
        Text(
            text     = "Voir tout",
            style    = MaterialTheme.typography.titleSmall,
            color    = MaterialTheme.colorScheme.primary,
            maxLines = 1
        )
        Icon(
            imageVector        = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            // Le libellé qui précède porte déjà le sens du lien.
            contentDescription = null,
            tint               = MaterialTheme.colorScheme.primary,
            modifier           = Modifier.size(Dimens.seeAllChevronSize)
        )
    }
}
