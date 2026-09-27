package fr.thermostat6.app180.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.data.model.Recipe
import fr.thermostat6.app180.ui.theme.Dimens
import kotlinx.coroutines.launch

/**
 * Carrousel « Slider de recettes » — module home exposé comme un `rail` porteur
 * de `variant: "slider"`.
 *
 * Miroir de `RecipeSlider` (`apple/180/RecipeSlider.swift`) : mêmes données
 * qu'un rail, seule la **présentation** change — une recette en vedette à la
 * fois au lieu du rail dense de cartes de 200 dp.
 *
 * Le visuel d'un slide est **exactement** le hero de l'accueil
 * ([FeaturedRecipeCard]) : aucun langage visuel n'est introduit ici, seule la
 * mécanique de défilement l'est. L'image garde donc son cadrage et son
 * `ImageRenderSize` d'origine — **aucun recadrage**.
 *
 * Chaque page occupe toute la largeur du viewport et porte elle-même sa
 * gouttière de 16 dp, comme côté iOS (`RecipeSlider.swift:48-52`) : la carte se
 * recentre parfaitement à chaque arrêt et l'écart perçu entre deux cartes vaut
 * deux gouttières.
 */
@Composable
fun RecipeSlider(
    recipes: List<Recipe>,
    favIds: Set<Int>,
    onRecipe: (Int) -> Unit,
    onFav: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recipes.isEmpty()) return

    val pagerState = rememberPagerState(pageCount = { recipes.size })
    val scope      = rememberCoroutineScope()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        HorizontalPager(state = pagerState) { page ->
            val recipe = recipes[page]
            FeaturedRecipeCard(
                recipe          = recipe,
                isFavorite      = recipe.id in favIds,
                onClick         = { onRecipe(recipe.id) },
                onFavoriteClick = { onFav(recipe.id) },
                modifier        = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (recipes.size > 1) {
            SliderIndicator(
                count   = recipes.size,
                current = pagerState.currentPage,
                // Défilement **animé**, comme le `withAnimation(.snappy)` de
                // l'iOS (`RecipeSlider.swift:105`, `:125`).
                onSelect = { scope.launch { pagerState.animateScrollToPage(it) } }
            )
        }
    }
}

/**
 * Frise de pagination : pastille allongée sur la position courante, points sur
 * les autres — miroir de l'`indicator` iOS (`RecipeSlider.swift:93-121`).
 *
 * La rangée est **un seul** élément d'accessibilité : un lecteur d'écran annonce
 * « Recettes en vedette, 2 sur 6 ». Exposer six boutons alourdirait le parcours
 * pour une navigation que le balayage de la piste permet déjà.
 */
@Composable
private fun SliderIndicator(
    count: Int,
    current: Int,
    onSelect: (Int) -> Unit
) {
    Row(
        modifier              = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                contentDescription = "Recettes en vedette"
                stateDescription   = "${current + 1} sur $count"
            },
        horizontalArrangement = Arrangement.spacedBy(Dimens.sliderDotSpacing, Alignment.CenterHorizontally)
    ) {
        repeat(count) { index ->
            val isCurrent = index == current
            val dotWidth by animateDpAsState(
                targetValue = if (isCurrent) Dimens.sliderDotActiveWidth else Dimens.sliderDotSize,
                label       = "slider_dot_width"
            )
            Box(
                modifier         = Modifier
                    // Cible tactile élargie autour d'un point de 6 dp, sans
                    // écarter visuellement les pastilles (`RecipeSlider.swift:100-102`).
                    .size(width = Dimens.sliderDotTouchWidth, height = Dimens.sliderDotTouchHeight)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = { onSelect(index) }
                    )
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .width(dotWidth)
                        .height(Dimens.sliderDotSize)
                        .clip(CircleShape)
                        .background(
                            // Points inactifs en `.systemGray4`, pas en gris de
                            // texte estompé (`RecipeSlider.swift:98`).
                            if (isCurrent) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }
        }
    }
}
