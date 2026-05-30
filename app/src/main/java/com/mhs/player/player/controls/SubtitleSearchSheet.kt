package com.mhs.player.player.controls

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.mhs.player.player.subtitles.model.SubtitleResult
import com.mhs.player.player.subtitles.SubtitleRepository
import com.mhs.player.ui.theme.designsystem.AppColors
import com.mhs.player.ui.theme.designsystem.AppShapes
import com.mhs.player.ui.theme.designsystem.AppTypography
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassCard
import com.mhs.player.ui.theme.designsystem.GlassStyles.glassOverlay
import com.mhs.player.ui.theme.glassButton
import com.mhs.player.ui.theme.accentGlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SubtitleSearchViewModel @Inject constructor(
    private val repository: SubtitleRepository
) : ViewModel() {

    private val _state = MutableStateFlow(SubtitleSearchUiState())
    val state: StateFlow<SubtitleSearchUiState> = _state.asStateFlow()

    fun search(query: String, videoPath: String?, lang: String, apiKey: String) {
        if (query.isBlank()) return
        Log.d("MHSPlayer-Subtitles", "ViewModel: Search requested - Query: '$query', Lang: '$lang'")
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null, query = query, videoPath = videoPath)
            val results = repository.search(query, lang, videoPath, apiKey)
            Log.d("MHSPlayer-Subtitles", "ViewModel: Search finished - Results found: ${results.size}")
            _state.value = _state.value.copy(isLoading = false, results = results, error = null)
        }
    }

    fun download(result: SubtitleResult, videoTitle: String, apiKey: String, onDone: (File?) -> Unit) {
        if (result.provider == "local") {
            onDone(File(result.downloadUrl))
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(downloadingId = result.id)
            val file = repository.download(result, videoTitle, apiKey)
            _state.value = _state.value.copy(downloadingId = null)
            onDone(file)
        }
    }

    fun autoSearch(filename: String, videoPath: String?, lang: String, apiKey: String) {
        val query = repository.extractQueryFromFilename(filename)
        search(query, videoPath, lang, apiKey)
    }
}

data class SubtitleSearchUiState(
    val isLoading: Boolean = false,
    val results: List<SubtitleResult> = emptyList(),
    val query: String = "",
    val videoPath: String? = null,
    val downloadingId: String? = null,
    val error: String? = null
)

private val SUBTITLE_LANGUAGES = listOf(
    "ml" to "Malayalam", "ta" to "Tamil", "hi" to "Hindi", "te" to "Telugu",
    "kn" to "Kannada", "en" to "English", "ar" to "Arabic", "fr" to "French",
    "de" to "German", "es" to "Spanish", "pt" to "Portuguese", "ru" to "Russian",
    "tr" to "Turkish", "zh" to "Chinese", "ja" to "Japanese", "ko" to "Korean",
    "it" to "Italian", "nl" to "Dutch", "pl" to "Polish", "id" to "Indonesian",
    "vi" to "Vietnamese", "th" to "Thai", "sv" to "Swedish", "no" to "Norwegian",
    "da" to "Danish", "fi" to "Finnish", "cs" to "Czech", "ro" to "Romanian",
    "hu" to "Hungarian", "el" to "Greek", "he" to "Hebrew", "fa" to "Persian",
    "ur" to "Urdu", "bn" to "Bengali", "si" to "Sinhala"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleSearchSheet(
    videoFilename: String,
    videoPath: String? = null,
    apiKey: String,
    preferredLanguage: String = "ml",
    onSubtitleSelected: (File, String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: SubtitleSearchViewModel = androidx.hilt.navigation.compose.hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    var query by remember { mutableStateOf(viewModel.state.value.query) }
    var selectedLang by remember { mutableStateOf(preferredLanguage) }
    val videoTitle = remember { videoFilename.substringBeforeLast(".") }
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    
    val performSearch = {
        keyboardController?.hide()
        focusManager.clearFocus()
        viewModel.search(query, videoPath, selectedLang, apiKey)
    }

    // Re-search whenever the language chip changes — always, not just when empty.
    // This ensures switching from Malayalam to English gives fresh English results.
    LaunchedEffect(selectedLang) {
        if (videoFilename.isNotBlank()) {
            val targetQuery = query.ifBlank { viewModel.state.value.query }
            if (targetQuery.isNotBlank()) {
                viewModel.search(targetQuery, videoPath, selectedLang, apiKey)
            } else {
                viewModel.autoSearch(videoFilename, videoPath, selectedLang, apiKey)
            }
        }
    }

    // Keep query box in sync with any programmatic query updates
    LaunchedEffect(state.query) {
        if (state.query.isNotBlank() && state.query != query) {
            query = state.query
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = { 
                        keyboardController?.hide()
                        focusManager.clearFocus()
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 500.dp)
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.95f)
                    .glassOverlay(shape = AppShapes.RoundedLG, isPlaybackActive = true)
                    .padding(20.dp)
            ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Online Subtitles",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Search and apply subtitles for your media",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.background(MaterialTheme.colorScheme.onSurface.copy(0.05f), CircleShape)
                        ) {
                            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    // Search Section
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Movie or show name...", color = MaterialTheme.colorScheme.onSurface.copy(0.4f)) },
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.CyanGlow) },
                        trailingIcon = {
                            if (state.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = AppColors.CyanGlow)
                            } else if (query.isNotEmpty()) {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Default.Clear, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                                }
                            }
                        },
                        singleLine = true,
                        shape = AppShapes.RoundedMD,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AppColors.CyanGlow,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(0.1f),
                            cursorColor = AppColors.CyanGlow
                        ),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { performSearch() })
                    )

                    Spacer(Modifier.height(12.dp))

                    // Languages
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(SUBTITLE_LANGUAGES) { (code, name) ->
                            FilterChip(
                                selected = selectedLang == code,
                                onClick = {
                                    selectedLang = code
                                    // Always do a fresh search for the newly selected language
                                    keyboardController?.hide()
                                    focusManager.clearFocus()
                                    viewModel.search(query.ifBlank { viewModel.state.value.query }, videoPath, code, apiKey)
                                },
                                label = { Text(name, style = MaterialTheme.typography.labelMedium) },
                                shape = AppShapes.RoundedMD,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AppColors.CyanGlow.copy(0.15f),
                                    selectedLabelColor = AppColors.CyanGlow,
                                    containerColor = MaterialTheme.colorScheme.onSurface.copy(0.03f),
                                    labelColor = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                                ),
                                border = null
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(0.05f))
                    Spacer(Modifier.height(16.dp))

                    // Results
                    Box(modifier = Modifier.weight(1f)) {
                        when {
                            state.isLoading -> LoadingState()
                            state.error != null -> {
                                ErrorState(state.error.orEmpty()) { performSearch() }
                            }
                            state.results.isEmpty() -> EmptyState()
                            else -> {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 16.dp)
                                ) {
                                    items(state.results) { sub ->
                                        EnhancedSubtitleCard(
                                            result = sub,
                                            isDownloading = state.downloadingId == sub.id,
                                            onClick = {
                                                viewModel.download(sub, videoTitle, apiKey) { file ->
                                                    if (file != null) onSubtitleSelected(file, selectedLang)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
            }
        }
    }
}

@Composable
private fun EnhancedSubtitleCard(
    result: SubtitleResult,
    isDownloading: Boolean,
    onClick: () -> Unit
) {
    val isMsone = result.provider == "msone"
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isDownloading, onClick = onClick),
        shape = AppShapes.RoundedMD,
        colors = CardDefaults.cardColors(
            containerColor = if (isMsone) AppColors.CyanGlow.copy(0.04f) else MaterialTheme.colorScheme.onSurface.copy(0.03f)
        ),
        border = if (isMsone) androidx.compose.foundation.BorderStroke(1.dp, AppColors.CyanGlow.copy(0.15f)) else null
    ) {
        Row(
            modifier = Modifier.padding(vertical = 18.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Poster / Icon
            Box(
                modifier = Modifier
                    .size(if (!result.posterUrl.isNullOrBlank()) 64.dp else 52.dp)
                    .clip(AppShapes.RoundedMD)
                    .background(MaterialTheme.colorScheme.onSurface.copy(0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (!result.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = result.posterUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        if (isMsone) Icons.Default.Verified else Icons.Default.Subtitles,
                        null,
                        tint = if (isMsone) AppColors.CyanGlow else MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Title Hierarchy
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        result.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    
                    if (isMsone) {
                        Surface(
                            color = AppColors.CyanGlow.copy(0.9f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(Icons.Default.Verified, null, tint = Color.Black, modifier = Modifier.size(10.dp))
                                Text(
                                    "VERIFIED",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp
                                    ),
                                    color = Color.Black
                                )
                            }
                        }
                    }
                }

                if (!result.titleMal.isNullOrBlank()) {
                    Text(
                        result.titleMal,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))
                
                // Metadata Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Year
                    if (result.releaseYear != null) {
                        MetadataTag(result.releaseYear.toString(), Icons.Default.CalendarToday)
                    } else if (!result.uploadedAt.isNullOrBlank() && result.uploadedAt != "Local") {
                        MetadataTag(result.uploadedAt.take(4), Icons.Default.CalendarToday)
                    }
                    
                    // Language
                    MetadataTag(result.language, Icons.Default.Language, tint = AppColors.CyanGlow)
                    
                    // Rating
                    if (!result.rating.isNullOrBlank()) {
                        MetadataTag(result.rating, Icons.Default.Star, tint = Color(0xFFFFC107))
                    }
                    
                    // Provider
                    MetadataTag(result.provider.uppercase(), Icons.Default.Cloud)
                }

                // Footer Info
                if (!result.translator.isNullOrBlank() || !result.releaseType.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.alpha(0.6f)
                    ) {
                        val footerText = buildString {
                            if (!result.translator.isNullOrBlank()) append("By: ${result.translator}")
                            if (!result.releaseType.isNullOrBlank()) {
                                if (isNotEmpty()) append(" • ")
                                append(result.releaseType)
                            }
                        }
                        Text(
                            footerText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Action
            if (isDownloading) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp, color = AppColors.CyanGlow)
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(AppColors.CyanGlow.copy(0.12f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Download, null, tint = AppColors.CyanGlow, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun MetadataTag(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = tint.copy(alpha = 0.7f), modifier = Modifier.size(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = tint
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppColors.CyanGlow, strokeWidth = 3.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                "Searching across providers...", 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SearchOff, 
                null, 
                modifier = Modifier.size(72.dp), 
                tint = MaterialTheme.colorScheme.onSurface.copy(0.05f)
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No subtitles found", 
                style = MaterialTheme.typography.titleMedium, 
                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
            )
            Text(
                "Try a simpler title or different language", 
                style = MaterialTheme.typography.bodySmall, 
                color = MaterialTheme.colorScheme.onSurface.copy(0.3f)
            )
        }
    }
}

@Composable
private fun ErrorState(error: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally, 
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.ErrorOutline, 
                null, 
                modifier = Modifier.size(64.dp), 
                tint = MaterialTheme.colorScheme.error.copy(0.4f)
            )
            Text(
                error, 
                textAlign = TextAlign.Center, 
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
            )
            Button(
                onClick = onRetry, 
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanGlow),
                shape = AppShapes.RoundedMD
            ) {
                Text("Retry Search", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

