package camera.cn.com.mycamera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class PhotoTextureView extends TextureView {

    private int mRatioWidth = 0;
    private int mRatioHeight = 0;

    public PhotoTextureView(Context context) {
        super(context);
    }

    public PhotoTextureView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public PhotoTextureView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if (0 == mRatioWidth || 0 == mRatioHeight) {
            setMeasuredDimension(width, height);
        } else {
            if (width < height * mRatioWidth / mRatioHeight) {
                setMeasuredDimension(width, width * mRatioHeight / mRatioWidth);
            } else {
                setMeasuredDimension(height * mRatioWidth / mRatioHeight, height);
            }
        }
    }
}
