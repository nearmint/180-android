package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import fr.thermostat6.app180.ui.theme.Dimens
import fr.thermostat6.app180.ui.theme.app180

/**
 * Un onglet de la tab bar.
 *
 * @param label libellé affiché sous l'icône, aussi lu par le lecteur d'écran.
 * @param icon glyphe de l'onglet.
 */
data class TabBarItem(
    val label: String,
    val icon: ImageVector
)

/**
 * Tab bar flottante — miroir du `TabView` iOS teinté `.tint(.accent180)`
 * (`apple/180/ContentView.swift:50`, `:176-190`).
 *
 * Sur iOS 26 ce `TabView` natif se rend en **pilule flottante translucide**
 * détachée des bords, l'onglet actif portant une pastille et un glyphe accent.
 * Material n'a pas d'équivalent : la [androidx.compose.material3.NavigationBar]
 * est une barre pleine largeur, opaque, dont l'indicateur emprunte
 * `secondaryContainer` — c'est de là que venait la pilule olive du constat C7.
 * Ce composable reproduit donc la forme iOS à la main.
 *
 * Inactifs en blanc (`onBackground`), actif en accent : la hiérarchie de l'iOS,
 * où seul l'onglet courant est coloré.
 */
@Composable
fun FloatingTabBar(
    items: List<TabBarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Les encoches système restent hors de la pilule : elle flotte
            // au-dessus de la barre de navigation, jamais dessous.
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = Dimens.tabBarMargin, vertical = Dimens.tabBarMargin)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.app180.tabBarSurface)
            .padding(Dimens.tabBarPadding),
        horizontalArrangement = Arrangement.spacedBy(Dimens.tabBarPadding),
        verticalAlignment     = Alignment.CenterVertically
    ) {
        items.forEachIndexed { index, item ->
            TabBarCell(
                item     = item,
                selected = index == selectedIndex,
                onSelect = { onSelect(index) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun TabBarCell(
    item: TabBarItem,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    // `Role.Tab` : le lecteur d'écran annonce « onglet », et `selected` porte
    // l'état — inutile d'ajouter un `stateDescription` maison.
    val contentColor =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onBackground

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .selectable(
                selected = selected,
                role     = Role.Tab,
                onClick  = onSelect
            )
            .background(
                if (selected) MaterialTheme.app180.tabBarSelected
                else Color.Transparent
            )
            .height(Dimens.tabBarCellHeight)
            .padding(horizontal = Dimens.tabBarPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector        = item.icon,
            // Le libellé est déjà lu juste en dessous : redire le nom ferait
            // annoncer l'onglet deux fois.
            contentDescription = null,
            tint               = contentColor,
            modifier           = Modifier.size(Dimens.tabBarIconSize)
        )
        Text(
            text     = item.label,
            style    = MaterialTheme.typography.titleSmall,
            color    = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
