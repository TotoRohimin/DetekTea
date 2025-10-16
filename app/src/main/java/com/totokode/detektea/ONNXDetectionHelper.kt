package com.totokode.detektea

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import ai.onnxruntime.*
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.min

data class DetectionBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val confidence: Float,
    val classId: Int,
    val className: String
)

class ONNXDetectionHelper(private val context: Context) {
    private var ortSession: OrtSession? = null
    private var ortEnvironment: OrtEnvironment? = null
    private val inputSize = 640
    private var isModelLoaded = false

    private val classNames = arrayOf("cacar daun")
    // Ditingkatkan/diperendah untuk menangkap semua kemungkinan deteksi
    // Nilai ini bisa disesuaikan nanti setelah log diperiksa
    private val confidenceThreshold = 0.1f // UBAH: Ambang batas kepercayaan diturunkan
    private val iouThreshold = 0.5f

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            Log.d("ONNX", "=== STARTING MODEL LOAD ===")

            ortEnvironment = OrtEnvironment.getEnvironment()
            Log.d("ONNX", "ONNX Environment created")

            val modelBytes = context.assets.open("best.onnx").use { it.readBytes() }
            Log.d("ONNX", "Model file loaded, size: ${modelBytes.size} bytes")

            val sessionOptions = OrtSession.SessionOptions().apply {
                addCPU(false) // Use CPU by default, change if you have GPU support
                setExecutionMode(OrtSession.SessionOptions.ExecutionMode.SEQUENTIAL)
                setInterOpNumThreads(1)
                setIntraOpNumThreads(2)
                setOptimizationLevel(OrtSession.SessionOptions.OptLevel.BASIC_OPT)
            }

            ortSession = ortEnvironment?.createSession(modelBytes, sessionOptions)

            ortSession?.let { session ->
                Log.d("ONNX", "=== MODEL INFO ===")
                Log.d("ONNX", "Input count: ${session.numInputs}")
                Log.d("ONNX", "Output count: ${session.numOutputs}")
                session.inputInfo.forEach { (name, nodeInfo) ->
                    Log.d("ONNX", "Input name: '$name', Shape: ${(nodeInfo.info as TensorInfo).shape.contentToString()}")
                }
                session.outputInfo.forEach { (name, nodeInfo) ->
                    Log.d("ONNX", "Output name: '$name', Shape: ${(nodeInfo.info as TensorInfo).shape.contentToString()}")
                }

                isModelLoaded = true
                Log.d("ONNX", "=== MODEL LOADED SUCCESSFULLY ===")
            }

        } catch (e: Exception) {
            Log.e("ONNX", "Error loading model: ${e.message}", e)
            isModelLoaded = false
        }
    }

    fun isReady(): Boolean = isModelLoaded && ortSession != null

    fun detectObjects(bitmap: Bitmap): List<DetectionBox> {
        if (!isReady()) {
            Log.e("ONNX", "Model not ready")
            return emptyList()
        }

        return try {
            Log.d("ONNX", "=== STARTING DETECTION ===")
            Log.d("ONNX", "Image size: ${bitmap.width}x${bitmap.height}")

            val session = ortSession!!
            val environment = ortEnvironment!!

            val preprocessedData = preprocessImage(bitmap)
            val inputTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(preprocessedData),
                longArrayOf(1, 3, inputSize.toLong(), inputSize.toLong())
            )

            Log.d("ONNX", "Input tensor created")

            val inputName = session.inputInfo.keys.iterator().next()
            val outputs = session.run(mapOf(inputName to inputTensor))

            val detections = processYoloV8Output(outputs, bitmap.width, bitmap.height)

            outputs.close()
            inputTensor.close()

            Log.d("ONNX", "=== DETECTION COMPLETE ===")
            Log.d("ONNX", "Found ${detections.size} detections")

            detections

        } catch (e: Exception) {
            Log.e("ONNX", "Detection error: ${e.message}", e)
            emptyList()
        }
    }

    private fun preprocessImage(bitmap: Bitmap): FloatArray {
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resizedBitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        Log.d("ONNX", "Preprocessed: ${bitmap.width}x${bitmap.height} -> ${inputSize}x${inputSize}")

        val floatArray = FloatArray(3 * inputSize * inputSize)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            floatArray[i] = ((pixel shr 16 and 0xFF) / 255.0f) // R
            floatArray[i + inputSize * inputSize] = ((pixel shr 8 and 0xFF) / 255.0f) // G
            floatArray[i + 2 * inputSize * inputSize] = ((pixel and 0xFF) / 255.0f) // B
        }

        return floatArray
    }

    private fun processYoloV8Output(outputs: OrtSession.Result, originalWidth: Int, originalHeight: Int): List<DetectionBox> {
        try {
            Log.d("ONNX", "=== PROCESSING YOLOv8 OUTPUT ===")

            if (outputs.size() == 0) return emptyList()

            val outputValue = outputs[0].value
            Log.d("ONNX", "Output type: ${outputValue.javaClass.simpleName}")

            val outputTensor = outputValue as Array<Array<FloatArray>>
            val output = outputTensor[0]

            val boxes = mutableListOf<DetectionBox>()
            val numDetections: Int
            val numAttributes: Int
            val isTransposed: Boolean

            if (output[0].size > output.size) {
                isTransposed = true
                numAttributes = output.size
                numDetections = output[0].size
            } else {
                isTransposed = false
                numDetections = output.size
                numAttributes = output[0].size
            }

            Log.d("ONNX", "Output format detected: transposed=$isTransposed, detections=$numDetections, attributes=$numAttributes")

            // Tambahkan logging untuk melihat output mentah dari model
            val rawScores = mutableListOf<Pair<Float, Int>>()

            for (i in 0 until numDetections) {
                val cx: Float
                val cy: Float
                val w: Float
                val h: Float
                val conf: Float

                if (isTransposed) {
                    cx = output[0][i]
                    cy = output[1][i]
                    w = output[2][i]
                    h = output[3][i]
                    conf = output[4][i]
                } else {
                    val detection = output[i]
                    if (detection.size < 5) continue
                    cx = detection[0]
                    cy = detection[1]
                    w = detection[2]
                    h = detection[3]
                    conf = detection[4]
                }

                // Simpan semua confidence score untuk debugging
                rawScores.add(Pair(conf, i))

                // Jika confidence score di atas threshold (yang sekarang rendah)
                if (conf >= confidenceThreshold) {
                    val scaleX = originalWidth.toFloat() / inputSize
                    val scaleY = originalHeight.toFloat() / inputSize

                    val x1 = max(0f, (cx - w / 2) * scaleX)
                    val y1 = max(0f, (cy - h / 2) * scaleY)
                    val x2 = min(originalWidth.toFloat(), (cx + w / 2) * scaleX)
                    val y2 = min(originalHeight.toFloat(), (cy + h / 2) * scaleY)

                    if ((x2 - x1) > 5 && (y2 - y1) > 5) {
                        boxes.add(DetectionBox(x1, y1, x2, y2, conf, 0, "cacar daun"))
                    }
                }
            }

            // Log raw scores untuk analisis
            val topScores = rawScores.sortedByDescending { it.first }.take(10)
            Log.d("ONNX", "Top 10 raw confidence scores: ${topScores.joinToString { "(${it.first} @ idx ${it.second})" }}")

            Log.d("ONNX", "Found ${boxes.size} detections before NMS")
            return applyNMS(boxes)

        } catch (e: Exception) {
            Log.e("ONNX", "Processing error: ${e.message}", e)
            return emptyList()
        }
    }

    private fun applyNMS(boxes: List<DetectionBox>): List<DetectionBox> {
        if (boxes.isEmpty()) return emptyList()

        val sorted = boxes.sortedByDescending { it.confidence }
        val keep = mutableListOf<DetectionBox>()
        val suppress = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (suppress[i]) continue
            keep.add(sorted[i])

            for (j in i + 1 until sorted.size) {
                if (suppress[j]) continue
                val iou = calculateIoU(sorted[i], sorted[j])
                if (iou > iouThreshold) {
                    suppress[j] = true
                }
            }
        }

        Log.d("ONNX", "NMS: ${boxes.size} -> ${keep.size}")
        return keep
    }

    private fun calculateIoU(box1: DetectionBox, box2: DetectionBox): Float {
        val x1 = max(box1.x1, box2.x1)
        val y1 = max(box1.y1, box2.y1)
        val x2 = min(box1.x2, box2.x2)
        val y2 = min(box1.y2, box2.y2)

        val intersectionArea = max(0f, x2 - x1) * max(0f, y2 - y1)
        val box1Area = (box1.x2 - box1.x1) * (box1.y2 - box1.y1)
        val box2Area = (box2.x2 - box2.x1) * (box2.y2 - box2.y1)
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0) intersectionArea / unionArea else 0f
    }

    fun close() {
        try {
            Log.d("ONNX", "Closing resources...")
            ortSession?.close()
            ortEnvironment?.close()
            ortSession = null
            ortEnvironment = null
            isModelLoaded = false
        } catch (e: Exception) {
            Log.e("ONNX", "Error closing: ${e.message}")
        }
    }
}