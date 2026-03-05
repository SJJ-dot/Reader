package com.sjianjun.reader.module.watermarkcamera

import android.Manifest
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.viewModels
import com.sjianjun.reader.BaseFragment
import com.sjianjun.reader.databinding.FragmentWatermarkCameraBinding
import sjj.alog.Log
import java.io.File
import java.time.LocalDate
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class WatermarkCameraFragment : BaseFragment() {

    private var _binding: FragmentWatermarkCameraBinding? = null
    private val binding get() = _binding!!
    private val viewModel: WatermarkCameraViewModel by viewModels()

    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK

    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWatermarkCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()

        requestPermissionsAndStart()
        setupUI()
        observeViewModel()
    }

    // ======================== 权限 ========================

    private fun requestPermissionsAndStart() {
        val permissions = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        @Suppress("DEPRECATION")
        requestPermissions(permissions, REQUEST_CODE_PERMISSIONS)
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        @Suppress("DEPRECATION")
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val cameraGranted = grantResults.isNotEmpty()
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
            if (cameraGranted) {
                startCamera()
            } else {
                Toast.makeText(requireContext(), "需要相机权限才能使用水印相机", Toast.LENGTH_LONG).show()
            }
            val locationGranted = grantResults.size > 1
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED
            if (locationGranted) {
                startLocationUpdates()
            }
        }
    }

    // ======================== 相机 ========================

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = binding.previewView.surfaceProvider
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .build()

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        try {
            provider.unbindAll()
            provider.bindToLifecycle(viewLifecycleOwner, cameraSelector, preview, imageCapture)
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "相机启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ======================== UI ========================

    private fun setupUI() {
        // 拍照
        binding.btnCapture.setOnClickListener { takePhoto() }

        // 切换前后摄像头
        binding.btnSwitchCamera.setOnClickListener {
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            bindCameraUseCases()
        }

        // 设置面板开关
        binding.btnToggleSettings.setOnClickListener {
            binding.settingsPanel.visibility =
                if (binding.settingsPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.btnCloseSettings.setOnClickListener {
            binding.settingsPanel.visibility = View.GONE
        }

        // ---- 日期 ----
        binding.etDate.setOnClickListener { showDatePicker() }
        binding.btnPickDate.setOnClickListener { showDatePicker() }
        binding.btnResetDate.setOnClickListener {
            viewModel.isDateManual = false
            viewModel.refreshDate(LocalDate.now())
        }
        binding.cbDateAutoIncrement.setOnCheckedChangeListener { _, isChecked ->
            viewModel.dateAutoIncrement = isChecked
        }

        // ---- 时间 ----
        binding.rgTimeMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.rbTimeAuto.id -> {
                    viewModel.timeMode = WatermarkCameraViewModel.TimeMode.AUTO
                    binding.layoutTimeManual.visibility = View.GONE
                    binding.layoutTimeRange.visibility = View.GONE
                    viewModel.refreshTime()
                }
                binding.rbTimeManual.id -> {
                    viewModel.timeMode = WatermarkCameraViewModel.TimeMode.MANUAL
                    binding.layoutTimeManual.visibility = View.VISIBLE
                    binding.layoutTimeRange.visibility = View.GONE
                }
                binding.rbTimeRange.id -> {
                    viewModel.timeMode = WatermarkCameraViewModel.TimeMode.RANGE
                    binding.layoutTimeManual.visibility = View.GONE
                    binding.layoutTimeRange.visibility = View.VISIBLE
                    viewModel.refreshTime()
                }
            }
        }
        binding.etTimeHour.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.manualHour = (binding.etTimeHour.text.toString().toIntOrNull() ?: 0).coerceIn(0, 23)
                viewModel.refreshTime()
            }
        }
        binding.etTimeMinute.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.manualMinute = (binding.etTimeMinute.text.toString().toIntOrNull() ?: 0).coerceIn(0, 59)
                viewModel.refreshTime()
            }
        }
        val rangeFocusListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.rangeStartHour = binding.etRangeStartHour.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 0
                viewModel.rangeStartMinute = binding.etRangeStartMinute.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0
                viewModel.rangeEndHour = binding.etRangeEndHour.text.toString().toIntOrNull()?.coerceIn(0, 23) ?: 0
                viewModel.rangeEndMinute = binding.etRangeEndMinute.text.toString().toIntOrNull()?.coerceIn(0, 59) ?: 0
                viewModel.refreshTime()
            }
        }
        binding.etRangeStartHour.onFocusChangeListener = rangeFocusListener
        binding.etRangeStartMinute.onFocusChangeListener = rangeFocusListener
        binding.etRangeEndHour.onFocusChangeListener = rangeFocusListener
        binding.etRangeEndMinute.onFocusChangeListener = rangeFocusListener

        // ---- 经纬度 ----
        binding.rgGpsMode.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                binding.rbGpsAuto.id -> {
                    viewModel.setGpsMode(false)
                    binding.etGps.isFocusable = false
                    binding.etGps.isFocusableInTouchMode = false
                    startLocationUpdates()
                }
                binding.rbGpsManual.id -> {
                    viewModel.setGpsMode(true)
                    binding.etGps.isFocusable = true
                    binding.etGps.isFocusableInTouchMode = true
                    stopLocationUpdates()
                }
            }
        }
        binding.etGps.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && viewModel.isGpsManual) {
                viewModel.saveManualGps(binding.etGps.text.toString())
            }
        }

        // ---- 地址（纯手动输入，不自动获取） ----
        binding.etAddress.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                viewModel.saveAddress(binding.etAddress.text.toString())
            }
        }
        binding.btnResetAddress.setOnClickListener {
            viewModel.saveAddress("")
            binding.etAddress.setText("")
        }

        // ---- 恢复已保存的设置 ----
        restoreSavedSettings()
    }

    @SuppressLint("SetTextI18n")
    private fun restoreSavedSettings() {
        // 恢复日期自增
        binding.cbDateAutoIncrement.isChecked = viewModel.dateAutoIncrement

        // 恢复时间模式及相关UI
        when (viewModel.timeMode) {
            WatermarkCameraViewModel.TimeMode.AUTO -> {
                binding.rbTimeAuto.isChecked = true
                binding.layoutTimeManual.visibility = View.GONE
                binding.layoutTimeRange.visibility = View.GONE
            }
            WatermarkCameraViewModel.TimeMode.MANUAL -> {
                binding.rbTimeManual.isChecked = true
                binding.layoutTimeManual.visibility = View.VISIBLE
                binding.layoutTimeRange.visibility = View.GONE
                binding.etTimeHour.setText(viewModel.manualHour.toString())
                binding.etTimeMinute.setText(viewModel.manualMinute.toString())
            }
            WatermarkCameraViewModel.TimeMode.RANGE -> {
                binding.rbTimeRange.isChecked = true
                binding.layoutTimeManual.visibility = View.GONE
                binding.layoutTimeRange.visibility = View.VISIBLE
                binding.etRangeStartHour.setText(viewModel.rangeStartHour.toString())
                binding.etRangeStartMinute.setText(viewModel.rangeStartMinute.toString())
                binding.etRangeEndHour.setText(viewModel.rangeEndHour.toString())
                binding.etRangeEndMinute.setText(viewModel.rangeEndMinute.toString())
            }
        }

        // 恢复地址
        val savedAddress = viewModel.addressText.value ?: ""
        if (savedAddress.isNotEmpty()) {
            binding.etAddress.setText(savedAddress)
        }

        // 恢复GPS模式
        if (viewModel.isGpsManual) {
            binding.rbGpsManual.isChecked = true
            binding.etGps.isFocusable = true
            binding.etGps.isFocusableInTouchMode = true
        } else {
            binding.rbGpsAuto.isChecked = true
            binding.etGps.isFocusable = false
            binding.etGps.isFocusableInTouchMode = false
        }

        // 恢复GPS文本
        val savedGps = viewModel.gpsText.value ?: ""
        if (savedGps.isNotEmpty()) {
            binding.etGps.setText(savedGps)
        }
    }

    // ======================== 观察 ViewModel ========================

    @SuppressLint("SetTextI18n")
    private fun observeViewModel() {
        viewModel.dateText.observe(viewLifecycleOwner) { date ->
            binding.tvWatermarkDate.text = date
            binding.etDate.setText(date)
        }
        viewModel.dayOfWeekText.observe(viewLifecycleOwner) { dayOfWeek ->
            binding.tvWatermarkDayOfWeek.text = dayOfWeek
        }
        viewModel.timeText.observe(viewLifecycleOwner) { time ->
            binding.tvWatermarkTime.text = time
        }
        viewModel.gpsText.observe(viewLifecycleOwner) { gps ->
            binding.etGps.setText(gps)
        }
        viewModel.addressText.observe(viewLifecycleOwner) { addr ->
            binding.tvWatermarkAddress.text = addr
        }
    }

    // ======================== 日期选择 ========================

    private fun showDatePicker() {
        val date = viewModel.currentDate
        DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
            val selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
            viewModel.isDateManual = true
            viewModel.refreshDate(selectedDate)
        }, date.year, date.monthValue - 1, date.dayOfMonth).show()
    }

    // ======================== 定位（系统 LocationManager） ========================

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        // 手动模式下不需要定位
        if (viewModel.isGpsManual) return


        if (locationManager == null) {
            locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        }

        // 尝试从所有可用 Provider 获取最新的已知位置
        var lastKnown: Location? = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            lastKnown = locationManager?.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
        }
        if (lastKnown == null) {
            lastKnown = locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        }
        if (lastKnown == null) {
            lastKnown = locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        }
        if (lastKnown == null) {
            lastKnown = locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }
        if (lastKnown != null) {
            viewModel.updateLocation(lastKnown)
        }

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                viewModel.updateLocation(location)
            }
            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }

        // 同时注册 GPS 和 NETWORK Provider，降低阈值以便室内也能获取
        try {
            if (locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 3000L, 1f, locationListener!!
                )
            }
        } catch (_: Exception) {}

        try {
            if (locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 3000L, 1f, locationListener!!
                )
            }
        } catch (_: Exception) {}

        // 被动定位（接收其他应用的定位结果，零功耗）
        try {
            if (locationManager?.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) == true) {
                locationManager?.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER, 3000L, 1f, locationListener!!
                )
            }
        } catch (_: Exception) {}
    }

    private fun stopLocationUpdates() {
        locationListener?.let { locationManager?.removeUpdates(it) }
    }

    // ======================== 拍照 ========================

    @SuppressLint("SetTextI18n")
    private fun takePhoto() {
        val capture = imageCapture ?: return

        if (viewModel.timeMode == WatermarkCameraViewModel.TimeMode.AUTO) {
            viewModel.refreshTime()
        }

        val cacheDir = File(requireContext().externalCacheDir, "watermark_camera")
        cacheDir.mkdirs()
        val tempFile = File(cacheDir, "temp_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(tempFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(requireContext()),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    processAndSavePhoto(tempFile)
                }
                override fun onError(exception: ImageCaptureException) {
                    Toast.makeText(requireContext(), "拍照失败: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    @SuppressLint("SetTextI18n")
    private fun processAndSavePhoto(tempFile: File) {
        try {
            val originalBitmap = BitmapFactory.decodeFile(tempFile.absolutePath)
                ?: throw Exception("无法读取照片")

            val exifOriginal = ExifInterface(tempFile.absolutePath)
            val orientation = exifOriginal.getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
            val rotatedBitmap = rotateBitmap(originalBitmap, orientation)
            val watermarkedBitmap = drawWatermark(rotatedBitmap)

            // 保存到系统相册（含EXIF信息）
            saveToGallery(watermarkedBitmap)


            // 清理
            tempFile.delete()
            originalBitmap.recycle()
            if (rotatedBitmap !== originalBitmap) rotatedBitmap.recycle()
            watermarkedBitmap.recycle()

            viewModel.onPhotoTaken()
            binding.tvPhotoCount.text = "已拍: ${viewModel.photoCount}"
            Toast.makeText(requireContext(), "照片已保存", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun drawWatermark(source: Bitmap): Bitmap {
        val result = source.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val lines = viewModel.getWatermarkLines()
        if (lines.isEmpty()) return result

        val padding = result.width / 30f

        // 时间大字体画笔
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = result.width / 8f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 3f, 3f, Color.argb(180, 0, 0, 0))
        }

        // 日期画笔
        val datePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = result.width / 22f
            typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(4f, 2f, 2f, Color.argb(180, 0, 0, 0))
        }

        // 星期画笔
        val dayOfWeekPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = result.width / 28f
            setShadowLayer(4f, 2f, 2f, Color.argb(180, 0, 0, 0))
        }

        // 地址画笔
        val addressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = result.width / 22f
            setShadowLayer(4f, 2f, 2f, Color.argb(180, 0, 0, 0))
        }

        // 分隔线画笔
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeWidth = 3f
        }

        // 解析第一行：time|datePart
        var timeStr = ""
        var dateStr = ""
        var dayOfWeekStr = ""
        var addressStr = ""

        if (lines.isNotEmpty()) {
            val firstLine = lines[0]
            val parts = firstLine.split("|", limit = 2)
            timeStr = parts[0]
            if (parts.size > 1) {
                val dateParts = parts[1].split(" ", limit = 2)
                dateStr = dateParts[0]
                if (dateParts.size > 1) {
                    dayOfWeekStr = dateParts[1]
                }
            }
        }
        if (lines.size > 1) {
            addressStr = lines[1]
        }

        // 计算各元素尺寸
        val timeWidth = timePaint.measureText(timeStr)
        val timeBounds = Rect()
        timePaint.getTextBounds(timeStr, 0, timeStr.length, timeBounds)
        val timeHeight = timeBounds.height().toFloat()

        val dateBounds = Rect()
        datePaint.getTextBounds(dateStr, 0, dateStr.length, dateBounds)

        val dayBounds = Rect()
        if (dayOfWeekStr.isNotEmpty()) {
            dayOfWeekPaint.getTextBounds(dayOfWeekStr, 0, dayOfWeekStr.length, dayBounds)
        }

        val barMargin = padding * 0.8f

        // 计算地址行高度
        val addressBounds = Rect()
        if (addressStr.isNotEmpty()) {
            addressPaint.getTextBounds(addressStr, 0, addressStr.length, addressBounds)
        }

        // 计算地址行高度
        val addressLineHeight = if (addressStr.isNotEmpty()) addressBounds.height() + padding * 0.5f else 0f

        // 起始 Y 坐标（从底部向上）
        val startY = result.height - padding - addressLineHeight

        // 绘制时间（大字）
        val timeX = padding
        val timeY = startY
        canvas.drawText(timeStr, timeX, timeY, timePaint)

        // 绘制分隔线
        val barX = timeX + timeWidth + barMargin
        val barTop = timeY - timeHeight * 0.7f
        val barBottom = timeY + timeHeight * 0.05f
        canvas.drawLine(barX, barTop, barX, barBottom, linePaint)

        // 绘制日期+星期
        val dateX = barX + barMargin
        val dateY = timeY - timeHeight * 0.35f
        canvas.drawText(dateStr, dateX, dateY, datePaint)
        if (dayOfWeekStr.isNotEmpty()) {
            val dayY = dateY + dateBounds.height() * 0.4f + dayBounds.height() + padding * 0.2f
            canvas.drawText(dayOfWeekStr, dateX, dayY, dayOfWeekPaint)
        }

        // 绘制地址
        if (addressStr.isNotEmpty()) {
            val addrY = startY + padding * 0.8f + addressBounds.height()
            canvas.drawText(addressStr, padding, addrY, addressPaint)
        }

        return result
    }

    private fun saveToGallery(bitmap: Bitmap) {
        try {
            val date = (viewModel.dateText.value ?: "").replace(".", "")
            val seconds = String.format(java.util.Locale.US, "%02d", java.time.LocalTime.now().second)
            val time = (viewModel.timeText.value ?: "").replace(":", "") + seconds
            val fileName = "WM_${date}_${time}.jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/WatermarkCamera")
            }
            val uri = requireContext().contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            )
            uri?.let {
                requireContext().contentResolver.openOutputStream(it)?.use { os ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, os)
                }
                // 写入EXIF到相册文件
                try {
                    requireContext().contentResolver.openFileDescriptor(it, "rw")?.use { pfd ->
                        val exif = ExifInterface(pfd.fileDescriptor)
                        val date = viewModel.dateText.value ?: ""
                        val time = viewModel.timeText.value ?: ""
                        val sec = String.format(java.util.Locale.US, "%02d", java.time.LocalTime.now().second)
                        val dateTime = "${date.replace(".", ":")} $time:$sec"
                        exif.setAttribute(ExifInterface.TAG_DATETIME, dateTime)
                        exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, dateTime)
                        if (viewModel.latitude != 0.0 || viewModel.longitude != 0.0) {
                            exif.setLatLong(viewModel.latitude, viewModel.longitude)
                            Log.i("写入EXIF GPS: ${viewModel.latitude}, ${viewModel.longitude}")
                        }
                        val address = viewModel.addressText.value ?: ""
                        if (address.isNotEmpty()) {
                            exif.setAttribute(ExifInterface.TAG_USER_COMMENT, address)
                        }
                        exif.saveAttributes()
                    }
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }
    }


    // ======================== 生命周期 ========================

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        stopLocationUpdates()
        _binding = null
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1001
    }
}
