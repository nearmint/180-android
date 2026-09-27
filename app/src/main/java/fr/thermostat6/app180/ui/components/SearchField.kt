package fr.thermostat6.app180.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.thermostat6.app180.ui.theme.Dimens

/**
 * Champ de recherche 180°C — miroir du `.searchable(…)` iOS
 * (`apple/180/SearchView.swift:214-218`, `FavoritesView.swift:92-96`).
 *
 * L'iOS s'appuie sur la barre système : **fond gris rempli, forme pilule,
 * loupe en tête, placeholder gris**, sans bordure ni ligne. L'Android posait un
 * [androidx.compose.material3.OutlinedTextField] rectangulaire à bordure
 * (constat C9). On reproduit ici la forme iOS, une seule fois, pour les deux
 * écrans qui en ont un.
 *
 * @param onSubmit action de la touche « Rechercher » du clavier. C'est le seul
 *   déclencheur d'une recherche côté iOS (`SearchView.swift:219-221`) ; laisser
 *   `null` pour un filtrage au fil de la frappe (Carnet).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(percent = 50)
    val colors = TextFieldDefaults.colors(
        focusedContainerColor   = MaterialTheme.colorScheme.surfaceVariant,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
        // Aucune ligne sous le texte : la barre iOS n'en a pas.
        focusedIndicatorColor   = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor  = Color.Transparent
    )

    BasicTextField(
        value         = value,
        onValueChange = onValueChange,
        singleLine    = true,
        textStyle     = LocalTextStyle.current.merge(
            MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface)
        ),
        cursorBrush     = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            imeAction = if (onSubmit != null) ImeAction.Search else ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onSearch = { onSubmit?.invoke() }),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.searchFieldHeight)
    ) { innerTextField ->
        // `DecorationBox` apporte le fond, la forme et les icônes ; le
        // `BasicTextField` au-dessus permet la hauteur compacte de l'iOS, que le
        // `TextField` Material (56 dp minimum) ne sait pas tenir.
        TextFieldDefaults.DecorationBox(
            value             = value,
            innerTextField    = innerTextField,
            enabled           = true,
            singleLine        = true,
            visualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
            interactionSource = interactionSource,
            placeholder = {
                Text(
                    text  = placeholder,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            leadingIcon = {
                Icon(
                    imageVector        = Icons.Filled.Search,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector        = Icons.Filled.Clear,
                            contentDescription = "Effacer",
                            tint               = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            shape          = shape,
            colors         = colors,
            contentPadding = PaddingValues(0.dp)
        )
    }
}
