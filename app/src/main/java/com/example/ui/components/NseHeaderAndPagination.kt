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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.R
import com.example.data.remote.ApiConfig
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
    }
}

@Composable
fun NseTopNavigationBar(
    activeCategory: MovieCategory,
    searchQuery: String,
    isSearchExpanded: Boolean,
    watchlistCount: Int,
    onToggleSearch: () -> Unit,
    onOpenServerSettings: () -> Unit,
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

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = onOpenServerSettings,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GraphiteElevated)
                        .testTag("server_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = "Server Settings",
                        tint = MutedSilver
                    )
                }

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

@Composable
fun ServerSettingsDialog(
    currentBaseUrl: String,
    onDismiss: () -> Unit,
    onSaveBaseUrl: (String) -> String?,
    onResetDefault: () -> Unit
) {
    var inputUrl by remember(currentBaseUrl) { mutableStateOf(currentBaseUrl) }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CharcoalSurface,
        titleContentColor = CrispWhite,
        textContentColor = MutedSilver,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Dns,
                    contentDescription = "Server Configuration",
                    tint = NseCrimson
                )
                Text(
                    text = "Server Configuration",
                    style = MaterialTheme.typography.titleLarge,
                    color = CrispWhite,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Configure the centralized movie catalog API base URL. Default uses the legitimate public-domain cinema catalog (${ApiConfig.DEFAULT_BASE_URL}).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedSilver
                )
                OutlinedTextField(
                    value = inputUrl,
                    onValueChange = {
                        inputUrl = it
                        validationError = null
                    },
                    label = { Text("API Base URL") },
                    singleLine = true,
                    isError = validationError != null,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NseCrimson,
                        unfocusedBorderColor = SlateCardBorder,
                        focusedTextColor = CrispWhite,
                        unfocusedTextColor = CrispWhite,
                        focusedLabelColor = NseCrimson,
                        unfocusedLabelColor = MutedSilver
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input")
                )
                if (validationError != null) {
                    Text(
                        text = validationError!!,
                        style = MaterialTheme.typography.labelSmall,
                        color = NseCrimson
                    )
                }
                Text(
                    text = "NSE STREAM v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MutedSilver
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val error = onSaveBaseUrl(inputUrl)
                    if (error != null) {
                        validationError = error
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = NseCrimson,
                    contentColor = CrispWhite
                ),
                modifier = Modifier.testTag("server_save_button")
            ) {
                Text("Save & Reload")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(
                    onClick = onResetDefault,
                    modifier = Modifier.testTag("server_reset_button")
                ) {
                    Text("Reset Default", color = MutedSilver)
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = CrispWhite)
                }
            }
        }
    )
}
