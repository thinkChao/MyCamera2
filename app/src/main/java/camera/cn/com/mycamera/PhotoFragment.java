package camera.cn.com.mycamera;


import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

import java.util.Arrays;


public class PhotoFragment extends Fragment {
    String TAG = "MyLog-PhotoFragment";
    private CameraManager mCameraManager;
    private Context mContext;
    private int numCamera;
    CameraCharacteristics frontCameraCharacteristics;
    CameraCharacteristics backCameraCharacteristics;
    String frontCameraId = null;
    String backCameraId = null;
    CameraDevice mCameraDevice;
    private Handler mBackgroundHandler = null;
    private HandlerThread mBackgroundThread = null;
    private PhotoTextureView mTextureView;
    private CaptureRequest.Builder mPreviewRequestBuilder;
    private ImageReader mImageReader;
    private Size mPreviewSize;

    public PhotoFragment() {
    }

    @SuppressLint("ValidFragment")
    public PhotoFragment(Context mContext) {
        this.mContext = mContext;
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.photo_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTextureView = (PhotoTextureView) view.findViewById(R.id.photo_texture_view);

    }


    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        detectCamera();
        //openCamera();
        if (mTextureView.isAvailable()) {
            openCamera();
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
            Log.e(TAG,"执行了这一步");
        }
        startPreview();
    }

    /**
     *第一步：检测相机，包括：
     *      1、获取每个摄像头的ID
     *      2、获取摄像头的个数
     *      3、获取每个摄像头的参数信息
     **/
    public void detectCamera() {
        mCameraManager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);//得到CameraManager
        try {
            final String[] ids = mCameraManager.getCameraIdList();
            numCamera = ids.length;
            for (String id : ids) {
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(id);
                int orientation = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (orientation == CameraCharacteristics.LENS_FACING_BACK) {
                    backCameraId = id;
                    backCameraCharacteristics = cameraCharacteristics;
                    Log.e(TAG, "后置摄像头ID=" + backCameraId);
                } else {
                    frontCameraId = id;
                    frontCameraCharacteristics = cameraCharacteristics;
                    Log.e(TAG, "前置摄像头ID=" + frontCameraId);
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
        try {
            if (ActivityCompat.checkSelfPermission(getActivity(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(getActivity(),new String[]{android.Manifest.permission.CAMERA},0);
            }else {
                mCameraManager.openCamera(backCameraId, stateCallback, mBackgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /*
    创建openCamera(backCameraId, stateCallback, mBackgroundHandler)中的第2个参数
     */
    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice cameraDevice) {
            mCameraDevice = cameraDevice;
        }

        @Override
        public void onDisconnected(CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError( CameraDevice cameraDevice, int i) {
            cameraDevice.close();
        }
    };

    /*
    创建openCamera(backCameraId, stateCallback, mBackgroundHandler)中的第3个参数
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /*
    第三部：开启预览
     */
    public void startPreview(){
        try {
            SurfaceTexture surfaceTexture = mTextureView.getSurfaceTexture();
            assert surfaceTexture != null;
            if(surfaceTexture != null)
                Log.e(TAG,"surfaceTexture不为空");
            else
                Log.e(TAG,"surfaceTexture为空");
            surfaceTexture.setDefaultBufferSize(1920,1080);
            Surface surface = new Surface(surfaceTexture);

            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);

            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured( CameraCaptureSession cameraCaptureSession) {

                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {

                        }
                    },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }
    private final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
            Log.e(TAG,"可以用");
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {
            Log.e(TAG,"可以用2");
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

}
