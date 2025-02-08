package org.libsdl.app;

import static org.libsdl.app.SDLSurface.fixedHeight;
import static org.libsdl.app.SDLSurface.fixedWidth;

import android.content.Context;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.annotation.NonNull; /**
    SDLSurface. This is what we draw on, so we need to know when it's created
    in order to do anything useful.

    Because of this, that's where we set up the SDL thread
*/

public class ZinkSurface extends SurfaceView implements SurfaceHolder.Callback{

    public ZinkSurface(Context context) {
        super(context);
        if (fixedWidth > 0) {
            getHolder().setFixedSize(fixedWidth, fixedHeight);
        }
        getHolder().addCallback(this);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int widthSize = MeasureSpec.getSize(widthMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        if (fixedWidth > 0) {
            float myAspect = 1.0f * fixedWidth / fixedHeight;
            float resultWidth = widthSize;
            float resultHeight = resultWidth / myAspect;
            if (resultHeight > heightSize) {
                resultHeight = heightSize;
                resultWidth = resultHeight * myAspect;
            }

            setMeasuredDimension((int) resultWidth, (int) resultHeight);
        } else {
            setMeasuredDimension(widthSize, heightSize);
        }
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        SDLActivity.onZinkSurfaceCreated(holder.getSurface());
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        SDLActivity.onZinkSurfaceDestroyed();
    }
}
