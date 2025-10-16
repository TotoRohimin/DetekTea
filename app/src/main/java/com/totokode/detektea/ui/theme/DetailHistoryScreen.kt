package com.totokode.detektea.ui.theme // Pastikan nama package sesuai dengan proyekmu

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.totokode.detektea.DetectionBox
import com.totokode.detektea.db.DetectionResult
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailHistoryScreen(
    detectionResult: DetectionResult,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val detectedImage = remember(detectionResult.imagePath) {
        try {
            val file = java.io.File(detectionResult.imagePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(file.absolutePath)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("DetekTea", "Error loading image: ${e.message}")
            null
        }
    }

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
                title = { Text("Detail Riwayat", color = Color.White) },
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
                        text = "Waktu Deteksi: ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(detectionResult.timestamp)}",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Hasil Deteksi:",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (detectionResult.detectedObjectCount == 0) {
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
                                    text = "Tidak ada penyakit terdeteksi",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFD32F2F)
                                )
                            }
                        }
                    } else {
                        // Card ringkasan
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
                                            text = detectionResult.primaryDetectionName,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF2E7D32)
                                        )
                                        Text(
                                            text = "Kepercayaan: ${"%.1f".format(detectionResult.primaryDetectionConfidence * 100)}%",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Ditemukan **${detectionResult.detectedObjectCount}** objek penyakit.",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Tambahkan Panduan Penanganan hanya jika ada deteksi "cacar daun"
                        if (detectionResult.primaryDetectionName == "cacar daun") {
                            Spacer(modifier = Modifier.height(24.dp))
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
                        }
                    }
                } else {
                    Text("Gagal memuat gambar dari riwayat.")
                }
            }
        }
    }
}