package camera.cn.com.mycamera;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;

public class PhotoFragment extends Fragment {

    private static final int STATE_PREVIEW = 0;  //正在预览状态

    private static final int STATE_WAITTING_LOCK = 1;  //等待对焦

    private static final int STATE_WAITING_PRECAPTURE = 2;

    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    private static final int STATE_PICTURE_TAKEN = 4;   //拍照结束

    private static String TAG = "MyLog-PhotoFragment.java";
    private CameraManager mCameraManager;
    private Context mContext;
    private int numCamera;
    private CameraCharacteristics frontCameraCharacteristics;
    private CameraCharacteristics backCameraCharacteristics;
    private String mFrontCameraId = null;
    private String mBackCameraId = null;
    private CameraDevice mCameraDevice;
    private Handler mBackgroundHandler = null;
    private HandlerThread mBackgroundThread = null;
    private PhotoTextureView mTextureView;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    SurfaceTexture mSurfaceTexture;
    CaptureRequest mCaptureRequest;
    CameraCaptureSession mCaptureSession;
    private Button mShutterButton;
    private int mPreviewState = STATE_PREVIEW;
    private ImageReader mImageReader;
    private File mFile;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    private int mSensorOrientation;
    private  CameraCharacteristics mCameraCharacteristics;
    private StreamConfigurationMap mConfigurationMap;
    private Size[] mSupportSizes;

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public PhotoFragment(){

    }

    @SuppressLint("ValidFragment")
    public PhotoFragment(Context mContext) {
        this.mContext = mContext;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.e(TAG,"onCreate()=======>>");
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.e(TAG,"onCreateView()=======>>");
        return inflater.inflate(R.layout.photo_fragment, container, false);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        Log.e(TAG,"onActivityCreated()=======>>");
        super.onActivityCreated(savedInstanceState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        Log.e(TAG,"onViewCreated()=======>>");
        super.onViewCreated(view, savedInstanceState);
        mTextureView = (PhotoTextureView) view.findViewById(R.id.photo_texture_view);
        mShutterButton = (Button)view.findViewById(R.id.shutter_button);
        mShutterButton.setOnClickListener(shutterButtonCllickListener);
    }

    @Override
    public void onStart() {
        Log.e(TAG,"onStart()=======>>");
        super.onStart();
    }

    @Override
    public void onResume() {
        Log.e(TAG,"onResume()=======>>");
        super.onResume();
        mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");

        startBackgroundThread();//打开相机的操作在这个线程中，所以这里先开启这个线程
        detectCamera();
        openCamera();
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        //startPreview();开启预览要在SurfaceTexture创建后开始，所以这里直接将预览放在了创建SurfaceTexture的回调里
        if(mTextureView == null){
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }else{
            startPreview();
        }
    }

    @Override
    public void onPause() {
        Log.e(TAG,"onPause()=======>>");
        super.onPause();
    }

    @Override
    public void onStop() {
        Log.e(TAG,"onStop()=======>>");
        super.onStop();
        stopPreview();
        closeCamera();
    }

    @Override
    public void onDestroy() {
        Log.e(TAG,"onDestroy()=======>>");
        super.onDestroy();

    }

    /**
     *第一步：检测相机，包括：
     *      1、获取每个摄像头的ID
     *      2、获取摄像头的个数
     *      3、获取每个摄像头的参数信息
     **/
    public void detectCamera() {
        Log.e(TAG,"detectCamera()=======>>");
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);//得到CameraManager
        try {
            final String[] ids = mCameraManager.getCameraIdList();
            //numCamera = ids.length;
            for (String id : ids) {
                mCameraCharacteristics = mCameraManager.getCameraCharacteristics(id);
                int orientation = mCameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (orientation == CameraCharacteristics.LENS_FACING_BACK) {
                    mBackCameraId = id;
                    backCameraCharacteristics = mCameraCharacteristics;
                    //Log.e(TAG, "后置摄像头ID=" + mBackCameraId);
                } else {
                    mFrontCameraId = id;
                    frontCameraCharacteristics = mCameraCharacteristics;
                    //Log.e(TAG, "前置摄像头ID=" + mFrontCameraId);
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 第二步：打开相机
     **/
    public void openCamera() {
        Log.e(TAG,"openCamera()=======>>");
        setOrGetCameraParameter();//参数获取和设置
        try {
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(),new String[]{android.Manifest.permission.CAMERA},0);
            }else {
                mCameraManager.openCamera(mBackCameraId, stateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *第三步：开启预览
     **/
    public void startPreview(){
        Log.e(TAG,"startPreview()=======>>");
        if(mCameraDevice == null){
            return;
        }
        try {
            mSurfaceTexture.setDefaultBufferSize(1080,1080);//设置预览尺寸
            Surface surface = new Surface(mSurfaceTexture);//创建Surface
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);//创建预览请求
            mPreviewRequestBuilder.addTarget(surface);//添加一个surface用来接收数据
            mCaptureRequest = mPreviewRequestBuilder.build();//创建请求
            mCameraDevice.createCaptureSession(Arrays.asList(surface,mImageReader.getSurface()),//添加了一个Surface和一个ImageView
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured( CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG,"startPreview()=======>>onConfigured()");
                            mCaptureSession = cameraCaptureSession;
                            try {
                                cameraCaptureSession.setRepeatingRequest(mCaptureRequest,mCaptureCallback,mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG,"startPreview()=======>>onConfigureFailed()");

                        }
                    },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *第四步：拍照
     **/
    private void captureStillPicture(){
        Log.e(TAG,"captureStillPicture()=======>>");
        try {
            final Activity activity = getActivity();
            if (null == activity ||  null == mCameraDevice) {
                return;
            }
            final CaptureRequest.Builder captureBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);

            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, 90);//得到得照片要旋转90度
            mCaptureSession.stopRepeating();

            mCaptureSession.capture(captureBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setOrGetCameraParameter(){
        mConfigurationMap = backCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (mConfigurationMap == null) {
            return;
        }
        mSupportSizes = mConfigurationMap.getOutputSizes(SurfaceTexture.class);
        int i = 1;
        for (Size option : mSupportSizes) {
            //Log.e(TAG,"Support Size" +i+ " : " +"Width = " + option.getWidth() + " , Height = " + option.getHeight() );
            i++;
        }
        Display display = getActivity().getWindowManager().getDefaultDisplay();
        Point outSize = new Point();
        display.getSize(outSize);
        int screenWidth = outSize.x;
        int screenHeight = outSize.y;
        Log.e(TAG,"ScreenWidth = " + screenWidth + " , ScreenHeight = " + screenHeight);

        mSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);//得到的值是旋转角度
        //Log.e(TAG,"摄像头传感器的方向 = " + mSensorOrientation);
        mImageReader = ImageReader.newInstance(2160, 1080,
                ImageFormat.JPEG, /*maxImages*/2);       //设置图片的格式、尺寸
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener,mBackgroundHandler);
    }

    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            Log.e(TAG,"onSurfaceTextureAvailable()=======>>");
            mSurfaceTexture = surfaceTexture;
            startPreview();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            Log.e(TAG,"onSurfaceTextureSizeChanged()=======>>");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            Log.e(TAG,"onSurfaceTextureDestroyed()=======>>");
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
            // Log.e(TAG,"onSurfaceTextureUpdated()=======>>");
        }
    };

    /**
     * 第五步：保存照片
     */
    private static class ImageSaver implements Runnable {
        private final Image mImage;
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
            Log.e(TAG,"ImageSaver.run()=======>>");
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

    }

    /**
     *第六步：取消对焦
     **/
    private void unlockFocus() {
        Log.e(TAG,"unlockFocus()=======>>");
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
            mPreviewState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mCaptureRequest, mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 第七步：关闭预览
     * 关闭预览，就是关闭当前会话
     */
    private void stopPreview(){
        if(mCaptureSession != null){
            mCaptureSession.close();
            mCaptureSession=null;
        }
    }
    /**
     * 第八步：释放相机资源
     * 关闭预览，就是关闭当前会话
     */
    private void closeCamera(){
        mCameraDevice.close();
    }

    /**
     * Shtter键的点击事件
     */
    View.OnClickListener shutterButtonCllickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.e(TAG,"onClick()=======>>ShutterButton Clicked");
            try{
                //1、对焦
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                mPreviewState = STATE_WAITTING_LOCK;
                mCaptureSession.capture(mPreviewRequestBuilder.build(),mCaptureCallback,mBackgroundHandler);
            }catch (Exception ignore){

            }
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            switch(mPreviewState){
                case STATE_WAITTING_LOCK:{
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN == afState) {
                        Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mPreviewState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_PREVIEW:{
                    break;
                }
                case STATE_PICTURE_TAKEN:{
                    unlockFocus();
                    break;
                }
            }
        }
    };

    private void runPrecaptureSequence() {
        Log.e(TAG,"runPrecaptureSequence()=======>>");
        try {
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
            mPreviewState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     *创建openCamera(backCameraId, stateCallback, mBackgroundHandler)中的第2个参数
     **/
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            Log.e(TAG,"stateCallback.onOpened()=======>>");
            mCameraDevice = cameraDevice;//获取CameraDevice

        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            Log.e(TAG,"stateCallback.onDisconnected()=======>>");
            cameraDevice.close();//关闭获取CameraDevice
        }

        @Override
        public void onError( CameraDevice cameraDevice, int i) {
            Log.e(TAG,"stateCallback.onError()=======>>");
            cameraDevice.close();
        }
    };

    /**
     *创建openCamera(backCameraId, stateCallback, mBackgroundHandler)中的第3个参数
     **/
    private void startBackgroundThread() {
        Log.e(TAG,"startBackgroundThread()=======>>");
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    
    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            Log.e(TAG,"onImageAvailable()=======>>");
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));//将保存操作放到线程去执行
        }
    };

}
