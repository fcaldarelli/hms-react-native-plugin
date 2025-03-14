/*
    Copyright 2020-2022. Huawei Technologies Co., Ltd. All rights reserved.

    Licensed under the Apache License, Version 2.0 (the "License")
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

        https://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/

package com.huawei.hms.rn.scan.multi;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.facebook.react.ReactActivity;
import com.facebook.react.bridge.ReactApplicationContext;
import com.huawei.hmf.tasks.OnFailureListener;
import com.huawei.hmf.tasks.OnSuccessListener;
import com.huawei.hms.hmsscankit.ScanUtil;
import com.huawei.hms.ml.scan.HmsScan;
import com.huawei.hms.ml.scan.HmsScanAnalyzer;
import com.huawei.hms.mlsdk.common.MLFrame;
import com.huawei.hms.rn.scan.R;
import com.huawei.hms.rn.scan.logger.HMSLogger;
import com.huawei.hms.rn.scan.utils.Errors;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import static com.huawei.hms.rn.scan.multi.RNHMSScanMultiProcessorModule.MULTIPROCESSOR_ASYNC_CODE;
import static com.huawei.hms.rn.scan.multi.RNHMSScanMultiProcessorModule.MULTIPROCESSOR_SYNC_CODE;

public class MultiProcessorActivity extends ReactActivity {
    private ReactApplicationContext mContext;
    public static final int REQUEST_CODE_PHOTO = 0X1113;
    private static final String TAG = "MultiProcessorActivity";

    private SurfaceHolder surfaceHolder;
    private MultiProcessorCamera mMultiProcessorCamera;
    private SurfaceCallBack surfaceCallBack;
    private MultiProcessorHandler handler;
    private boolean isShow;
    private int mode;
    private ImageView galleryButton;
    private HMSLogger mHMSLogger;
    private HmsScanAnalyzer mAnalyzer;

    public ScanResultView scanResultView;
    Bundle bundle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = (ReactApplicationContext) getReactNativeHost().getReactInstanceManager().getCurrentReactContext();
        Window window = getWindow();

        mHMSLogger = HMSLogger.getInstance(mContext);

        try {
            bundle = getIntent().getExtras();
        }catch (Exception e){
            Log.i("Customized-Exception", e.getMessage());
        }

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_multiprocessor);

        mode = Objects.requireNonNull(bundle).getInt("scanMode");

        mMultiProcessorCamera = new MultiProcessorCamera();
        surfaceCallBack = new SurfaceCallBack();
        SurfaceView cameraPreview = findViewById(R.id.surfaceView);
        adjustSurface(cameraPreview);
        surfaceHolder = cameraPreview.getHolder();
        isShow = false;
        setBackOperation();

        Intent intent = getIntent();
        try {
            bundle = intent.getExtras();
        }catch (Exception e){
            Log.i("Customized-Exception", e.getMessage());
        }
        galleryButton = findViewById(R.id.img_btn);
        galleryButton.setVisibility(View.INVISIBLE);

        if (bundle.getBoolean("isGalleryAvailable")) {
            galleryButton.setVisibility(View.VISIBLE);
            setPictureScanOperation();
        }

        scanResultView = findViewById(R.id.scan_result_view);

        Intent getIntent = getIntent();
        try {
            bundle = getIntent.getExtras();
        }catch (Exception e){
            Log.i("Customized-Exception", e.getMessage());
        }
        mAnalyzer = new HmsScanAnalyzer.Creator(this).setHmsScanTypes(
                Objects.requireNonNull(bundle).getInt("scanType"),
            bundle.getIntArray("additionalScanTypes")).create();

    }

    private void adjustSurface(SurfaceView cameraPreview) {
        FrameLayout.LayoutParams paramSurface = (FrameLayout.LayoutParams) cameraPreview.getLayoutParams();
        if (getSystemService(Context.WINDOW_SERVICE) != null) {
            WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            Display defaultDisplay = Objects.requireNonNull(windowManager).getDefaultDisplay();
            Point outPoint = new Point();
            defaultDisplay.getRealSize(outPoint);
            float screenWidth = outPoint.x;
            float screenHeight = outPoint.y;
            float rate;
            if (screenWidth / (float) 1080 > screenHeight / (float) 1920) {
                rate = screenWidth / (float) 1080;
                int targetHeight = (int) (1920 * rate);
                paramSurface.width = FrameLayout.LayoutParams.MATCH_PARENT;
                paramSurface.height = targetHeight;
                int topMargin = (int) (-(targetHeight - screenHeight) / 2);
                if (topMargin < 0) {
                    paramSurface.topMargin = topMargin;
                }
            } else {
                rate = screenHeight / (float) 1920;
                int targetWidth = (int) (1080 * rate);
                paramSurface.width = targetWidth;
                paramSurface.height = FrameLayout.LayoutParams.MATCH_PARENT;
                int leftMargin = (int) (-(targetWidth - screenWidth) / 2);
                if (leftMargin < 0) {
                    paramSurface.leftMargin = leftMargin;
                }
            }
        }
    }

    private void setBackOperation() {
        ImageView backButton = findViewById(R.id.back_img);
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    @Override
    public void onBackPressed() {
        if (mode == MULTIPROCESSOR_ASYNC_CODE
                || mode == MULTIPROCESSOR_SYNC_CODE) {
            setResult(RESULT_CANCELED);
        }
        MultiProcessorActivity.this.finish();
    }

    private void setPictureScanOperation() {
        galleryButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent pickIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                pickIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*");
                MultiProcessorActivity.this.startActivityForResult(pickIntent, REQUEST_CODE_PHOTO);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isShow) {
            initCamera();
        } else {
            surfaceHolder.addCallback(surfaceCallBack);
        }
    }

    @Override
    protected void onPause() {
        if (handler != null) {
            handler.quit();
            handler = null;
        }
        mMultiProcessorCamera.close();
        if (!isShow) {
            surfaceHolder.removeCallback(surfaceCallBack);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void initCamera() {
        try {
            mMultiProcessorCamera.open(surfaceHolder);
            if (handler == null) {
                Intent intent = getIntent();

                long[] colorList = Objects.requireNonNull(intent.getExtras()).getLongArray("colorList");

                int textColor = intent.getExtras().getInt("textColor");
                float textSize = intent.getExtras().getFloat("textSize");
                float strokeWidth = intent.getExtras().getFloat("strokeWidth");

                int textBackgroundColor = intent.getExtras().getInt("textBackgroundColor");
                boolean showText = intent.getExtras().getBoolean("showText");
                boolean showTextOutBounds = intent.getExtras().getBoolean("showTextOutBounds");
                boolean autoSizeText = intent.getExtras().getBoolean("autoSizeText");
                int minTextSize = intent.getExtras().getInt("minTextSize");
                int granularity = intent.getExtras().getInt("granularity");

                if (mAnalyzer != null) {
                    handler = new MultiProcessorHandler(MultiProcessorActivity.this, mContext, mMultiProcessorCamera,
                            mode, colorList, textColor, textSize, strokeWidth, textBackgroundColor, showText,
                            showTextOutBounds, autoSizeText, minTextSize, granularity, mAnalyzer);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString(), e);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || requestCode != REQUEST_CODE_PHOTO) {
            return;
        }
        try {
            if (mode == MULTIPROCESSOR_SYNC_CODE && mAnalyzer != null) {
                decodeMultiSync(MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData()));
            } else if (mode == MULTIPROCESSOR_ASYNC_CODE && mAnalyzer != null) {
                decodeMultiAsync(MediaStore.Images.Media.getBitmap(getContentResolver(), data.getData()));
            }
        } catch (IOException e) {
            Log.e(TAG, "Gallery Exception");
        }
    }

    private void decodeMultiAsync(Bitmap bitmap) {

        MLFrame image = MLFrame.fromBitmap(bitmap);

        if (mAnalyzer.isAvailable()) {
            mHMSLogger.startMethodExecutionTimer("MultiProcessorActivity.decodeMultiAsync");
            mAnalyzer.analyzInAsyn(image).addOnSuccessListener(new OnSuccessListener<List<HmsScan>>() {
                @Override
                public void onSuccess(List<HmsScan> hmsScans) {
                    if (hmsScans != null && hmsScans.size() > 0 && hmsScans.get(0) != null && !TextUtils.isEmpty(
                            hmsScans.get(0).getOriginalValue())) {
                        mHMSLogger.sendSingleEvent("MultiProcessorActivity.decodeMultiAsync");
                        HmsScan[] infos = new HmsScan[hmsScans.size()];
                        Intent intent = new Intent();
                        intent.putExtra(ScanUtil.RESULT, hmsScans.toArray(infos));
                        setResult(RESULT_OK, intent);
                        MultiProcessorActivity.this.finish();
                    }
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(Exception e) {
                    Log.w(TAG, e);
                    mHMSLogger.sendSingleEvent("MultiProcessorActivity.decodeMultiAsync",
                            Errors.DECODE_MULTI_ASYNC_ON_FAILURE.getErrorCode());
                }
            });
        } else {
            Log.e(Errors.HMS_SCAN_ANALYZER_ERROR.getErrorCode(), Errors.HMS_SCAN_ANALYZER_ERROR.getErrorMessage(), null);
        }
    }

    private void decodeMultiSync(Bitmap bitmap) {
        MLFrame image = MLFrame.fromBitmap(bitmap);
        if (mAnalyzer.isAvailable()) {
            mHMSLogger.startMethodExecutionTimer("MultiProcessorActivity.decodeMultiSync");
            SparseArray<HmsScan> result = mAnalyzer.analyseFrame(image);
            mHMSLogger.sendSingleEvent("MultiProcessorActivity.decodeMultiSync");
            if (result != null && result.size() > 0 && result.valueAt(0) != null && !TextUtils.isEmpty(
                    result.valueAt(0).getOriginalValue())) {
                HmsScan[] info = new HmsScan[result.size()];
                for (int index = 0; index < result.size(); index++) {
                    info[index] = result.valueAt(index);
                }
                Intent intent = new Intent();
                intent.putExtra(ScanUtil.RESULT, info);
                setResult(RESULT_OK, intent);
                MultiProcessorActivity.this.finish();
            } else {
                Log.i("Error code: " + Errors.DECODE_MULTI_SYNC_COULD_NOT_FIND.getErrorCode(),
                        Errors.DECODE_MULTI_SYNC_COULD_NOT_FIND.getErrorMessage());
            }
        } else {
            Log.e(Errors.HMS_SCAN_ANALYZER_ERROR.getErrorCode(), Errors.HMS_SCAN_ANALYZER_ERROR.getErrorMessage(), null);
        }
    }

    class SurfaceCallBack implements SurfaceHolder.Callback {

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (!isShow) {
                isShow = true;
                initCamera();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            isShow = false;
        }
    }
}
