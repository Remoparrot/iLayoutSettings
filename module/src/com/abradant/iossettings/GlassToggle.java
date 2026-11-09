package com.abradant.iossettings;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;

/**
 * Liquid glass toggle for the airplane-mode row.
 *
 * <p>Backdrop pipeline: before every frame that needs it, the window content
 * behind this view is redrawn into a small bitmap (this view is made invisible
 * for that one draw), the bitmap is blurred through a RenderNode with a
 * RenderEffect, and the knob samples the unblurred capture at a slightly
 * magnified scale so the pill behaves like a real lens. Falls back to a
 * gradient-only rendering when the capture is unavailable.</p>
 */
final class GlassToggle extends View {

    interface OnCheckedChangeListener {
        void onCheckedChanged(boolean checked);
    }

    private static final int PAD_DP = 10;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF trackRect = new RectF();
    private final RectF knobRect = new RectF();
    private final float density;

    private boolean checked;
    private float progress;
    private boolean dragging;
    private ValueAnimator animator;
    private OnCheckedChangeListener listener;

    private Bitmap backdrop;
    private RenderNode blurNode;
    private boolean needsCapture = true;
    private long lastCapture;

    GlassToggle(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        setClickable(true);
        setFocusable(true);
        bitmapPaint.setFilterBitmap(true);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(dp(1));
        setWillNotDraw(false);
    }

    void setOnCheckedChangeListener(OnCheckedChangeListener l) {
        listener = l;
    }

    boolean isChecked() {
        return checked;
    }

    void setChecked(boolean value) {
        if (checked == value) {
            return;
        }
        checked = value;
        animateTo(value ? 1f : 0f);
    }

    private void toggleFromUser() {
        boolean next = !checked;
        checked = next;
        animateTo(next ? 1f : 0f);
        if (listener != null) {
            listener.onCheckedChanged(next);
        }
    }

    private void animateTo(float target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(240);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                progress = (Float) animation.getAnimatedValue();
                needsCapture = true;
                invalidate();
            }
        });
        animator.start();
    }

    private final ViewTreeObserver.OnPreDrawListener preDraw =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (needsCapture && getWidth() > 0 && getHeight() > 0) {
                        long now = System.currentTimeMillis();
                        if (now - lastCapture >= 16) {
                            lastCapture = now;
                            needsCapture = false;
                            captureBackdrop();
                        }
                    }
                    return true;
                }
            };

    private final ViewTreeObserver.OnScrollChangedListener scrollListener =
            new ViewTreeObserver.OnScrollChangedListener() {
                @Override
                public void onScrollChanged() {
                    needsCapture = true;
                }
            };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(preDraw);
        getViewTreeObserver().addOnScrollChangedListener(scrollListener);
        needsCapture = true;
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            getViewTreeObserver().removeOnPreDrawListener(preDraw);
            getViewTreeObserver().removeOnScrollChangedListener(scrollListener);
        } catch (Throwable ignored) {
        }
        if (animator != null) {
            animator.cancel();
        }
        backdrop = null;
        blurNode = null;
        super.onDetachedFromWindow();
    }

    /** Redraws the window behind this view into a small bitmap. */
    private void captureBackdrop() {
        try {
            View root = getRootView();
            if (root == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            int pad = dp(PAD_DP);
            int w = getWidth() + pad * 2;
            int h = getHeight() + pad * 2;
            if (backdrop == null || backdrop.getWidth() != w || backdrop.getHeight() != h) {
                backdrop = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(backdrop);
            int[] viewLoc = new int[2];
            int[] rootLoc = new int[2];
            getLocationInWindow(viewLoc);
            root.getLocationInWindow(rootLoc);
            canvas.translate(pad - (viewLoc[0] - rootLoc[0]), pad - (viewLoc[1] - rootLoc[1]));
            float alpha = getAlpha();
            setAlpha(0f);
            try {
                canvas.save();
                root.draw(canvas);
                canvas.restore();
            } finally {
                setAlpha(alpha);
            }
        } catch (Throwable t) {
            XLog.w("glass backdrop capture failed: " + t);
            backdrop = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = dp(56);
        int height = dp(32);
        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            width = Math.min(width, MeasureSpec.getSize(widthMeasureSpec));
        }
        if (MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY) {
            height = Math.min(height, MeasureSpec.getSize(heightMeasureSpec));
        }
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float inset = dp(1);
        trackRect.set(inset, inset, w - inset, h - inset);
        float radius = trackRect.height() / 2f;

        boolean dark = UiTweaks.isDarkMode(getContext());
        int pad = dp(PAD_DP);

        // ---- knob geometry (needed by both track and knob passes) ----
        float knobSize = h - dp(6);
        float travel = w - knobSize - dp(6);
        float knobX = dp(3) + travel * clamp(progress);
        float knobY = (h - knobSize) / 2f;
        knobRect.set(knobX, knobY, knobX + knobSize, knobY + knobSize);

        // ---- track: frosted backdrop ----
        boolean glass = backdrop != null;
        if (glass) {
            drawBlurredBackdrop(canvas, trackRect, radius, dark);
        } else {
            int offColor = dark ? 0xFF39393D : 0xFFE9E9EB;
            int onColor = 0xFF34C759;
            int trackColor = blend(offColor, onColor, progress);
            paint.setShader(new LinearGradient(0, trackRect.top, 0, trackRect.bottom,
                    new int[]{lighten(trackColor, 0.18f), trackColor,
                            darken(trackColor, 0.10f)},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(trackRect, radius, radius, paint);
            paint.setShader(null);
        }

        // Glass tint over the blurred backdrop: green when on, neutral otherwise.
        int tint = blend(dark ? 0x33FFFFFF : 0x66FFFFFF,
                dark ? 0x5534C759 : 0x8834C759, progress);
        paint.setShader(null);
        paint.setColor(tint);
        canvas.drawRoundRect(trackRect, radius, radius, paint);

        // Specular top band + rim light.
        paint.setShader(new LinearGradient(0, trackRect.top, 0, trackRect.top + h * 0.65f,
                0x55FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(trackRect, radius, radius, paint);
        paint.setShader(null);
        rimPaint.setColor(0x55FFFFFF);
        canvas.drawRoundRect(trackRect, radius, radius, rimPaint);

        // ---- knob: lens ----
        // soft shadow (radial gradient so it works on a hardware canvas)
        shadowPaint.setShader(new RadialGradient(knobRect.centerX(),
                knobRect.centerY() + dp(1.5f), knobSize * 0.62f,
                new int[]{0x55000000, 0x00000000}, new float[]{0.72f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(knobRect.centerX(), knobRect.centerY() + dp(1.5f),
                knobSize * 0.62f, shadowPaint);
        shadowPaint.setShader(null);

        int save = canvas.save();
        canvas.clipPath(circlePath(knobRect.centerX(), knobRect.centerY(), knobSize / 2f));
        if (glass) {
            // Magnified sample of the unblurred capture: the lens effect.
            float srcSize = knobSize / 1.18f;
            Rect src = new Rect(
                    Math.round(pad + knobRect.centerX() - srcSize / 2f),
                    Math.round(pad + knobRect.centerY() - srcSize / 2f),
                    Math.round(pad + knobRect.centerX() + srcSize / 2f),
                    Math.round(pad + knobRect.centerY() + srcSize / 2f));
            Rect dst = new Rect(Math.round(knobRect.left), Math.round(knobRect.top),
                    Math.round(knobRect.right), Math.round(knobRect.bottom));
            src.intersect(0, 0, backdrop.getWidth(), backdrop.getHeight());
            if (!src.isEmpty()) {
                canvas.drawBitmap(backdrop, src, dst, bitmapPaint);
            }
            // Frost the lens a little so it reads as glass, not as a mirror.
            paint.setColor(dark ? 0x40FFFFFF : 0x55FFFFFF);
            canvas.drawCircle(knobRect.centerX(), knobRect.centerY(), knobSize / 2f, paint);
        } else {
            paint.setShader(new RadialGradient(
                    knobRect.centerX() - knobSize * 0.22f,
                    knobRect.centerY() - knobSize * 0.28f, knobSize * 0.92f,
                    new int[]{0xFFFFFFFF, 0xFFF4F6F9, 0xFFD8DDE4},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(knobRect.centerX(), knobRect.centerY(), knobSize / 2f, paint);
            paint.setShader(null);
        }
        // Inner shade + specular highlight.
        paint.setShader(new LinearGradient(0, knobRect.top, 0, knobRect.bottom,
                0x00FFFFFF, 0x26000000, Shader.TileMode.CLAMP));
        canvas.drawCircle(knobRect.centerX(), knobRect.centerY(), knobSize / 2f, paint);
        paint.setShader(new RadialGradient(
                knobRect.centerX() - knobSize * 0.18f,
                knobRect.centerY() - knobSize * 0.24f, knobSize * 0.32f,
                0xBBFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        canvas.drawCircle(knobRect.centerX() - knobSize * 0.18f,
                knobRect.centerY() - knobSize * 0.24f, knobSize * 0.32f, paint);
        paint.setShader(null);
        rimPaint.setColor(0x66FFFFFF);
        canvas.drawCircle(knobRect.centerX(), knobRect.centerY(), knobSize / 2f, rimPaint);
        canvas.restoreToCount(save);
    }

    private void drawBlurredBackdrop(Canvas canvas, RectF dst, float radius, boolean dark) {
        try {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            if (blurNode == null) {
                blurNode = new RenderNode("ios_glass_blur");
            }
            blurNode.setPosition(0, 0, w, h);
            Canvas recording = blurNode.beginRecording(w, h);
            int pad = dp(PAD_DP);
            Rect src = new Rect(pad, pad, pad + w, pad + h);
            recording.drawBitmap(backdrop, src,
                    new Rect(0, 0, w, h), bitmapPaint);
            blurNode.endRecording();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blurNode.setRenderEffect(RenderEffect.createBlurEffect(
                        dp(9), dp(9), Shader.TileMode.CLAMP));
            }
            int save = canvas.save();
            canvas.clipPath(roundRectPath(dst, radius));
            canvas.drawRenderNode(blurNode);
            canvas.restoreToCount(save);
        } catch (Throwable t) {
            XLog.w("glass blur failed: " + t);
        }
    }

    private android.graphics.Path roundRectPath(RectF r, float radius) {
        android.graphics.Path path = new android.graphics.Path();
        path.addRoundRect(r, radius, radius, android.graphics.Path.Direction.CW);
        return path;
    }

    private android.graphics.Path circlePath(float cx, float cy, float radius) {
        android.graphics.Path path = new android.graphics.Path();
        path.addCircle(cx, cy, radius, android.graphics.Path.Direction.CW);
        return path;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (dragging) {
                    float knobSize = getHeight() - dp(6);
                    float travel = getWidth() - knobSize - dp(6);
                    if (travel > 0) {
                        progress = clamp((event.getX() - dp(3) - knobSize / 2f) / travel);
                        needsCapture = true;
                        invalidate();
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (dragging) {
                    dragging = false;
                    boolean next = progress > 0.5f;
                    if (next != checked) {
                        checked = next;
                        if (listener != null) {
                            listener.onCheckedChanged(next);
                        }
                    }
                    animateTo(checked ? 1f : 0f);
                }
                return true;
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    public boolean performClick() {
        toggleFromUser();
        return true;
    }

    private float clamp(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }

    private static int blend(int from, int to, float t) {
        float f = clampStatic(t);
        int a = (int) (Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * f);
        int r = (int) (Color.red(from) + (Color.red(to) - Color.red(from)) * f);
        int g = (int) (Color.green(from) + (Color.green(to) - Color.green(from)) * f);
        int b = (int) (Color.blue(from) + (Color.blue(to) - Color.blue(from)) * f);
        return Color.argb(a, r, g, b);
    }

    private static float clampStatic(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    private static int lighten(int color, float amount) {
        int r = Math.min(255, (int) (Color.red(color) + 255 * amount));
        int g = Math.min(255, (int) (Color.green(color) + 255 * amount));
        int b = Math.min(255, (int) (Color.blue(color) + 255 * amount));
        return Color.argb(Color.alpha(color), r, g, b);
    }

    private static int darken(int color, float amount) {
        int r = Math.max(0, (int) (Color.red(color) - 255 * amount));
        int g = Math.max(0, (int) (Color.green(color) - 255 * amount));
        int b = Math.max(0, (int) (Color.blue(color) - 255 * amount));
        return Color.argb(Color.alpha(color), r, g, b);
    }
}
