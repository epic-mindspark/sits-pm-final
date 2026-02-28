package com.smartpillbox.app.ui.scan

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.smartpillbox.app.R
import com.smartpillbox.app.databinding.ActivityScanBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class ScanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanBinding
    private var imageCapture: ImageCapture? = null

    private val requestPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission is required to scan", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val pickImage = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val intent = Intent(this, ScanResultsActivity::class.java)
            intent.putExtra("image_uri", it.toString())
            forwardExtras(intent)
            startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermission.launch(Manifest.permission.CAMERA)
        }

        binding.btnCapture.setOnClickListener { takePhoto() }
        binding.btnPickGallery.setOnClickListener { pickImage.launch("image/*") }
        binding.btnClose.setOnClickListener { finish() }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = binding.previewView.surfaceProvider
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e("ScanActivity", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            cacheDir,
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        binding.btnCapture.isEnabled = false

        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val intent = Intent(this@ScanActivity, ScanResultsActivity::class.java)
                    intent.putExtra("image_uri", savedUri.toString())
                    forwardExtras(intent)
                    startActivity(intent)
                    binding.btnCapture.isEnabled = true
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("ScanActivity", "Photo capture failed", exception)
                    Toast.makeText(
                        this@ScanActivity,
                        "Failed to capture photo: ${exception.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                    binding.btnCapture.isEnabled = true
                }
            }
        )
    }

    private fun forwardExtras(intent: Intent) {
        val m = this.intent.getStringExtra("mode")
        val pc = this.intent.getStringExtra("patient_code")
        if (m != null) intent.putExtra("mode", m)
        if (pc != null) intent.putExtra("patient_code", pc)
    }
}