package camera.cn.com.mycamera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class PhotoTextureView extends TextureView {


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

    }
}
