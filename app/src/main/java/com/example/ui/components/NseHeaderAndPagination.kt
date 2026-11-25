package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.MovieCategory
import com.example.ui.theme.BebasNeueFontFamily
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.CrispWhite
import com.example.ui.theme.GraphiteElevated
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.NseCrimson
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SlateCardBorder

@Composable
fun NseBrandLogo(
    onLogoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable { onLogoClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(9.dp))
                .border(1.dp, NseCrimson.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
                .background(ObsidianBlack),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_nse_logo_icon_1794136871384),
                contentDescription = "NSE Stream Logo",
                modifier = Modifier.size(38.dp),
                contentScale = ContentScale.Crop
            )
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "NSE",
                    fontFamily = BebasNeueFontFamily,
                    fontSize = 26.sp,
                    color = NseCrimson,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "STREAM",
                    fontFamily = BebasNeueFontFamily,
                    fontSize = 26.sp,
                    color = CrispWhite,
                    letterSpacing = 1.5.sp
                )
            }
            Text(
                text = "MOVIELINKBD CINEMA",
                style = MaterialTheme.typography.labelSmall,
                color = MutedSilver,
                fontSize = 9.sp,
                letterSpacing = 1.1.sp
            )
        }
    }
}

@Composable
fun NseTopNavigationBar(
    activeCategory: MovieCategory,
    searchQuery: String,
    isSearchExpanded: Boolean,
    watchlistCount: Int,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectCategory: (MovieCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        ObsidianBlack,
                        ObsidianBlack.copy(alpha = 0.96f),
                        CharcoalSurface.copy(alpha = 0.90f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NseBrandLogo(
                onLogoClick = { onSelectCategory(MovieCategory.HOME) },
                modifier = Modifier.testTag("nse_brand_logo")
            )

            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isSearchExpanded) NseCrimson else GraphiteElevated)
                    .testTag("search_toggle_button")
            ) {
                Icon(
                    imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (isSearchExpanded) "Close Search" else "Open Search",
                    tint = CrispWhite
                )
            }
        }

        AnimatedVisibility(visible = isSearchExpanded) {
            Column(modifier = Modifier.padding(top = 12.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_input_field"),
                    placeholder = {
                        Text(
                            text = "Search Bangla, Hollywood, Bollywood, South Indian...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSilver
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Icon",
                            tint = NseCrimson
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = onClearSearch,
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear search query",
                                    tint = MutedSilver
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NseCrimson,
                        unfocusedBorderColor = SlateCardBorder,
                        focusedContainerColor = GraphiteElevated,
                        unfocusedContainerColor = CharcoalSurface,
                        focusedTextColor = CrispWhite,
                        unfocusedTextColor = CrispWhite
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            onSearchSubmit(searchQuery)
                            focusManager.clearFocus()
                        }
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MovieCategory.entries.forEach { category ->
                val isSelected = activeCategory == category && searchQuery.isBlank()
                val label = if (category == MovieCategory.MY_LIST && watchlistCount > 0) {
                    "${category.title} ($watchlistCount)"
                } else {
                    category.title
                }
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectCategory(category) },
                    label = {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = NseCrimson,
                        selectedLabelColor = CrispWhite,
                        containerColor = GraphiteElevated,
                        labelColor = MutedSilver
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = SlateCardBorder,
                        selectedBorderColor = NseCrimson
                    ),
                    modifier = Modifier.testTag("category_chip_${category.id}")
                )
            }
        }
    }
}
