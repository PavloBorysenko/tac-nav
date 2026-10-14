package com.tacnav.mortar.activation

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.atomic.AtomicBoolean

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun QrScanner(
    onPayload: (ActivationPayload) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val providerFuture = remember { ProcessCameraProvider.getInstance(context) }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build(),
        )
    }
    val delivered = remember { AtomicBoolean(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { viewContext ->
            PreviewView(viewContext).also { previewView ->
                providerFuture.addListener(
                    {
                        runCatching {
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().apply {
                                setSurfaceProvider(previewView.surfaceProvider)
                            }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(
                                    ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST,
                                )
                                .build()
                            analysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { proxy ->
                                val mediaImage = proxy.image
                                if (mediaImage == null || delivered.get()) {
                                    proxy.close()
                                    return@setAnalyzer
                                }
                                val image = InputImage.fromMediaImage(
                                    mediaImage,
                                    proxy.imageInfo.rotationDegrees,
                                )
                                scanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val raw = barcodes.firstNotNullOfOrNull { it.rawValue }
                                        if (raw != null && delivered.compareAndSet(false, true)) {
                                            runCatching { ActivationPayloadParser.parse(raw) }
                                                .onSuccess(onPayload)
                                                .onFailure {
                                                    delivered.set(false)
                                                    onError(
                                                        it.message
                                                            ?: "QR code is not an activation code.",
                                                    )
                                                }
                                        }
                                    }
                                    .addOnFailureListener {
                                        onError("The QR code could not be read.")
                                    }
                                    .addOnCompleteListener { proxy.close() }
                            }
                            provider.unbindAll()
                            provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }.onFailure {
                            onError("Camera is unavailable. Use manual activation.")
                        }
                    },
                    ContextCompat.getMainExecutor(context),
                )
            }
        },
    )

    DisposableEffect(Unit) {
        onDispose {
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            scanner.close()
        }
    }
}
