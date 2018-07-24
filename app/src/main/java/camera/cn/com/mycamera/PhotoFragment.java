package camera.cn.com.mycamera;


import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
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

public class PhotoFragment extends Fragment {
    /**
     * Camera state: Showing camera preview.
     */
    private static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    private static final int STATE_WAITTING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    private static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    private static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Picture was taken.
     */
    private static final int STATE_PICTURE_TAKEN = 4;
    String TAG = "MyLog-PhotoFragment.java";
    private CameraManager mCameraManager;
    private Context mContext;
    private int numCamera;
    private CameraCharacteristics frontCameraCharacteristics;
    private CameraCharacteristics backCameraCharacteristics;
    private String frontCameraId = null;
    private String backCameraId = null;
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
    private  CameraCharacteristics cameraCharacteristics;
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
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mFile = new File(getActivity().getExternalFilesDir(null), "pic.jpg");
    }

    @Override
    public View onCreateView( LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.photo_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mTextureView = (PhotoTextureView) view.findViewById(R.id.photo_texture_view);
        mShutterButton = (Button)view.findViewById(R.id.shutter_button);
        mShutterButton.setOnClickListener(shutterButtonCllickListener);
    }


    @Override
    public void onResume() {
        super.onResume();
        startBackgroundThread();
        detectCamera();
        mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        openCamera();
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
            numCamera = ids.length;
            for (String id : ids) {
                cameraCharacteristics = mCameraManager.getCameraCharacteristics(id);
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
        Log.e(TAG,"openCamera()=======>>");
        mSensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);//后来增加
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

    /**
     *第三步：开启预览
     **/
    public void startPreview(){
        Log.e(TAG,"startPreview()=======>>");
        if(mCameraDevice == null){
            return;
        }
        try {
            mSurfaceTexture.setDefaultBufferSize(1920,1080);
            Surface surface = new Surface(mSurfaceTexture);
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);//添加一个surface用来接收数据
            mCaptureRequest = mPreviewRequestBuilder.build();
            mCameraDevice.createCaptureSession(Arrays.asList(surface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured( CameraCaptureSession cameraCaptureSession) {
                            Log.e(TAG,"startPreview()=======>>onConfigured()");
                            mCaptureSession = cameraCaptureSession;
                            try {
                                cameraCaptureSession.setRepeatingRequest(mCaptureRequest,captureCallback,mBackgroundHandler);
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

    private CameraCaptureSession.CaptureCallback captureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
        }

        @Override
        public void onCaptureFailed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureSequenceCompleted(@NonNull CameraCaptureSession session, int sequenceId, long frameNumber) {
            super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraCaptureSession session, int sequenceId) {
            super.onCaptureSequenceAborted(session, sequenceId);
        }

        @Override
        public void onCaptureBufferLost(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull Surface target, long frameNumber) {
            super.onCaptureBufferLost(session, request, target, frameNumber);
        }
    };

    View.OnClickListener shutterButtonCllickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            Log.e(TAG,"onClick()=======>>ShutterButton Clicked");
            try{
                //1、对焦
                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);
                mPreviewState = STATE_WAITTING_LOCK;
                mCaptureSession.capture(mPreviewRequestBuilder.build(),captureCallback,mBackgroundHandler);
            }catch (Exception ignore){

            }
        }
    };

    private CameraCaptureSession.CaptureCallback mCaptureCallback = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureStarted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull CaptureResult partialResult) {
            super.onCaptureProgressed(session, request, partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request, @NonNull TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
            if (afState == null) {
                captureStillPicture();
            } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                    CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                if (aeState == null ||
                        aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                    mPreviewState = STATE_PICTURE_TAKEN;
                    captureStillPicture();
                } else {
                    runPrecaptureSequence();
                }
            }
        }
    };

    private void captureStillPicture(){
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
            //setAutoFlash(captureBuilder);

            int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    Log.e(TAG, "onCaptureCompleted()=======>>");
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            //mCaptureSession.abortCaptures();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void unlockFocus() {
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

    private final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {

        @Override
        public void onImageAvailable(ImageReader reader) {
            mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), mFile));
        }

    };

    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;

        ImageSaver(Image image, File file) {
            mImage = image;
            mFile = file;
        }

        @Override
        public void run() {
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

    private void runPrecaptureSequence() {
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

    private int getOrientation(int rotation) {
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

}
