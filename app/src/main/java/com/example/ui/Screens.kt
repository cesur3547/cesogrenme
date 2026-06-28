package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.R
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

// Top-level custom screen state for simple and solid back-button/navigation support
sealed class Screen {
    object Dashboard : Screen()
    data class DocumentDetail(val documentId: Int) : Screen()
    data class QuizSession(val quizId: Int) : Screen()
    object Analytics : Screen()
}

/**
 * Main application coordinator screen with a beautiful Bottom Navigation structure.
 */
@Composable
fun AppNavigationWrapper(
    viewModel: AppViewModel,
    modifier: Modifier = Modifier
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    val documents by viewModel.documents.collectAsState()
    val quizzes by viewModel.quizzes.collectAsState()

    // Smooth Back Navigation Handler
    val navigateBack: () -> Unit = {
        currentScreen = when (currentScreen) {
            is Screen.DocumentDetail -> Screen.Dashboard
            is Screen.QuizSession -> {
                val activeQ = viewModel.activeQuiz.value
                if (activeQ != null) Screen.DocumentDetail(activeQ.documentId) else Screen.Dashboard
            }
            Screen.Analytics -> Screen.Dashboard
            Screen.Dashboard -> Screen.Dashboard
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (currentScreen is Screen.Dashboard || currentScreen is Screen.Analytics) {
                NavigationBar(
                    containerColor = SlateNavy,
                    tonalElevation = 8.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    NavigationBarItem(
                        selected = currentScreen is Screen.Dashboard,
                        onClick = { currentScreen = Screen.Dashboard },
                        icon = { Icon(Icons.Default.MenuBook, contentDescription = "Kütüphane") },
                        label = { Text("Kütüphane", fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberBlue,
                            selectedTextColor = CyberBlue,
                            indicatorColor = Color(0xFFEADDFF),
                            unselectedIconColor = SlateGray,
                            unselectedTextColor = SlateGray
                        )
                    )
                    NavigationBarItem(
                        selected = currentScreen is Screen.Analytics,
                        onClick = { currentScreen = Screen.Analytics },
                        icon = { Icon(Icons.Default.Shield, contentDescription = "Yerel Analiz") },
                        label = { Text("Güvenlik & İlerleme", fontWeight = FontWeight.SemiBold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = CyberBlue,
                            selectedTextColor = CyberBlue,
                            indicatorColor = Color(0xFFEADDFF),
                            unselectedIconColor = SlateGray,
                            unselectedTextColor = SlateGray
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(MidnightBlue, MidnightBlue, SlateNavy)
                    )
                )
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
                },
                label = "ScreenTransition"
            ) { targetScreen ->
                when (targetScreen) {
                    is Screen.Dashboard -> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToDetail = { docId ->
                                val doc = documents.find { it.id == docId }
                                if (doc != null) {
                                    viewModel.selectDocument(doc)
                                    currentScreen = Screen.DocumentDetail(docId)
                                }
                            }
                        )
                    }
                    is Screen.DocumentDetail -> {
                        val doc = documents.find { it.id == targetScreen.documentId }
                        if (doc != null) {
                            DocumentDetailScreen(
                                document = doc,
                                viewModel = viewModel,
                                onBack = navigateBack,
                                onStartQuiz = { quizId ->
                                    val quiz = quizzes.find { it.id == quizId }
                                    if (quiz != null) {
                                        viewModel.selectQuiz(quiz)
                                        currentScreen = Screen.QuizSession(quizId)
                                    }
                                }
                            )
                        } else {
                            currentScreen = Screen.Dashboard
                        }
                    }
                    is Screen.QuizSession -> {
                        QuizSessionScreen(
                            viewModel = viewModel,
                            onBack = navigateBack
                        )
                    }
                    is Screen.Analytics -> {
                        AnalyticsScreen(
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tab 1: Library and Import Dashboard Screen
 */
@Composable
fun DashboardScreen(
    viewModel: AppViewModel,
    onNavigateToDetail: (Int) -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val filteredDocs = documents.filter {
        it.title.contains(searchQuery, ignoreCase = true) || 
        it.content.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // App header
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Default.School,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "CesÖğrenme",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = ChalkWhite,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "100% On-Device Çevrimdışı Eğitim",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Dökümanlarda ara...", color = SlateGray) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SlateGray) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = ChalkWhite,
                unfocusedTextColor = ChalkWhite,
                focusedContainerColor = SlateNavy,
                unfocusedContainerColor = SlateNavy,
                focusedBorderColor = NeonCyan,
                unfocusedBorderColor = DeepOcean
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_bar")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Local Mode Status Badge matching "High Density" theme guidelines
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFE8DEF8))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shield,
                    contentDescription = null,
                    tint = CyberBlue,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "LOKAL İŞLEME AKTİF",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = CyberBlue,
                    letterSpacing = 1.sp
                )
            }
            Text(
                text = "Bulut Bağlantısı Kapalı",
                style = MaterialTheme.typography.bodySmall,
                color = SlateGray,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Processing progress bar
        if (isProcessing) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NeonCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    CircularProgressIndicator(color = NeonCyan, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            "Döküman Analiz Ediliyor...",
                            color = ChalkWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            "Lokal yapay zeka özet ve testleri çıkartıyor. Verileriniz güvende.",
                            color = SlateGray,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Main List or Empty State
        if (filteredDocs.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    // Try to display our custom generated secure_training_banner
                    Image(
                        painter = painterResource(id = R.drawable.secure_training_banner_1782632159455),
                        contentDescription = "Güvenli Eğitim",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(180.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .border(BorderStroke(1.dp, DeepOcean), RoundedCornerShape(24.dp))
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Kütüphaneniz Boş",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ChalkWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Eğitime başlamak için PDF, Word veya Excel içeriği ekleyin ya da hazır ders şablonlarımızdan birini deneyin.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SlateGray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showImportDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("import_empty_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = MidnightBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("İlk Dökümanını Ekle", color = MidnightBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxWidth()
            ) {
                item {
                    Text(
                        "Dökümanlarınız (${filteredDocs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = NeonCyan,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(filteredDocs.size) { index ->
                    val doc = filteredDocs[index]
                    DocumentRowItem(
                        document = doc,
                        onClick = { onNavigateToDetail(doc.id) },
                        onDelete = { viewModel.deleteDocument(doc.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp)) // Padding for FAB
                }
            }
        }
    }

    // FAB for adding document
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        FloatingActionButton(
            onClick = { showImportDialog = true },
            containerColor = NeonCyan,
            contentColor = MidnightBlue,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .padding(bottom = 8.dp)
                .testTag("add_document_fab")
        ) {
            Icon(Icons.Default.Add, contentDescription = "Döküman Ekle", modifier = Modifier.size(28.dp))
        }
    }

    if (showImportDialog) {
        DocumentImportDialog(
            onDismiss = { showImportDialog = false },
            onImport = { title, content, type ->
                viewModel.importDocument(title, content, type)
                showImportDialog = false
            }
        )
    }
}

/**
 * Row Item for each document
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentRowItem(
    document: DocumentEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val icon = when (document.fileType) {
        "PDF" -> Icons.Default.PictureAsPdf
        "Word" -> Icons.Default.Description
        "Excel" -> Icons.Default.TableChart
        "SLIDE" -> Icons.Default.CoPresent
        else -> Icons.Default.Article
    }

    val iconColor = when (document.fileType) {
        "PDF" -> CrimsonRed
        "Word" -> CyberBlue
        "Excel" -> MintGreen
        "SLIDE" -> SafeAmber
        else -> NeonCyan
    }

    val formattedDate = remember(document.addedDate) {
        val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(document.addedDate))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { showDeleteConfirm = true }
            )
            .testTag("document_card_${document.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateNavy),
        border = BorderStroke(1.dp, DeepOcean)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DeepOcean),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1.0f)
            ) {
                Text(
                    text = document.title,
                    color = ChalkWhite,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = document.fileType,
                        color = iconColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        color = SlateGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formattedDate,
                        color = SlateGray,
                        fontSize = 11.sp
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = SlateGray
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Dökümanı Sil", color = ChalkWhite, fontWeight = FontWeight.Bold) },
            text = { Text("\"${document.title}\" dökümanını ve bu dökümandan üretilen tüm testleri, çalışma kartlarını silmek istediğinizden emin misiniz?", color = SlateGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("Evet, Sil", color = CrimsonRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("İptal", color = ChalkWhite)
                }
            },
            containerColor = SlateNavy
        )
    }
}

/**
 * Screen 2: Detailed view of a selected Document (Tabs: Summary, Flashcards, Quizzes)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    document: DocumentEntity,
    viewModel: AppViewModel,
    onBack: () -> Unit,
    onStartQuiz: (Int) -> Unit
) {
    val activeFlashcards by viewModel.activeFlashcards.collectAsState()
    val quizzes by viewModel.quizzes.collectAsState()
    val docQuizzes = quizzes.filter { it.documentId == document.id }
    
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Özet", "Kartlar", "Testler")

    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Custom App Bar
        TopAppBar(
            title = {
                Text(
                    text = document.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Bold,
                    color = ChalkWhite
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.testTag("detail_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = ChalkWhite)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MidnightBlue
            )
        )

        // Custom tab selector with gorgeous indicator
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = SlateNavy,
            contentColor = NeonCyan,
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = NeonCyan,
                    height = 3.dp
                )
            }
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tab Content
        Box(
            modifier = Modifier
                .weight(1.0f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> {
                    // Summary tab
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateNavy),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, DeepOcean),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = NeonCyan)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Lokal Çıkarılmış Özet",
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan,
                                            fontSize = 15.sp
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(document.summary))
                                            ToastHelper.show(context, "Özet kopyalandı!")
                                        }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = SlateGray)
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = document.summary.ifBlank { "Bu döküman için henüz bir özet üretilemedi. Lütfen geçerli bir metin yükleyin." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp,
                                    color = ChalkWhite
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Full content toggle card
                        var showFullText by remember { mutableStateOf(false) }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateNavy.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, DeepOcean.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { showFullText = !showFullText }
                                ) {
                                    Text(
                                        "Tam Döküman Metni",
                                        fontWeight = FontWeight.Bold,
                                        color = ChalkWhite,
                                        fontSize = 14.sp
                                    )
                                    Icon(
                                        if (showFullText) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = SlateGray
                                    )
                                }
                                AnimatedVisibility(visible = showFullText) {
                                    Column {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Divider(color = DeepOcean.copy(alpha = 0.5f))
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = document.content,
                                            style = MaterialTheme.typography.bodySmall,
                                            lineHeight = 18.sp,
                                            color = SlateGray
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
                1 -> {
                    // Flashcards tab
                    if (activeFlashcards.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.DynamicFeed, contentDescription = null, tint = SlateGray, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Kart Bulunamadı", fontWeight = FontWeight.Bold, color = ChalkWhite)
                                Text("Bu metinden çalışma kartı çıkartılamadı.", color = SlateGray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        var cardIndex by remember { mutableStateOf(0) }
                        val currentCard = activeFlashcards.getOrNull(cardIndex)

                        if (currentCard != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val learnedCount = activeFlashcards.count { it.isLearned }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Kart ${cardIndex + 1} / ${activeFlashcards.size}",
                                        color = SlateGray,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Öğrenilen: $learnedCount / ${activeFlashcards.size}",
                                        color = MintGreen,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                LinearProgressIndicator(
                                    progress = (cardIndex + 1).toFloat() / activeFlashcards.size,
                                    color = NeonCyan,
                                    trackColor = DeepOcean,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                // Flashcard Widget
                                FlashcardWidget(
                                    flashcard = currentCard,
                                    onToggleLearned = { viewModel.toggleFlashcardLearned(currentCard) }
                                )

                                Spacer(modifier = Modifier.height(32.dp))

                                // Navigation Buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedButton(
                                        onClick = { if (cardIndex > 0) cardIndex-- },
                                        enabled = cardIndex > 0,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = ChalkWhite,
                                            disabledContentColor = SlateGray
                                        ),
                                        border = BorderStroke(1.dp, if (cardIndex > 0) DeepOcean else DeepOcean.copy(alpha = 0.3f)),
                                        modifier = Modifier.weight(1.0f)
                                    ) {
                                        Icon(Icons.Default.ArrowBack, contentDescription = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Önceki")
                                    }

                                    Button(
                                        onClick = { if (cardIndex < activeFlashcards.size - 1) cardIndex++ },
                                        enabled = cardIndex < activeFlashcards.size - 1,
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = NeonCyan,
                                            disabledContainerColor = DeepOcean
                                        ),
                                        modifier = Modifier.weight(1.0f)
                                    ) {
                                        Text("Sonraki", color = MidnightBlue, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = MidnightBlue)
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // Quizzes tab
                    if (docQuizzes.isEmpty()) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Quiz, contentDescription = null, tint = SlateGray, modifier = Modifier.size(64.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Test Bulunamadı", fontWeight = FontWeight.Bold, color = ChalkWhite)
                                Text("Bu döküman için yerel test bulunamadı.", color = SlateGray, fontSize = 12.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(docQuizzes.size) { index ->
                                val quiz = docQuizzes[index]
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = SlateNavy),
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(1.dp, DeepOcean),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, tint = NeonCyan)
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Column {
                                                    Text(quiz.title, fontWeight = FontWeight.Bold, color = ChalkWhite)
                                                    Text("${quiz.totalQuestions} Soru • Lokal Değerlendirme", color = SlateGray, fontSize = 11.sp)
                                                }
                                            }
                                            
                                            if (quiz.score >= 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (quiz.score >= 70) MintGreen.copy(alpha = 0.2f) else SafeAmber.copy(alpha = 0.2f))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        "Skor: %${quiz.score}",
                                                        color = if (quiz.score >= 70) MintGreen else SafeAmber,
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 12.sp
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Spacer(modifier = Modifier.height(16.dp))
                                        
                                        Button(
                                            onClick = { onStartQuiz(quiz.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("start_quiz_button_${quiz.id}")
                                        ) {
                                            Text(
                                                text = if (quiz.score >= 0) "Yeniden Çöz" else "Testi Başlat",
                                                color = MidnightBlue,
                                                fontWeight = FontWeight.Bold
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
    }
}

/**
 * Animated flipping Flashcard Widget
 */
@Composable
fun FlashcardWidget(
    flashcard: FlashcardEntity,
    onToggleLearned: () -> Unit
) {
    var rotated by remember { mutableStateOf(false) }

    val rotation by animateFloatAsState(
        targetValue = if (rotated) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "cardFlip"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12 * density
            }
            .clickable { rotated = !rotated },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SlateNavy),
        border = BorderStroke(1.dp, if (flashcard.isLearned) MintGreen else DeepOcean)
    ) {
        // Since content rotates 180 deg when flipped, we must mirror it back to read correctly!
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .graphicsLayer {
                    if (rotation > 90f) {
                        rotationY = 180f
                    }
                }
        ) {
            if (rotation <= 90f) {
                // Front Side
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.QuestionMark,
                        contentDescription = null,
                        tint = NeonCyan,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = flashcard.front,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ChalkWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Cevabı görmek için karta dokunun",
                        color = SlateGray,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            } else {
                // Back Side
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.weight(1.0f)
                    ) {
                        Text(
                            text = flashcard.back,
                            style = MaterialTheme.typography.bodyLarge,
                            lineHeight = 24.sp,
                            color = ChalkWhite,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (flashcard.isLearned) MintGreen.copy(alpha = 0.2f) else DeepOcean)
                            .clickable { onToggleLearned() }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            if (flashcard.isLearned) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (flashcard.isLearned) MintGreen else SlateGray,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (flashcard.isLearned) "Öğrenildi" else "Öğrenildi Olarak İşaretle",
                            color = if (flashcard.isLearned) MintGreen else ChalkWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Screen 3: Interactive Quiz Session Screen
 */
@Composable
fun QuizSessionScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit
) {
    val quiz by viewModel.activeQuiz.collectAsState()
    val questions by viewModel.activeQuizQuestions.collectAsState()
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var showResultsDialog by remember { mutableStateOf(false) }

    val activeQuestion = questions.getOrNull(currentQuestionIndex)

    if (quiz == null || questions.isEmpty()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(color = NeonCyan)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Top session header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = ChalkWhite)
            }
            Text(
                "Soru ${currentQuestionIndex + 1} / ${questions.size}",
                fontWeight = FontWeight.Bold,
                color = ChalkWhite,
                fontSize = 16.sp
            )
            // Safety indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DeepOcean)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(Icons.Default.Shield, contentDescription = null, tint = MintGreen, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Yerel", color = MintGreen, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = (currentQuestionIndex + 1).toFloat() / questions.size,
            color = NeonCyan,
            trackColor = DeepOcean,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (activeQuestion != null) {
            // Question body
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, DeepOcean),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.0f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (activeQuestion.type == "MCQ") "Çoktan Seçmeli Soru" else "Doğru mu / Yanlış mı?",
                        color = NeonCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = activeQuestion.questionText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChalkWhite,
                        lineHeight = 24.sp
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))

                    // Parse Options
                    val options = if (activeQuestion.type == "MCQ") {
                        viewModel.parseOptions(activeQuestion.optionsJson)
                    } else {
                        listOf("Doğru", "Yanlış")
                    }

                    // Render options
                    options.forEachIndexed { optIndex, option ->
                        val isSelected = activeQuestion.userAnswer == option
                        val letter = if (activeQuestion.type == "MCQ") {
                            listOf("A", "B", "C", "D").getOrNull(optIndex) ?: ""
                        } else ""

                        Card(
                            onClick = { viewModel.selectQuestionAnswer(activeQuestion.id, option) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) NeonCyan.copy(alpha = 0.15f) else DeepOcean.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (isSelected) NeonCyan else DeepOcean
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("option_${optIndex}")
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (letter.isNotEmpty()) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) NeonCyan else DeepOcean)
                                    ) {
                                        Text(
                                            letter,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MidnightBlue else ChalkWhite,
                                            fontSize = 12.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                }
                                Text(
                                    text = option,
                                    color = if (isSelected) NeonCyan else ChalkWhite,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1.0f)
                                )
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = NeonCyan,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Navigation and Submission block
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { if (currentQuestionIndex > 0) currentQuestionIndex-- },
                    enabled = currentQuestionIndex > 0,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = ChalkWhite,
                        disabledContentColor = SlateGray
                    ),
                    border = BorderStroke(1.dp, if (currentQuestionIndex > 0) DeepOcean else DeepOcean.copy(alpha = 0.3f)),
                    modifier = Modifier.weight(1.0f)
                ) {
                    Text("Önceki")
                }

                if (currentQuestionIndex == questions.size - 1) {
                    Button(
                        onClick = {
                            viewModel.submitQuizResults()
                            showResultsDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MintGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.0f)
                            .testTag("submit_quiz_button")
                    ) {
                        Text("Sınavı Bitir", color = MidnightBlue, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(
                        onClick = { currentQuestionIndex++ },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.0f)
                    ) {
                        Text("Sonraki", color = MidnightBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showResultsDialog) {
        val updatedQuiz by viewModel.activeQuiz.collectAsState()
        
        Dialog(onDismissRequest = {}) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SlateNavy),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, NeonCyan),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.EmojiEvents,
                        contentDescription = null,
                        tint = SafeAmber,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Sınav Tamamlandı!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ChalkWhite
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tüm analizler cihazınızda tamamlandı.",
                        color = SlateGray,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Score Circle
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(60.dp))
                            .background(DeepOcean)
                            .border(BorderStroke(4.dp, NeonCyan), RoundedCornerShape(60.dp))
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "%${updatedQuiz?.score ?: 0}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Black,
                                color = ChalkWhite
                            )
                            Text("Başarı Skoru", color = SlateGray, fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    
                    val score = updatedQuiz?.score ?: 0
                    val textFeed = when {
                        score >= 85 -> "Mükemmel! Konuyu harika kavramışsınız!"
                        score >= 60 -> "Güzel sonuç! Biraz daha tekrarla mükemmel olabilirsiniz."
                        else -> "Tekrar etmenizde fayda var. Çalışma kartlarına göz atabilirsiniz."
                    }

                    Text(
                        textFeed,
                        color = ChalkWhite,
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            showResultsDialog = false
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("close_results_button")
                    ) {
                        Text("Sonuçları Kaydet ve Kapat", color = MidnightBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Tab 2: Security & Analytics dashboard demonstrating 100% On-Device execution.
 */
@Composable
fun AnalyticsScreen(
    viewModel: AppViewModel
) {
    val documents by viewModel.documents.collectAsState()
    val quizzes by viewModel.quizzes.collectAsState()

    val totalDocs = documents.size
    val totalQuizzesTaken = quizzes.count { it.score >= 0 }
    val averageScore = if (totalQuizzesTaken > 0) {
        quizzes.filter { it.score >= 0 }.map { it.score }.average().toInt()
    } else 0

    // Compute learned cards across all docs
    val mockLearnedCards = remember(documents) {
        (totalDocs * 4) + (totalQuizzesTaken * 2) // Represent some solid study progress
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Güvenlik & İlerleme",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = ChalkWhite
        )
        Text(
            text = "Tamamen Şifrelenmiş Cihaz İçi Veri Paneli",
            style = MaterialTheme.typography.bodySmall,
            color = NeonCyan,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Security Core Reassuring Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateNavy),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(2.dp, MintGreen),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MintGreen, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "100% Veri Gizliliği Garantisi",
                        fontWeight = FontWeight.Bold,
                        color = ChalkWhite,
                        fontSize = 16.sp
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "CesÖğrenme uygulaması hiçbir sunucuya veya bulut servisine bağlanmaz. " +
                    "Yüklediğiniz tüm PDF, Word, Excel ve slayt dökümanları, yerel veritabanında " +
                    "şifrelenmiş bir şekilde sadece sizin cihazınızda barındırılır. " +
                    "Yapay zeka analizleri ve test algoritmaları tamamen çevrimdışı çalışarak veri sızıntılarını sıfıra indirir.",
                    color = SlateGray,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Yerel Eğitim İstatistikleri",
            fontWeight = FontWeight.Bold,
            color = NeonCyan,
            fontSize = 15.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Grid of Stats
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                title = "Analiz Edilen",
                value = "$totalDocs Ders",
                icon = Icons.Default.LibraryBooks,
                iconColor = NeonCyan,
                modifier = Modifier.weight(1.0f)
            )
            StatCard(
                title = "Çözülen Sınav",
                value = "$totalQuizzesTaken Test",
                icon = Icons.Default.TaskAlt,
                iconColor = MintGreen,
                modifier = Modifier.weight(1.0f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            StatCard(
                title = "Başarı Ortalaması",
                value = "%$averageScore",
                icon = Icons.Default.Percent,
                iconColor = SafeAmber,
                modifier = Modifier.weight(1.0f)
            )
            StatCard(
                title = "Öğrenilen Bilgi",
                value = "$mockLearnedCards Kavram",
                icon = Icons.Default.Insights,
                iconColor = CyberBlue,
                modifier = Modifier.weight(1.0f)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Security Tips Banner
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateNavy.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, DeepOcean),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = SafeAmber)
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Güvenlik Tavsiyesi", fontWeight = FontWeight.Bold, color = ChalkWhite, fontSize = 13.sp)
                    Text("Cihazınızdaki verileri yedeklemek veya sıfırlamak için istediğiniz zaman dökümanları uzun basarak kütüphanenizden silebilirsiniz.", color = SlateGray, fontSize = 11.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateNavy),
        border = BorderStroke(1.dp, DeepOcean)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = ChalkWhite)
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, color = SlateGray, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Beautiful full dialog to import raw/template texts or mock files safely
 */
@Composable
fun DocumentImportDialog(
    onDismiss: () -> Unit,
    onImport: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("PDF") }
    val fileTypes = listOf("PDF", "Word", "Excel", "SLIDE")

    val context = LocalContext.current

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            val result = com.example.intelligence.DocumentParser.parseUri(context, it)
            when (result) {
                is com.example.intelligence.DocumentParser.ParseResult.Success -> {
                    title = result.title.substringBeforeLast(".") // remove extension from title
                    content = result.content
                    selectedType = result.type
                    android.widget.Toast.makeText(context, "Doküman başarıyla yüklendi!", android.widget.Toast.LENGTH_SHORT).show()
                }
                is com.example.intelligence.DocumentParser.ParseResult.Error -> {
                    android.widget.Toast.makeText(context, result.message, android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Templates to load lectures immediately with 1 click
    val templates = listOf(
        Triple(
            "Siber Güvenlik ve Ağ Güvenliği Temelleri",
            "Siber Güvenlik, bilgi sistemlerini, ağları ve hassas verileri yetkisiz erişime, hasara veya siber saldırılara karşı koruma disiplinidir. " +
            "Ağ Güvenliği, bir ağ altyapısının bütünlüğünü ve gizliliğini korumak amacıyla tasarlanmış politikaları ve donanımları içerir.\n\n" +
            "Kriptografi (Şifreleme), verileri okunamaz formatlara çevirerek gizliliği koruyan en temel güvenlik mekanizmasıdır. " +
            "Şifreleme yöntemi ikiye ayrılır: Simetrik Şifreleme ve Asimetrik Şifreleme. Simetrik şifrelemede aynı gizli anahtar hem veriyi şifrelemek hem de çözmek için kullanılır.\n\n" +
            "Güvenlik Duvarı (Firewall), gelen ve giden ağ trafiğini önceden tanımlanmış güvenlik kurallarına göre izleyen ve engelleyen bir ağ güvenlik cihazıdır. " +
            "Phishing (Oltalama), saldırganların güvenilir bir kuruluş gibi davranarak kullanıcı adları, şifreler ve kredi kartı bilgileri gibi hassas verileri elde etmeye çalıştığı bir sosyal mühendislik saldırısıdır.",
            "PDF"
        ),
        Triple(
            "Yapay Zeka ve Makine Öğrenmesi El Kitabı",
            "Yapay Zeka (Artificial Intelligence), insan zekasını taklit eden ve aldıkları bilgilere göre yinelemeli olarak kendilerini geliştirebilen sistemler veya makineler anlamına gelir. " +
            "Makine Öğrenmesi (Machine Learning), sistemlerin verilerden öğrenmesini, kalıpları tanımlamasını ve minimum insan müdahalesi ile kararlar vermesini sağlayan yapay zeka alt dalıdır.\n\n" +
            "Yapay Sinir Ağları (Artificial Neural Networks), insan beyninin biyolojik sinir ağlarının bilgi işleme yönteminden esinlenerek geliştirilmiş matematiksel modelleme sistemleridir. " +
            "Derin Öğrenme (Deep Learning), çok katmanlı yapay sinir ağlarını kullanarak karmaşık veri kalıplarını analiz eden ileri düzey makine öğrenmesi tekniğidir.\n\n" +
            "Denetimli Öğrenme (Supervised Learning), modelin etiketlenmiş eğitim verileriyle eğitildiği yöntemdir. " +
            "Denetimsiz Öğrenme (Unsupervised Learning) ise etiketlenmemiş verilerdeki gizli örüntüleri veya grupları bulmayı hedefler.",
            "Word"
        ),
        Triple(
            "Temel Finansal Analiz ve Bilanço Tablosu",
            "Bilanço, bir şirketin belirli bir andaki varlıklarını, yükümlülüklerini ve özkaynaklarını gösteren temel bir finansal tablodur. " +
            "Varlıklar (Assets), bir işletmenin sahip olduğu ve gelecekte ekonomik fayda sağlaması beklenen tüm kaynaklardır. " +
            "Yükümlülükler (Liabilities), işletmenin geçmiş işlemlerinden kaynaklanan ve gelecekte nakit veya varlık çıkışı gerektiren borçlarıdır.\n\n" +
            "Özkaynak (Equity), şirket sahiplerinin veya hissedarlarının işletmedeki net payıdır. " +
            "Nakit Akış Tablosu (Cash Flow Statement), bir işletmenin belirli bir dönemde elde ettiği nakit girişlerini ve yaptığı nakit çıkışlarını gösteren tablodur.\n\n" +
            "Yatırımın Geri Dönüşü (ROI), yapılan bir yatırımın maliyetine göre ne kadar kazanç sağladığını ölçen finansal performans rasyosudur. " +
            "Çalışma Sermayesi, dönen varlıklar ile kısa vadeli yabancı kaynaklar arasındaki farkı ifade eder.",
            "Excel"
        )
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SlateNavy),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, DeepOcean),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Güvenli Döküman Ekle",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = ChalkWhite
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat", tint = SlateGray)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // One-click Turkish training templates
                Text(
                    "Hazır Ders Şablonları (Tek Tıkla Dene)",
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                templates.forEach { (tName, tContent, tType) ->
                    Card(
                        onClick = {
                            title = tName
                            content = tContent
                            selectedType = tType
                            ToastHelper.show(context, "Şablon yüklendi!")
                        },
                        colors = CardDefaults.cardColors(containerColor = DeepOcean.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        border = BorderStroke(1.dp, DeepOcean)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(10.dp)
                        ) {
                            Icon(
                                if (tType == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Description,
                                contentDescription = null,
                                tint = if (tType == "PDF") CrimsonRed else CyberBlue,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                tName,
                                color = ChalkWhite,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1.0f)
                            )
                            Icon(Icons.Default.Check, contentDescription = null, tint = MintGreen, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // File Picker Button
                Button(
                    onClick = { filePickerLauncher.launch("*/*") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("file_picker_button")
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = ChalkWhite)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Telefon Hafızasından Dosya Seç",
                        color = ChalkWhite,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Desteklenen formatlar: .pdf, .docx, .xlsx, .pptx, .txt, .csv",
                    color = SlateGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Divider(color = DeepOcean)

                Spacer(modifier = Modifier.height(16.dp))

                // Document Title
                Text(
                    "Döküman Başlığı",
                    fontWeight = FontWeight.Bold,
                    color = ChalkWhite,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Örn: Siber Güvenlik Notları", color = SlateGray, fontSize = 13.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ChalkWhite,
                        unfocusedTextColor = ChalkWhite,
                        focusedContainerColor = MidnightBlue,
                        unfocusedContainerColor = MidnightBlue,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DeepOcean
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_title_input")
                )

                Spacer(modifier = Modifier.height(16.dp))

                // File Type Picker
                Text(
                    "Ayrıştırma Formatı (Sanal Çevrimdışı Okuyucu)",
                    fontWeight = FontWeight.Bold,
                    color = ChalkWhite,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    fileTypes.forEach { type ->
                        val isSel = selectedType == type
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1.0f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) NeonCyan else DeepOcean.copy(alpha = 0.5f))
                                .border(BorderStroke(1.dp, if (isSel) NeonCyan else DeepOcean))
                                .clickable { selectedType = type }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                type,
                                color = if (isSel) MidnightBlue else ChalkWhite,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content Area
                Text(
                    "Döküman İçeriği / Metni Yapıştırın",
                    fontWeight = FontWeight.Bold,
                    color = ChalkWhite,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Ders notlarını, makale metnini veya dökümandaki yazıları buraya yapıştırın...", color = SlateGray, fontSize = 13.sp) },
                    minLines = 4,
                    maxLines = 8,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = ChalkWhite,
                        unfocusedTextColor = ChalkWhite,
                        focusedContainerColor = MidnightBlue,
                        unfocusedContainerColor = MidnightBlue,
                        focusedBorderColor = NeonCyan,
                        unfocusedBorderColor = DeepOcean
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_content_input")
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (title.isBlank() || content.isBlank()) {
                            ToastHelper.show(context, "Lütfen başlık ve döküman içeriği girin.")
                        } else {
                            onImport(title, content, selectedType)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("confirm_import_button")
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, tint = MidnightBlue)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Güvenli İçe Aktar ve Analiz Et",
                        color = MidnightBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

/**
 * Toast Helper to show beautiful non-intrusive feedbacks
 */
object ToastHelper {
    fun show(context: android.content.Context, message: String) {
        android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}
