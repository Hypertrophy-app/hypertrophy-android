package app.hypertrophy.core.vision

import android.Manifest
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.hypertrophy.core.designsystem.theme.LocalPhoenixColors
import app.hypertrophy.core.kinematics.Landmark
import app.hypertrophy.core.kinematics.PoseFrame
import java.util.concurrent.Executors

/**
 * CameraX Preview + ImageAnalysis (STRATEGY_KEEP_ONLY_LATEST) feeding the
 * MediaPipe landmarker, with the skeleton overlay stacked on top.
 *
 * [onPoseFrame] is invoked off the main thread as soon as a landmark result is
 * available. Consumers should hand it to a thread-safe state holder or post it
 * to their own dispatcher.
 */
@Composable
fun CameraPosePreview(
    modifier: Modifier = Modifier,
    lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    onPoseFrame: (PoseFrame) -> Unit = {},
    onCameraError: (String) -> Unit = {},
    violatingJoint: Int? = null,
    formOptimal: Boolean = true,
    showSkeleton: Boolean = true,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestFrame by rememberUpdatedState(onPoseFrame)
    val latestError by rememberUpdatedState(onCameraError)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var landmarks by remember(lensFacing) { mutableStateOf<List<Landmark>>(emptyList()) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val helper = remember(context, lensFacing) {
        PoseLandmarkerHelper(
            context = context,
            listener = object : PoseLandmarkerHelper.Listener {
                override fun onPoseFrame(frame: PoseFrame) {
                    // Analysis runs off the main thread; compose state must be
                    // written on the main thread via the handler.
                    mainHandler.post { landmarks = frame.landmarks }
                    latestFrame(frame)
                }

                override fun onPoseError(message: String) {
                    latestError(message)
                }

                override fun onDelegateChanged(delegate: PoseLandmarkerHelper.PoseDelegate) = Unit
            },
        )
    }

    DisposableEffect(lifecycleOwner, lensFacing, helper) {
        val executor = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val mainExecutor = ContextCompat.getMainExecutor(context)
        val analyzer = PoseImageAnalyzer(helper)

        executor.execute {
            // The helper already reports the underlying load failure through
            // onPoseError, so don't overwrite it with a generic message here.
            if (!helper.setup()) return@execute

            providerFuture.addListener(
                {
                    try {
                        val provider = providerFuture.get()
                        val selector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()
                        val preview = Preview.Builder()
                            .build()
                            .also { it.setSurfaceProvider(previewView.surfaceProvider) }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                            .build()
                            .also { it.setAnalyzer(executor, analyzer) }

                        provider.unbindAll()
                        provider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            analysis,
                        )
                    } catch (error: Throwable) {
                        latestError(error.message ?: "Camera could not start")
                    }
                },
                mainExecutor,
            )
        }

        onDispose {
            runCatching {
                if (providerFuture.isDone) providerFuture.get().unbindAll()
            }
            executor.shutdownNow()
            helper.close()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize(),
        )
        if (showSkeleton) {
            SkeletonOverlayCanvas(
                landmarks = landmarks,
                violatingJoint = violatingJoint,
                formOptimal = formOptimal,
                mirrorHorizontally = lensFacing == CameraSelector.LENS_FACING_FRONT,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Requests CAMERA once and hosts [content] after it is granted. The button
 * remains available if the user dismissed the first system dialog.
 */
@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = LocalPhoenixColors.current
    var granted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { result -> granted = result }

    LaunchedEffect(Unit) {
        if (!granted) launcher.launch(Manifest.permission.CAMERA)
    }

    if (granted) {
        content()
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(colors.canvas)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant camera access to start form coaching")
            }
        }
    }
}
