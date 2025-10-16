package com.totokode.detektea

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.totokode.detektea.db.AppDatabase
import com.totokode.detektea.db.DetectionResult
import com.totokode.detektea.ui.theme.DetailHistoryScreen
import com.totokode.detektea.ui.theme.DetekTeaTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private var detectionHelper: ONNXDetectionHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize ONNX detection helper with error handling
        try {
            detectionHelper = ONNXDetectionHelper(this)
            Log.d("DetekTea", "ONNXDetectionHelper initialized successfully")
        } catch (e: Exception) {
            Log.e("DetekTea", "Error initializing ONNXDetectionHelper: ${e.message}", e)
            Toast.makeText(this, "Error initializing detection model: ${e.message}", Toast.LENGTH_LONG).show()
        }

        setContent {
            DetekTeaTheme {
                DetekTeaApp(detectionHelper)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            detectionHelper?.close()
        } catch (e: Exception) {
            Log.e("DetekTea", "Error closing detection helper: ${e.message}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetekTeaApp(detectionHelper: ONNXDetectionHelper?) {
    var currentScreen by remember { mutableStateOf("get_started") }
    var detectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var detectionResults by remember { mutableStateOf<List<DetectionBox>?>(null) }
    // State baru untuk menyimpan item riwayat yang dipilih
    var selectedHistoryItem by remember { mutableStateOf<DetectionResult?>(null) }

    when (currentScreen) {
        "get_started" -> GetStartedScreen { currentScreen = "menu" }
        "menu" -> MenuScreen(
            onDetectionClick = {
                if (detectionHelper != null) {
                    currentScreen = "detection"
                } else {
                    // Show error if detection helper not initialized
                }
            },
            onHistoryClick = { currentScreen = "history" },
            onAboutClick = { currentScreen = "about" }
        )
        "detection" -> {
            if (detectionHelper != null) {
                DetectionScreen(
                    detectionHelper = detectionHelper,
                    onBack = { currentScreen = "menu" },
                    onDetectionComplete = { bitmap, results ->
                        detectedImage = bitmap
                        detectionResults = results
                        currentScreen = "result"
                    }
                )
            } else {
                ErrorScreen(
                    message = "Detection model not initialized",
                    onBack = { currentScreen = "menu" }
                )
            }
        }
        "history" -> HistoryScreen(
            onBack = { currentScreen = "menu" },
            onItemClick = { result ->
                selectedHistoryItem = result
                currentScreen = "history_detail"
            }
        )
        "about" -> AboutScreen { currentScreen = "menu" }
        "result" -> ResultScreen(
            detectedImage = detectedImage,
            detectionResults = detectionResults,
            onBack = { currentScreen = "menu" },
            onSaveSuccess = { currentScreen = "history" }
        )
        "history_detail" -> {
            selectedHistoryItem?.let { result ->
                DetailHistoryScreen(
                    detectionResult = result,
                    onBack = { currentScreen = "history" }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorScreen(message: String, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Error", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFD32F2F)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = "Error",
                modifier = Modifier.size(80.dp),
                tint = Color(0xFFD32F2F)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Terjadi Kesalahan",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFD32F2F)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                )
            ) {
                Text("Kembali ke Menu")
            }
        }
    }
}

@Composable
fun GetStartedScreen(onGetStarted: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF2E7D32),
                        Color(0xFF4CAF50)
                    )
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .background(Color.White.copy(alpha = 0.2f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocalFlorist,
                contentDescription = "DetekTea Logo",
                modifier = Modifier.size(60.dp),
                tint = Color.White
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = "DetekTea",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Deteksi Penyakit Tanaman Teh",
            fontSize = 16.sp,
            color = Color.White.copy(alpha = 0.8f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(48.dp))
        Text(
            text = "Aplikasi untuk mendeteksi Daun Tanaman Teh yang terkena infeksi penyakit Cacar daun atau Blister Blight dengan menggunakan teknologi model YOLOv8",
            fontSize = 14.sp,
            color = Color.White.copy(alpha = 0.9f),
            textAlign = TextAlign.Center,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(64.dp))
        Button(
            onClick = onGetStarted,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF2E7D32)
            ),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text(
                text = "Mulai",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MenuScreen(
    onDetectionClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DetekTea",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E7D32)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Selamat Datang!",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = "Di Aplikasi DetekTea",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item {
                MenuCard(
                    title = "Deteksi Penyakit",
                    description = "Ambil foto daun teh untuk mendeteksi penyakit dengan model YOLOv8",
                    icon = Icons.Default.PhotoCamera,
                    backgroundColor = Color(0xFF4CAF50),
                    onClick = onDetectionClick
                )
            }
            item {
                MenuCard(
                    title = "Riwayat Penanganan",
                    description = "Lihat riwayat hasil deteksi dan penanganan sebelumnya",
                    icon = Icons.Default.Schedule,
                    backgroundColor = Color(0xFF2196F3),
                    onClick = onHistoryClick
                )
            }
            item {
                MenuCard(
                    title = "Tentang Aplikasi",
                    description = "Informasi tentang aplikasi DetekTea",
                    icon = Icons.Default.Info,
                    backgroundColor = Color(0xFFFF9800),
                    onClick = onAboutClick
                )
            }
        }
    }
}

@Composable
fun MenuCard(
    title: String,
    description: String,
    icon: ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .background(backgroundColor.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    modifier = Modifier.size(30.dp),
                    tint = backgroundColor
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF2E7D32)
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 18.sp
                )
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Arrow",
                tint = Color.Gray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectionScreen(
    detectionHelper: ONNXDetectionHelper,
    onBack: () -> Unit,
    onDetectionComplete: (Bitmap, List<DetectionBox>) -> Unit
) {
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }

    val imageFile = remember {
        File(context.externalCacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        imageFile
    )

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success) {
                isProcessing = true
                try {
                    val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    if (bitmap != null) {
                        Toast.makeText(context, "Memproses gambar...", Toast.LENGTH_SHORT).show()

                        try {
                            val rawResults = detectionHelper.detectObjects(bitmap)

                            // --- LOGIKA PENTING: Tambahkan filter berdasarkan kepercayaan (confidence) ---
                            // Hanya ambil hasil yang kepercayaan (confidence) nya di atas 0.45 (45%)
                            // Kamu bisa sesuaikan angka 0.45 ini sesuai kebutuhanmu.
                            val filteredResults = rawResults.filter { it.confidence > 0.45 }

                            // Gambarkan bounding box hanya pada hasil yang sudah difilter
                            val bitmapWithBoxes = drawBoundingBoxes(bitmap, filteredResults)
                            onDetectionComplete(bitmapWithBoxes, filteredResults)
                        } catch (e: Exception) {
                            Log.e("DetekTea", "Error during detection: ${e.message}", e)
                            Toast.makeText(context, "Error saat deteksi: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Toast.makeText(context, "Gagal memuat gambar.", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("DetekTea", "Error processing image: ${e.message}", e)
                    Toast.makeText(context, "Error memproses gambar: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    isProcessing = false
                }
            } else {
                Toast.makeText(context, "Pengambilan gambar dibatalkan.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                cameraLauncher.launch(uri)
            } else {
                Toast.makeText(context, "Izin kamera ditolak.", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Deteksi Penyakit", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E7D32)
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(80.dp),
                    color = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Memproses gambar...",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Mohon tunggu sebentar",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PhotoCamera,
                    contentDescription = "Camera",
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF4CAF50)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Ambil Foto Daun Teh",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Posisikan kamera pada daun teh yang ingin dideteksi. Pastikan pencahayaan cukup untuk deteksi yang akurat.",
                    fontSize = 16.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                )
                Spacer(modifier = Modifier.height(32.dp))
                Button(
                    onClick = { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buka Kamera")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    detectedImage: Bitmap?,
    detectionResults: List<DetectionBox>?,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val database = remember { AppDatabase.getDatabase(context) }
    val detectionDao = database.detectionDao()

    // --- LOGIKA PENTING: Filter hasil deteksi hanya untuk objek "cacar daun" ---
    val filteredResults = detectionResults?.filter { it.className == "cacar daun" } ?: emptyList()
    val isCacarDaunDetected = filteredResults.isNotEmpty()
    val primaryDetection = filteredResults.maxByOrNull { it.confidence }

    val handlingGuide = """
        **1. Sanitasi dan Pemangkasan:**
        Singkirkan dan musnahkan daun atau cabang yang terinfeksi. Pemangkasan juga dapat meningkatkan sirkulasi udara dan mengurangi kelembaban.

        **2. Pengendalian Biologis:**
        Gunakan agen biokontrol seperti jamur parasit (Trichoderma spp.) yang dapat mengendalikan jamur penyebab cacar daun.

        **3. Penggunaan Fungisida:**
        Gunakan fungisida yang direkomendasikan seperti yang berbahan aktif tembaga (copper) atau fungisida sistemik. Lakukan penyemprotan sesuai dosis dan jadwal yang dianjurkan.

        **4. Peningkatan Kesehatan Tanaman:**
        Pastikan tanaman mendapatkan nutrisi yang cukup dan kondisi tanah yang baik agar lebih tahan terhadap penyakit.
    """.trimIndent()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hasil Deteksi", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E7D32)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                if (detectedImage != null) {
                    Image(
                        bitmap = detectedImage.asImageBitmap(),
                        contentDescription = "Detected Image",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Hasil Deteksi:",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (!isCacarDaunDetected) { // Jika tidak ada deteksi "cacar daun"
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "No Detection",
                                    tint = Color(0xFFD32F2F),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Tidak ada cacar daun terdeteksi",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD32F2F)
                                )
                                Text(
                                    text = "Arahkan kamera ke daun teh yang terinfeksi. Pastikan foto cukup jelas dan pencahayaan baik.",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else { // Jika ada deteksi "cacar daun"
                        // Card ringkasan hasil deteksi
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Deteksi Berhasil",
                                        tint = Color(0xFF4CAF50),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Penyakit Terdeteksi:",
                                            fontSize = 16.sp,
                                            color = Color.Gray
                                        )
                                        Text(
                                            text = primaryDetection?.className ?: "Tidak terdeteksi",
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "Kepercayaan: ${"%.1f".format((primaryDetection?.confidence ?: 0f) * 100)}%",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ditemukan **${filteredResults.size}** objek penyakit.",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Panduan Penanganan di ResultScreen
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F7FA))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MedicalServices,
                                        contentDescription = "Panduan Penanganan",
                                        tint = Color(0xFF00796B),
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = "Penanganan Cacar Daun",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF00796B)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = handlingGuide,
                                    fontSize = 14.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    try {
                                        val imagePath = saveImageToInternalStorage(context, detectedImage)

                                        val detectionResult = DetectionResult(
                                            imagePath = imagePath,
                                            detectedObjectCount = filteredResults.size,
                                            primaryDetectionName = primaryDetection?.className ?: "Tidak terdeteksi",
                                            primaryDetectionConfidence = primaryDetection?.confidence ?: 0f,
                                            boundingBoxesJson = Gson().toJson(filteredResults)
                                        )
                                        detectionDao.insertDetection(detectionResult)
                                        Toast.makeText(context, "Hasil deteksi berhasil disimpan!", Toast.LENGTH_SHORT).show()
                                        onSaveSuccess()
                                    } catch (e: Exception) {
                                        Log.e("DetekTea", "Error saving result: ${e.message}", e)
                                        Toast.makeText(context, "Error menyimpan hasil: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF4CAF50)
                            )
                        ) {
                            Icon(Icons.Default.Save, contentDescription = "Simpan")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simpan Hasil")
                        }
                    }
                } else {
                    Text("Tidak ada gambar yang dideteksi.")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onBack: () -> Unit, onItemClick: (DetectionResult) -> Unit) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val detectionDao = database.detectionDao()
    val history by detectionDao.getAllDetections().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Riwayat Penanganan", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E7D32)
                )
            )
        }
    ) { paddingValues ->
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = "History",
                    modifier = Modifier.size(80.dp),
                    tint = Color(0xFF2196F3)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Belum Ada Riwayat",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Lakukan deteksi penyakit terlebih dahulu untuk melihat riwayat penanganan",
                    fontSize = 14.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(history) { result ->
                    HistoryItemCard(
                        result = result,
                        modifier = Modifier.clickable { onItemClick(result) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItemCard(result: DetectionResult, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text(
                text = "Deteksi pada: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(result.timestamp)}",
                fontSize = 12.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Science,
                    contentDescription = "Result Icon",
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = "Penyakit Terdeteksi:",
                        fontSize = 16.sp,
                        color = Color.Gray
                    )
                    Text(
                        text = result.primaryDetectionName,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "Kepercayaan: ${"%.2f".format(result.primaryDetectionConfidence * 100)}%",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    if (result.detectedObjectCount > 0) {
                        Text(
                            text = "Objek terdeteksi: ${result.detectedObjectCount}",
                            fontSize = 12.sp,
                            color = Color(0xFF2196F3)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tentang Aplikasi", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF2E7D32)
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.LocalFlorist,
                        contentDescription = "App Icon",
                        modifier = Modifier.size(80.dp),
                        tint = Color(0xFF4CAF50)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "DetekTea",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF2E7D32)
                    )
                    Text(
                        text = "Versi 1.0.0 - ONNX Edition",
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Tentang DetekTea",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "DetekTea adalah aplikasi untuk mendeteksi penyakit pada daun tanaman teh menggunakan teknologi deteksi objek yaitu YOLO. Aplikasi ini dibuat untuk membantu petani teh dalam mendeteksi daun tanaman teh yang terkena penyakit cacar daun atau blister blight secara cepat dan akurat",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Teknologi yang Digunakan",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• ONNX\n" +
                                    "• YOLOv8\n" +
                                    "• Bounding box visualization\n" +
                                    "• Non-Maximum Suppression (NMS)\n" +
                                    "• Room Database" +
                                    "• Image processing",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Fitur Utama",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• Deteksi penyakit dengan bounding box\n" +
                                    "• Analisis menggunakan ONNX AI\n" +
                                    "• Multiple object detection\n" +
                                    "• Confidence score untuk setiap deteksi\n" +
                                    "• Riwayat deteksi dan penanganan\n" +
                                    "• Visualisasi area terdeteksi",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Pengembang",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Dikembangkan oleh Toto Rohimin\n" +
                                    "© 2025 DetekTea. All rights reserved.\n" +
                                    "Powered by ONNX Runtime & YOLOv8",
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper function to draw bounding boxes on bitmap
 */
private fun drawBoundingBoxes(originalBitmap: Bitmap, detections: List<DetectionBox>): Bitmap {
    val mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(mutableBitmap)

    // Different colors for different classes
    val colors = listOf(
        android.graphics.Color.RED,
        android.graphics.Color.GREEN,
        android.graphics.Color.BLUE,
        android.graphics.Color.YELLOW,
        android.graphics.Color.CYAN,
        android.graphics.Color.MAGENTA
    )

    val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
        textSize = 40f
        isAntiAlias = true
    }

    val textPaint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 32f
        isAntiAlias = true
        setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
    }

    detections.forEachIndexed { index, detection ->
        // Use different color for each class
        paint.color = colors[detection.classId % colors.size]

        // Draw bounding box
        val rect = RectF(detection.x1, detection.y1, detection.x2, detection.y2)
        canvas.drawRect(rect, paint)

        // Draw label with confidence
        val label = "${detection.className} (${(detection.confidence * 100).toInt()}%)"
        val textX = detection.x1
        val textY = detection.y1 - 10f

        canvas.drawText(label, textX, textY, textPaint)
    }

    return mutableBitmap
}

/**
 * Helper function to save a Bitmap to internal storage and return its file path.
 */
private fun saveImageToInternalStorage(context: Context, bitmap: Bitmap): String {
    val file = File(context.filesDir, "detektea_image_${System.currentTimeMillis()}.jpg")
    try {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
        }
    } catch (e: Exception) {
        Log.e("SaveImage", "Error saving image: ${e.message}")
    }
    return file.absolutePath
}

@Preview(showBackground = true)
@Composable
fun DetekTeaAppPreview() {
    DetekTeaTheme {
        GetStartedScreen { }
    }
}