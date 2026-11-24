package com.example.testmove2

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Camera
import android.graphics.Canvas
import android.icu.text.IDNA
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.MediaStore
import android.util.Log
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraProvider
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.OutputOptions
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.jar.Manifest
import kotlin.text.insert

class MainActivity : AppCompatActivity() {
    private lateinit var viewModel: DeviceViewModel
    private var needRefreshGallery = true
    private lateinit var viewFinder: androidx.camera.view.PreviewView
    // 실시간 사진 변수
    private var imageCapture: ImageCapture? = null

    // 권한 요청 팝업을 띄우고 사용자의 대답을 기다리는 런처
    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()
    ) { isGranted : Boolean ->
        if(isGranted) {
            // 카메라 권한 허용을 눌렀을 때
            Toast.makeText(this, "권한이 허용되었습니다. 사진을 저장합니다.",Toast.LENGTH_SHORT).show()
            startCamera()
        } else {
            // 카메라 권한 거부를 눌렀을 때
            Toast.makeText(this, "권한이 거부되었습니다.",Toast.LENGTH_SHORT).show()
        }
    }
    // disconnect하면 꺼짐 전달
    private val infoLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if(result.resultCode == RESULT_OK) {
            val action = result.data?.getStringExtra("action")

            if(action == "disconnect") {
                viewModel.DisconnectDevice()
                Toast.makeText(this, "전달받은 상태: ${viewModel.connectionStatus.value}", Toast.LENGTH_SHORT).show()
                stopCamera()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[DeviceViewModel::class.java]

        viewFinder = findViewById<PreviewView>(R.id.viewFinder)
        val tvStatus = findViewById<TextView>(R.id.tv_connection_status)
        val acqBtn = findViewById<TextView>(R.id.acq)
        val viewBtn = findViewById<TextView>(R.id.view)
        val infBtn = findViewById<TextView>(R.id.inf)
        val connectBtn = findViewById<Button>(R.id.tryConnect)
        val brightBtn = findViewById<LinearLayout>(R.id.bright)
        val saveBtn = findViewById<LinearLayout>(R.id.save)
        val recordBtn = findViewById<LinearLayout>(R.id.record)
        val resetBtn = findViewById<LinearLayout>(R.id.reset)
        val cameraArea = findViewById<FrameLayout>(R.id.camera_preview_area)
        val rvGallery = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rv_photo_gallery)
        val ivFullScreen = findViewById<com.github.chrisbanes.photoview.PhotoView>(R.id.iv_fullscreen_photo)

        val photoAdapter = PhotoAdapter(emptyList()) { clickedUri ->
            ivFullScreen.visibility = View.VISIBLE
            com.bumptech.glide.Glide.with(this@MainActivity)
                .load(clickedUri)
                .thumbnail(0.1f)
                .into(ivFullScreen)
        }
        rvGallery.adapter = photoAdapter

        ivFullScreen.setOnPhotoTapListener { _, _, _ ->
            ivFullScreen.visibility = View.GONE
        }

        // 사진 진열장을 가로 3칸짜리 바둑판(GridLayout) 모양으로 세팅
        rvGallery.layoutManager = androidx.recyclerview.widget.GridLayoutManager(this, 3)

        viewModel.connectionStatus.observe(this) { newStatus ->
            tvStatus.text = newStatus
        }

        // ACQ 탭을 누르면 사진 진열장을 다시 숨기고 원래 카메라 화면 보여주기
        acqBtn.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            rvGallery.visibility = View.GONE
            ivFullScreen.visibility = View.GONE

            if(imageCapture != null) {
                viewFinder.visibility = View.VISIBLE
            }
        }

        // VIEW 탭을 누르면 사진 진열장을 열고 찍은 사진들 불러오기
        viewBtn.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)

            viewFinder.visibility = View.GONE
            rvGallery.visibility = View.VISIBLE
            ivFullScreen.visibility = View.GONE

            if(needRefreshGallery) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val photos = loadSavedPhotos()

                    withContext(Dispatchers.Main) {
                        photoAdapter.updatePhotos(photos)
                        needRefreshGallery = false
                    }
                }
            }
        }

        // 전체화면 시 클릭하면 사진갤러리로
        ivFullScreen.setOnClickListener {
            ivFullScreen.visibility = View.GONE
        }

        infBtn.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            val intent = Intent(this, InfoActivitiy::class.java)
            val currentStatus = viewModel.connectionStatus.value

            intent.putExtra("status_data", currentStatus)

            infoLauncher.launch(intent)
        }

        // 연결 시도 버튼을 누르면 권한 확인 후 앱 안에 실시간 카메라 켜기
        connectBtn.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)

            // 권한 확인 후 맞다면 실행 아니라면 다시 권한 확인
            if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
            }
            Toast.makeText(this, "전달받은 상태: ${viewModel.connectionStatus.value}", Toast.LENGTH_SHORT).show()
        }

        brightBtn.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            viewModel.toggleFlashLight()
        }

        // SAVE 버튼을 누르면 카메라 사진 촬영
        saveBtn.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            takePhoto()
        }

        recordBtn.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
        }

        resetBtn.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM)
            viewModel.DisconnectDevice()
            // 카메라 끄기
            Toast.makeText(this, "카메라 연결이 끊겼습니다.", Toast.LENGTH_SHORT).show()
            stopCamera()
        }

        //  Bright 버튼 ON/OFF 상태 확인
        viewModel.isFlashOn.observe(this) { isOn ->
            try {
                val cameraManager = getSystemService(android.content.Context.CAMERA_SERVICE) as android.hardware.camera2.CameraManager
                val cameraId = cameraManager.cameraIdList[0]

                cameraManager.setTorchMode(cameraId, isOn)
            } catch (e: Exception) {
                android.util.Log.d("디버그테스트", "물리적 손전등이 없음")
            }
            if(isOn) {
                cameraArea.setBackgroundColor(android.graphics.Color.parseColor("#333300"))
            } else {
                cameraArea.setBackgroundColor(android.graphics.Color.parseColor("#000000"))
            }
        }
    }

    // 갤러리(MediaStore)에서 앱이 저장한 사진들만 최신순으로 찾아오는 함수
    private fun loadSavedPhotos(): List<android.net.Uri> {
        // 사진 리스트 저장할 변수 생성
        val photoList = mutableListOf<android.net.Uri>()

        // 가져올 정보 : 사진의 고유 번호(_ID)와 파일 이름(DISPLAY_NAME)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        // 조건 : 파일 이름이 "EndoSight_"로 시작하는 우리가 찍은 사진만 골라내기
        //val selection = "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        //val selectionArgs = arrayOf("EndoSight_%")
        val selection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        } else {
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?"
        }
        val selectionArgs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf("%Pictures/EndoSight%")
        } else {
            arrayOf("EndoSight_%")
        }

        // 정렬 : 방금 찍은 최신 사진이 맨 앞에 오도록 정렬
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        // 안드로이드 보안 때문에 관리자(contentResolver)에게 요청 (query) 하는 것
        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumns = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumns)
                // 사진의 고유 번호(id)를 가지고 실제 사진 주소(Uri)를 조합해서 리스트에 추가
                val contentUri = android.content.ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
                photoList.add(contentUri)
            }
        }
        return photoList
    }

    // 카메라 녹화 시작 함수
    private fun startCamera() {
        // 변수 생성
        val waitingGuide = findViewById<LinearLayout>(R.id.layout_waiting_guide)

        // 글자, 버튼 등은 숨기고 카메라 띄우기
        waitingGuide.visibility = View.GONE
        viewFinder.visibility = View.VISIBLE

        // 카메라 제공자 준비
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        // 카메라 제공자 사용 가능 여부 확인
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // 실시간 미리보기 화면 보여주기
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            // 사진 촬영기 준비
            imageCapture = ImageCapture.Builder().build()

            // 후면 카메라 선택
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                // 기존에 켜진 카메라가 있다면 끔
                cameraProvider.unbindAll()

                // 현재 화면에 실시간 카메라와 사진기를 동시에 가동함
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("디버그테스트", "카메라 가동 실패", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    // 갤러리 사진 저장 함수
    private fun takePhoto() {
        // 저장 버튼 눌렀을 때 카메라가 안켜지면 켜달라고 하기
        val imageCapture = imageCapture ?: run {
            Toast.makeText(this, "[연결시도] 버튼을 먼저 눌러주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        // 저장할 파일 정보(MediaStore) 세팅
        val filename = "EndoSight_Live_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/EndoSight")
            }
        }

        // 찍자마자 갤러리로 보냄
        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        // 사진 촬영
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    Log.e("디버그 테스트", "사진 촬영 실패", exception)
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    needRefreshGallery = true
                    Toast.makeText(baseContext, "실시간 캡처 및 갤러리 저장 완료", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    // 카메라 끄는 함수
    private fun stopCamera() {
        // 카메라 화면과 기존 화면 변수 생성
        val waitingGuide = findViewById<LinearLayout>(R.id.layout_waiting_guide)

        // 실제 카메라 하드웨어 작동 멈추기, 카메라 제공자 사용 가능 여부 확인, 사용가능 여부 확인, 카메라 비활성화
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
        }, ContextCompat.getMainExecutor(this))

        // 사진 촬영기 변수 지우기
        imageCapture = null

        // 카메라 스크린은 숨기고 글자 버튼 등 보여주기
        viewFinder.visibility = View.GONE
        waitingGuide.visibility = View.VISIBLE
    }
}