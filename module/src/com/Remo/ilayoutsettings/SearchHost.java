package com.Remo.ilayoutsettings;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.FrameLayout;

/**
 * Wrapper for the bottom search bar.
 *
 * <p>The glass captures the window by drawing it into a bitmap. The search bar
 * itself must not appear in that bitmap, and toggling visibility every frame
 * would request a layout (which caps the capture rate). This container simply
 * skips its whole subtree while {@link #capturing} is set.</p>
 */
final class SearchHost extends FrameLayout {

    boolean capturing;

    SearchHost(Context context) {
        super(context);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (capturing) {
            return;
        }
        super.dispatchDraw(canvas);
    }
}
