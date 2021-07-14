package com.example.camera;

import androidx.annotation.ContentView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraX;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.provider.MediaStore;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SNIHostName;

public class MainActivity extends AppCompatActivity {
    static final int REQUEST_IMAGE_CAPTURED = 1;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    private SurfaceView mSurfaceView;
    private SurfaceHolder mSurfaceHolder;
    private ImageView iv_show;
    private Button button1;
    private Button button2;
    private CameraManager mCameraManager;//Camera Manager
    private Handler childHandler, mainHandler;
    private String mCameraID;//Camera Id 0 is the last 1 is the former
    private ImageReader mImageReader;
    private CameraCaptureSession mCameraCaptureSession;
    private CameraDevice mCameraDevice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initVIew();
    }

    private void initVIew() {
        button1 = findViewById(R.id.button1);
        button2 = findViewById(R.id.button2);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                takePicture();
            }
        });
        findViewById(R.id.button2).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent_implicito();
            }
        });
        iv_show = findViewById(R.id.imageView);
        mSurfaceView = findViewById(R.id.texture_view);
        mSurfaceHolder = mSurfaceView.getHolder();
        mSurfaceHolder.setKeepScreenOn(true);
        mSurfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                initCamera2();
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                if (null != mCameraDevice) {
                    mCameraDevice.close();
                    MainActivity.this.mCameraDevice = null;
                }
            }
        });
    }

        private void initCamera2(){
            HandlerThread handlerThread = new HandlerThread("Camera2");
            handlerThread.start();
            childHandler = new Handler(handlerThread.getLooper());
            mainHandler = new Handler(getMainLooper());
            mCameraID = "" + CameraCharacteristics.LENS_FACING_FRONT;//front camera
            mImageReader = ImageReader.newInstance(1080, 1920, ImageFormat.JPEG,1);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() { //You can process the temporary photo taken here, for example, write local
                @Override
                public void onImageAvailable(ImageReader reader) {
                    mCameraDevice.close();
                    mSurfaceView.setVisibility(View.GONE);
                    iv_show.setVisibility(View.VISIBLE);
                    Image image = reader.acquireNextImage();
                    ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                    byte[] bytes = new byte[buffer.remaining()];
                    buffer.get(bytes);
                    final Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        iv_show.setImageBitmap(bitmap);
                    }
                }
            }, mainHandler);
                    mCameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            try {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                mCameraManager.openCamera(mCameraID, stateCallback, mainHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraDevice = camera;
            takePreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            if (null != mCameraDevice) {
                mCameraDevice.close();
                MainActivity.this.mCameraDevice = null;
            }
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            Toast.makeText(MainActivity.this, "camera failed to open", Toast.LENGTH_SHORT).show();
        }
    };
    private void takePreview() {
        try {

            final CaptureRequest.Builder previewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            previewRequestBuilder.addTarget(mSurfaceHolder.getSurface());
            mCameraDevice.createCaptureSession(Arrays.asList(mSurfaceHolder.getSurface(), mImageReader.getSurface()), new CameraCaptureSession.StateCallback() // ③
            {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    if (null == mCameraDevice) return;
                          mCameraCaptureSession = cameraCaptureSession;
                    try {

                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                        CaptureRequest previewRequest = previewRequestBuilder.build();
                        mCameraCaptureSession.setRepeatingRequest(previewRequest, null, childHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Configuration failed", Toast.LENGTH_SHORT).show();
                }
            }, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void takePicture() {
        if (mCameraDevice == null) return;

        final CaptureRequest.Builder captureRequestBuilder;
        try {
            captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);

            captureRequestBuilder.addTarget(mImageReader.getSurface());

            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

            int rotation = getWindowManager().getDefaultDisplay().getRotation();

            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, ORIENTATIONS.get(rotation));

            CaptureRequest mCaptureRequest = captureRequestBuilder.build();
            mCameraCaptureSession.capture(mCaptureRequest, null, childHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private void Intent_implicito(){
        Intent takePictureIntent  = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if(takePictureIntent.resolveActivity(getPackageManager()) != null){
            startActivityForResult(takePictureIntent,REQUEST_IMAGE_CAPTURED);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_IMAGE_CAPTURED && resultCode==RESULT_OK){
            Bundle extras = data.getExtras();
            Bitmap imagebitmat= (Bitmap) extras.get("data");
            iv_show.setImageBitmap(imagebitmat);

        }
    }
}