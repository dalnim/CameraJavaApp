package com.android.example.camerajavaapp;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import com.android.example.camerajavaapp.databinding.ActivityMainBinding;
import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding viewBinding;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;

    private static final String TAG = "CameraXApp";
    private static final String FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS";
    private static final int REQUEST_CODE_PERMISSIONS = 10;

    private String[] REQUIRED_PERMISSIONS = {
            android.Manifest.permission.CAMERA
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        // 카메라 권한이 있으면 카메라 실행. 없으면 카메라 권한 요청
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        viewBinding.imageCaptureButton.setOnClickListener( v -> takePhoto());
        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    //사진을 찍어서 파일로 저장을 한다.
    private void takePhoto() {
        if (imageCapture == null) {
            return;
        }

        ContentValues contentValues = getContentValues();

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(
                getContentResolver(),
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues)
                .build();

        //캡쳐된 이미지를 저장하지 않고, 바로 사용할경우는 outputOptions을 넘기지 않고, OnImageCapturedCallback을 호출하면 된다.
        imageCapture.takePicture(
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(ImageProxy image) {
                        super.onCaptureSuccess(image);
                        //캡쳐된 image파일을 사용하면 된다.
                        Log.e(TAG, "사진 캡처 성공");
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        Log.e(TAG, "사진 캡처 실패 : " + exception.getMessage(), exception);
                    }
                });


        imageCapture.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(this),
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onError(@NonNull ImageCaptureException exc) {
                        Log.e(TAG, "사진 저장 실패 : " + exc.getMessage(), exc);
                    }

                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults output) {
                        String msg = "사진 저장 성공 : " + output.getSavedUri();
                        Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show();
                        Log.d(TAG, msg);
                    }
                }
        );
    }

    //찍은 사진이 저장될 경로
    @NonNull
    private static ContentValues getContentValues() {
        String name = new SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                .format(System.currentTimeMillis());

        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Dalnim-CameraX-Image");
        }
        return contentValues;
    }

    //카메라를 실행하고 preview에 촬영되고 있는 영상을 보여준다.
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {

                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                    // Preview
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(viewBinding.viewFinder.getSurfaceProvider());

                    imageCapture = new ImageCapture.Builder().build();
                    // 후면 카메라 선택
                    CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                    // 기존에 바인드 된게 있으면 해제부터 한다.
                    cameraProvider.unbindAll();

                    // 카메라의 생명주기를 이 액티비티와 동일하게 한다. (사진 저장이 필요없으면, imageCapture 인자를 생략하면 된다.)
                    cameraProvider.bindToLifecycle(
                            MainActivity.this, cameraSelector, preview, imageCapture);

                } catch (ExecutionException | InterruptedException exc) {
                    Log.e(TAG, "카메라 뷰어 바인딩 실패", exc);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    //카메라 권한이 있는지 검사
    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    //카메라 권한 요청 결과 처리
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
            } else {
                Toast.makeText(this, "유저가 권한을 주어야 사용가능합니다.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        imageCapture = null;
        cameraExecutor.shutdown();
    }
}