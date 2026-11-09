package com.abradant.iossettings;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * Liquid-glass container used by the bottom search bar.
 *
 * <p>Before each frame it redraws the window content behind itself into a
 * bitmap (this view is made invisible for that one draw), feeds it through a
 * kyant-style refraction shader on a RenderNode and clips the result to the
 * native search pill. The native stub keeps its own fills on top; this view
 * only supplies the glass underneath it.</p>
 */
final class SearchGlass extends FrameLayout {

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();
    private long lastDiag;

    private Bitmap backdrop;
    private Bitmap maskBitmap;
    private Drawable background9;
    private RenderNode blurNode;
    private RenderNode refractNode;
    private android.graphics.RuntimeShader liquidShader;
    private boolean shaderBroken;
    private boolean glassActiveLogged;
    /**
     * Kyant's rounded-rect refraction shader (Apache-2.0,
     * https://github.com/Kyant0/AndroidLiquidGlass, com.kyant.backdrop).
     * Adapted to run as a RenderEffect inside this module.
     */
    private static final String LIQUID_GLASS_AGSL =
            "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 offset;\n"
            + "uniform float4 cornerRadii;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float depthEffect;\n"
            + "float radiusAt(float2 coord, float4 radii) {\n"
            + "  if (coord.x >= 0.0) { if (coord.y <= 0.0) return radii.y; else return radii.z; }\n"
            + "  else { if (coord.y <= 0.0) return radii.x; else return radii.w; }\n"
            + "}\n"
            + "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "  float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "  float outside = length(max(cornerCoord, 0.0)) - radius;\n"
            + "  float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n"
            + "  return outside + inside;\n"
            + "}\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "  float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "  if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n"
            + "    float2 v = max(cornerCoord, 0.0);\n"
            + "    return sign(coord) * v / max(length(v), 0.0001);\n"
            + "  } else {\n"
            + "    float gradX = step(cornerCoord.y, cornerCoord.x);\n"
            + "    return sign(coord) * float2(gradX, 1.0 - gradX);\n"
            + "  }\n"
            + "}\n"
            + "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n"
            // The captured window is translucent, and content.eval() returns
            // premultiplied colour. Divide it back out before forcing alpha to
            // 1, otherwise the whole pill is multiplied by that alpha and looks
            // flat grey. Explicit constructors only: mixing half3/float is where
            // SkSL compilation tends to fail (that killed the shader in 16.2).
            + "half4 unpremul(half4 c) {\n"
            + "  float a = max(float(c.a), 0.05);\n"
            + "  float3 rgb = clamp(float3(c.rgb) / a, 0.0, 1.0);\n"
            + "  return half4(half(rgb.r), half(rgb.g), half(rgb.b), half(1.0));\n"
            + "}\n"
            + "half4 main(float2 coord) {\n"
            + "  float2 halfSize = size * 0.5;\n"
            + "  float2 centeredCoord = (coord + offset) - halfSize;\n"
            + "  float radius = radiusAt(coord, cornerRadii);\n"
            + "  float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n"
            + "  if (-sd >= refractionHeight) {\n"
            + "    return unpremul(content.eval(coord));\n"
            + "  }\n"
            + "  float d = circleMap(1.0 - (-min(sd, 0.0)) / refractionHeight) * refractionAmount;\n"
            + "  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "  float2 gradRaw = gradSdRoundedRect(centeredCoord, halfSize, gradRadius)\n"
            + "      + depthEffect * float2(0.0, 1.0);\n"
            + "  float2 grad = gradRaw / max(length(gradRaw), 0.0001);\n"
            // sign(0) == 0 collapses the SDF normal on the middle row, so fade
            // the offset out over a few pixels there: no 1px seam can be painted.
            + "  float seamFade = smoothstep(0.0, 6.0, abs(centeredCoord.y));\n"
            + "  float2 refractedCoord = coord + d * grad * seamFade;\n"
            + "  return unpremul(content.eval(refractedCoord));\n"
            + "}\n";

    private java.lang.ref.WeakReference<View> maskView;
    private java.lang.ref.WeakReference<SearchHost> captureHost;
    private boolean capturing;
    private long nextCaptureAt;
    private int fpsFrames;
    private long fpsWindowStart;
    private int lastFps;
    private int captureFails;
    private final Paint debugPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private long lastPrefsLoad;
    private float prefRefractionHeight = Prefs.DEFAULT_REFRACTION_HEIGHT;
    private float prefRefractionAmount = Prefs.DEFAULT_REFRACTION_AMOUNT;
    private float prefChromatic = Prefs.DEFAULT_CHROMATIC;
    private float prefInnerShadow = Prefs.DEFAULT_INNER_SHADOW;
    private float prefShadowW = Prefs.DEFAULT_SHADOW_W;
    private static String shaderSource;

    SearchGlass(Context context) {
        super(context);
        setWillNotDraw(false);
        bitmapPaint.setFilterBitmap(true);
        loadBackground(context);
        loadPrefs();
        loadShader(context);
    }

    private void loadPrefs() {
        lastPrefsLoad = System.currentTimeMillis();
        de.robv.android.xposed.XSharedPreferences prefs = Prefs.read();
        prefRefractionHeight = Prefs.getFloat(prefs, Prefs.REFRACTION_HEIGHT,
                Prefs.DEFAULT_REFRACTION_HEIGHT);
        prefRefractionAmount = Prefs.getFloat(prefs, Prefs.REFRACTION_AMOUNT,
                Prefs.DEFAULT_REFRACTION_AMOUNT);
        prefChromatic = Prefs.getFloat(prefs, Prefs.CHROMATIC, Prefs.DEFAULT_CHROMATIC);
        prefInnerShadow = Prefs.getFloat(prefs, Prefs.INNER_SHADOW,
                Prefs.DEFAULT_INNER_SHADOW);
        prefShadowW = Prefs.getFloat(prefs, Prefs.SHADOW_W, Prefs.DEFAULT_SHADOW_W);
    }

    private static void loadShader(Context context) {
        if (shaderSource != null) {
            return;
        }
        java.io.InputStream in = null;
        try {
            android.content.res.Resources res = context.getPackageManager()
                    .getResourcesForApplication("com.abradant.iossettings");
            in = res.getAssets().open("liquid_glass.agsl");
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }
            shaderSource = out.toString("UTF-8");
            XLog.i("lattice glass shader loaded");
        } catch (Throwable t) {
            XLog.w("load glass shader failed: " + t);
        } finally {
            if (in != null) {
                try { in.close(); } catch (Throwable ignored) { }
            }
        }
    }

    void setMaskView(View view) {
        maskView = view == null ? null : new java.lang.ref.WeakReference<>(view);
        XLog.i("search glass mask: " + (view == null ? "none (full bar)"
                : view.getClass().getName()));
    }

    void setCaptureHost(SearchHost host) {
        captureHost = host == null ? null : new java.lang.ref.WeakReference<>(host);
    }

    private void loadBackground(Context context) {
        try {
            Resources res = context.getPackageManager()
                    .getResourcesForApplication("com.abradant.iossettings");
            int id = res.getIdentifier("search_bar_bg", "drawable", "com.abradant.iossettings");
            if (id != 0) {
                background9 = res.getDrawable(id, null);
            }
            // reverted: the bitmap mask (bg.9.png) is no longer used
            maskBitmap = null;
        } catch (Throwable t) {
            XLog.w("search bar background missing: " + t);
        }
    }

    private final ViewTreeObserver.OnPreDrawListener preDraw =
            new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (!isShown() || getWidth() <= 0 || getHeight() <= 0) {
                        return true;
                    }
                    long now = android.os.SystemClock.uptimeMillis();
                    if (now < nextCaptureAt) {
                        return true;
                    }
                    long started = android.os.SystemClock.uptimeMillis();
                    capture();
                    long cost = android.os.SystemClock.uptimeMillis() - started;
                    // One capture per display frame (120Hz = 8ms), but never
                    // faster than the capture itself; this also keeps animating
                    // pages (外观 / 待机显示 / 墙纸 / 控制中心) alive instead of
                    // freezing for 800ms when the list offset stops changing.
                    nextCaptureAt = started + Math.max(CAPTURE_INTERVAL_MS, cost);
                    fpsFrames++;
                    if (fpsWindowStart == 0) {
                        fpsWindowStart = now;
                    } else if (now - fpsWindowStart >= 2000) {
                        int fps = (int) (fpsFrames * 1000L / Math.max(1, now - fpsWindowStart));
                        XLog.i("search glass capture fps=" + fps);
                        fpsWindowStart = now;
                        fpsFrames = 0;
                    }
                    return true;
                }
            };

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnPreDrawListener(preDraw);
        nextCaptureAt = 0;
        fpsWindowStart = 0;
        fpsFrames = 0;
    }

    @Override
    protected void onDetachedFromWindow() {
        try {
            getViewTreeObserver().removeOnPreDrawListener(preDraw);
        } catch (Throwable ignored) {
        }
        backdrop = null;
        blurNode = null;
        super.onDetachedFromWindow();
    }

    private static final float CAPTURE_SCALE = 0.6f;
    /** 120Hz: capture once per display frame (8ms) instead of 60Hz (16ms). */
    private static final long CAPTURE_INTERVAL_MS = 8;

    private void capture() {
        try {
            View root = getRootView();
            if (root == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            int pad = dp(4);
            int w = Math.max(1, Math.round((getWidth() + pad * 2) * CAPTURE_SCALE));
            int h = Math.max(1, Math.round((getHeight() + pad * 2) * CAPTURE_SCALE));
            if (backdrop == null || backdrop.getWidth() != w || backdrop.getHeight() != h) {
                backdrop = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            }
            Canvas canvas = new Canvas(backdrop);
            canvas.scale(CAPTURE_SCALE, CAPTURE_SCALE);
            int[] viewLoc = new int[2];
            int[] rootLoc = new int[2];
            getLocationInWindow(viewLoc);
            root.getLocationInWindow(rootLoc);
            canvas.translate(pad - (viewLoc[0] - rootLoc[0]), pad - (viewLoc[1] - rootLoc[1]));
            // Do not toggle visibility here: setVisibility() requests a layout
            // on every capture and that alone caps the glass far below 120Hz.
            // The host skips the whole search-bar subtree and the flag below
            // covers this view even when it has no host.
            SearchHost host = captureHost == null ? null : captureHost.get();
            boolean hostWasCapturing = host != null && host.capturing;
            if (host != null) {
                host.capturing = true;
            }
            capturing = true;
            try {
                canvas.save();
                root.draw(canvas);
                canvas.restore();
            } finally {
                capturing = false;
                if (host != null) {
                    host.capturing = hostWasCapturing;
                }
            }
            long now = System.currentTimeMillis();
            if (now - lastDiag > 2000 && backdrop != null
                    && backdrop.getWidth() > 0 && backdrop.getHeight() > 0) {
                lastDiag = now;
                int center = backdrop.getPixel(backdrop.getWidth() / 2,
                        backdrop.getHeight() / 2);
                XLog.i("search glass backdrop " + backdrop.getWidth() + "x"
                        + backdrop.getHeight() + " center=#" + Integer.toHexString(center));
            }
        } catch (Throwable t) {
            XLog.w("search glass capture failed: " + t);
            backdrop = null;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (capturing) {
            return;
        }
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        // Clip the blur to the native search bar background's exact bounds
        // (miuix_appcompat_search_mode_edit_text_bg_*.9.png): same width, same
        // height, same pill radius.
        android.graphics.Rect mask = new android.graphics.Rect(0, 0, w, h);
        View maskTarget = maskView == null ? null : maskView.get();
        if (maskTarget != null && maskTarget.getWidth() > 0 && maskTarget.getHeight() > 0) {
            int[] targetLoc = new int[2];
            int[] selfLoc = new int[2];
            maskTarget.getLocationInWindow(targetLoc);
            getLocationInWindow(selfLoc);
            int left = targetLoc[0] - selfLoc[0];
            int top = targetLoc[1] - selfLoc[1];
            mask.set(left, top, left + maskTarget.getWidth(), top + maskTarget.getHeight());
            // The native 9-patch carries padding (white margins on the sides);
            // inset the mask by it so the blur stops at the visible pill.
            Drawable background = maskTarget.getBackground();
            if (background != null) {
                android.graphics.Rect padding = new android.graphics.Rect();
                if (background.getPadding(padding)) {
                    mask.inset(padding.left, padding.top, padding.right, padding.bottom);
                }
            }
            // The theme's 9-patch has baked transparent side margins, so the
            // visible pill is narrower than the drawable/view bounds. Pull the
            // mask in by eye until it matches the visible search bar.
            int sideInset = dp(6);
            if (mask.width() > sideInset * 2) {
                mask.inset(sideInset, 0, sideInset, 0);
            }
            // The native pill may be shorter than the 50dp wrapper (the input
            // area is 44dp and sits at the top); centre it so the glass capsule
            // is centred in the bar instead of hugging the top edge.
            if (mask.height() < h) {
                mask.offset(0, (h - mask.height()) / 2);
            }
            if (!mask.intersect(0, 0, w, h)) {
                mask.set(0, 0, w, h);
            }
        }
        rect.set(mask);
        float radius = mask.height() / 2f;
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        boolean dark = UiTweaks.isDarkMode(getContext());
        if (maskBitmap != null) {
            int layer = canvas.saveLayer(0, 0, w, h, null);
            if (backdrop != null) {
                drawBlurred(canvas, w, h);
            } else {
                shadePaint.setColor(dark ? 0xFF1C1C1E : 0xFFFFFFFF);
                canvas.drawRoundRect(rect, radius, radius, shadePaint);
            }
            Paint maskPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            maskPaint.setFilterBitmap(true);
            android.graphics.Matrix matrix = new android.graphics.Matrix();
            matrix.setScale(w / (float) maskBitmap.getWidth(), h / (float) maskBitmap.getHeight());
            android.graphics.BitmapShader shader = new android.graphics.BitmapShader(
                    maskBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            shader.setLocalMatrix(matrix);
            maskPaint.setShader(shader);
            maskPaint.setXfermode(new android.graphics.PorterDuffXfermode(
                    android.graphics.PorterDuff.Mode.DST_IN));
            canvas.drawRect(0, 0, w, h, maskPaint);
            canvas.restoreToCount(layer);
        } else {
            int save = canvas.save();
            canvas.clipPath(clipPath);
            if (backdrop != null) {
                drawBlurred(canvas, w, h);
            } else {
                // No capture this frame: stay translucent so the bar never
                // shows up as an opaque slab.
                shadePaint.setColor(dark ? 0x66000000 : 0x66FFFFFF);
                canvas.drawRoundRect(rect, radius, radius, shadePaint);
            }
            canvas.restoreToCount(save);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (capturing) {
            return;
        }
        super.dispatchDraw(canvas);
    }

    private static void setUniform(android.graphics.RuntimeShader shader, String name,
                                   float value) {
        try {
            shader.setFloatUniform(name, value);
        } catch (Throwable ignored) {
            // The fallback (kyant) shader has no inner-shadow uniforms.
        }
    }

    private void drawBlurred(Canvas canvas, int w, int h) {
        try {
            int pad = dp(4);
            float scale = CAPTURE_SCALE;
            Rect src = new Rect(
                    Math.round(pad * scale), Math.round(pad * scale),
                    Math.round((pad + w) * scale), Math.round((pad + h) * scale));
            src.intersect(0, 0, backdrop.getWidth(), backdrop.getHeight());
            if (src.isEmpty()) {
                return;
            }
            if (!canvas.isHardwareAccelerated()) {
                canvas.drawBitmap(backdrop, src, new Rect(0, 0, w, h), bitmapPaint);
                return;
            }
            if (blurNode == null) {
                blurNode = new RenderNode("ios_search_blur");
            }
            blurNode.setPosition(0, 0, w, h);
            Canvas recording = blurNode.beginRecording(w, h);
            recording.drawBitmap(backdrop, src, new Rect(0, 0, w, h), bitmapPaint);
            blurNode.endRecording();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // kyant's glass blurs the backdrop before the lens pass.
                blurNode.setRenderEffect(RenderEffect.createBlurEffect(
                        dp(10), dp(10), Shader.TileMode.CLAMP));
            }
            if (false) { // refraction disabled: blur-only glass
                // Kyant liquid glass: pure refraction, no blur.
                try {
                    if (refractNode == null) {
                        refractNode = new RenderNode("ios_search_refract");
                    }
                    refractNode.setPosition(0, 0, w, h);
                    Canvas record = refractNode.beginRecording(w, h);
                    record.drawRenderNode(blurNode);
                    refractNode.endRecording();
                    if (liquidShader == null) {
                        try {
                            liquidShader = new android.graphics.RuntimeShader(
                                    shaderSource != null ? shaderSource : LIQUID_GLASS_AGSL);
                            XLog.i("liquid glass shader compiled");
                        } catch (Throwable t) {
                            XLog.e("lattice shader compile failed, fallback", t);
                            liquidShader = new android.graphics.RuntimeShader(LIQUID_GLASS_AGSL);
                        }
                    }
                    float radius = Math.min(w, h) / 2f;
                    liquidShader.setFloatUniform("size", w, h);
                    liquidShader.setFloatUniform("offset", 0f, 0f);
                    liquidShader.setFloatUniform("cornerRadii", radius, radius, radius, radius);
                    liquidShader.setFloatUniform("refractionHeight",
                            dp(Math.max(1f, prefRefractionHeight)));
                    liquidShader.setFloatUniform("refractionAmount",
                            -dp(Math.max(0f, prefRefractionAmount)));
                    liquidShader.setFloatUniform("depthEffect", 0f);
                    setUniform(liquidShader, "chromaticAberration",
                            Math.max(0f, Math.min(1f, prefChromatic)));
                    setUniform(liquidShader, "innerShadow",
                            Math.max(0f, Math.min(1f, prefInnerShadow)));
                    setUniform(liquidShader, "shadowW", dp(Math.max(2f, prefShadowW)));
                    refractNode.setRenderEffect(
                            RenderEffect.createRuntimeShaderEffect(liquidShader, "content"));
                    canvas.drawRenderNode(refractNode);
                    if (!glassActiveLogged) {
                        glassActiveLogged = true;
                        XLog.i("liquid glass shader active");
                    }
                    return;
                } catch (Throwable t) {
                    // The native stub fill is behind this view: hide the glass
                    // so the themed search bar shows instead of a blank strip.
                    shaderBroken = true;
                    liquidShader = null;
                    XLog.e("liquid glass shader failed, native bar restored", t);
                    setVisibility(View.GONE);
                }
            }
            canvas.drawRenderNode(blurNode);
        } catch (Throwable t) {
            XLog.w("search glass blur failed: " + t);
            try {
                if (backdrop != null) {
                    canvas.drawBitmap(backdrop,
                            new Rect(0, 0, backdrop.getWidth(), backdrop.getHeight()),
                            new Rect(0, 0, w, h), bitmapPaint);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
