package com.abradant.iossettings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

/**
 * Liquid-glass layer under the bottom search bar.
 *
 * <p>Pipeline per frame (Kyant0's AndroidLiquidGlass, Apache-2.0): capture the
 * window behind the bar → blur it → run the rounded-rect lens shader over it
 * (edge refraction) → add the 45° rim highlight and a bottom inner shadow, all
 * clipped to the bar's capsule.</p>
 *
 * <p>The AGSL source below is a Java port of {@code com.kyant.backdrop}
 * ({@code Shaders.kt}, {@code Lens.kt}, {@code HighlightStyle.Default},
 * {@code InnerShadow}), kept close to the original maths. Two deviations are
 * deliberate and documented in {@code docs/06-pitfalls.md}: {@code sign()} is
 * replaced by a never-zero {@code outerSign()} plus a length-guarded normal
 * (the original {@code sign(0)} collapsed the SDF normal and painted a 1px seam
 * across the bar), and the captured bitmap is un-premultiplied before the alpha
 * is forced to 1 (otherwise the translucent window capture turns the glass
 * grey).</p>
 *
 * <p>Every stage fails soft: shader → blur-only glass → raw backdrop → themed
 * pill. The native search bar stays clickable either way.</p>
 */
final class SearchGlass extends FrameLayout {

    /** Captured at 0.6×: the glass is a blur, the extra pixels are wasted work. */
    private static final float CAPTURE_SCALE = 0.6f;
    /** Temporary diagnostic: draw the blurred capture alone, without the lens. */
    private static final boolean DEBUG_BLUR_ONLY = false;
    /** Temporary diagnostic: lens with no displacement, rim or shadow. */
    private static final boolean DEBUG_PASS_THROUGH = false;
    /** 120Hz: at most one capture per display frame. */
    private static final long CAPTURE_INTERVAL_MS = 8;
    /**
     * How far around the bar the backdrop is captured. The blur needs real
     * pixels outside the pill, otherwise its edge fades into clamping streaks.
     */
    private static final int CAPTURE_PAD_DP = 16;

    /**
     * Kyant's lens shader with the highlight and inner shadow folded in, so the
     * whole glass is one pass instead of three layers.
     */
    private static final String LIQUID_GLASS_AGSL =
            "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 offset;\n"
            + "uniform float4 cornerRadii;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float depthEffect;\n"
            + "uniform float4 rimColor;\n"
            + "uniform float rimAngle;\n"
            + "uniform float rimFalloff;\n"
            + "uniform float rimWidth;\n"
            + "uniform float4 shadowColor;\n"
            + "uniform float shadowBand;\n"
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
            + "float2 outerSign(float2 coord) {\n"
            + "  return float2(coord.x >= 0.0 ? 1.0 : -1.0, coord.y >= 0.0 ? 1.0 : -1.0);\n"
            + "}\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "  float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "  if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n"
            + "    float2 v = max(cornerCoord, 0.0);\n"
            + "    return outerSign(coord) * v / max(length(v), 0.0001);\n"
            + "  } else {\n"
            + "    float gradX = step(cornerCoord.y, cornerCoord.x);\n"
            + "    return outerSign(coord) * float2(gradX, 1.0 - gradX);\n"
            + "  }\n"
            + "}\n"
            + "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n"
            + "float3 unpremulColor(half4 c) {\n"
            + "  float a = max(float(c.a), 0.05);\n"
            + "  return clamp(float3(c.rgb) / a, 0.0, 1.0);\n"
            + "}\n"
            + "half4 toHalf4(float3 color) {\n"
            + "  return half4(half(color.r), half(color.g), half(color.b), half(1.0));\n"
            + "}\n"
            + "half4 main(float2 coord) {\n"
            + "  float2 halfSize = size * 0.5;\n"
            + "  float2 local = coord + offset;\n"
            + "  float2 centeredCoord = local - halfSize;\n"
            + "  float radius = radiusAt(local, cornerRadii);\n"
            + "  float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n"
            + "  float height = max(refractionHeight, 0.5);\n"
            + "  float depth = -min(sd, 0.0);\n"
            + "  if (depth >= height) {\n"
            + "    return toHalf4(unpremulColor(content.eval(coord)));\n"
            + "  }\n"
            + "  float d = circleMap(1.0 - depth / height) * refractionAmount;\n"
            + "  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "  float2 gradRaw = gradSdRoundedRect(centeredCoord, halfSize, gradRadius)\n"
            + "      + depthEffect * float2(0.0, 1.0);\n"
            + "  float2 grad = gradRaw / max(length(gradRaw), 0.0001);\n"
            + "  float3 color = unpremulColor(content.eval(coord + d * grad));\n"
            // Kyant DefaultHighlight: |cos(normal, light)| ^ falloff, 45°.
            + "  float rimCos = abs(dot(grad, float2(cos(rimAngle), sin(rimAngle))));\n"
            + "  float rimIntensity = pow(rimCos, max(rimFalloff, 0.001));\n"
            + "  float rimT = 1.0 - smoothstep(0.0, max(rimWidth, 0.5), depth);\n"
            + "  color += rimColor.rgb * (rimColor.a * rimIntensity * rimT);\n"
            // Kyant InnerShadow: dark band hugging the bottom edge.
            + "  float ny = clamp(centeredCoord.y / max(halfSize.y, 0.5), -1.0, 1.0);\n"
            + "  float bottom = smoothstep(-0.35, 1.0, ny);\n"
            + "  float shadowT = 1.0 - smoothstep(0.0, max(shadowBand, 0.5), depth);\n"
            + "  color = color * (1.0 - shadowColor.a * bottom * shadowT);\n"
            + "  return toHalf4(color);\n"
            + "}\n";

    /**
     * Lens without the rim/shadow uniforms. Used only when the full shader is
     * rejected by the AGSL compiler, so a driver quirk degrades the look
     * instead of removing the glass.
     */
    private static final String LENS_ONLY_AGSL =
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
            + "float2 outerSign(float2 coord) {\n"
            + "  return float2(coord.x >= 0.0 ? 1.0 : -1.0, coord.y >= 0.0 ? 1.0 : -1.0);\n"
            + "}\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "  float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "  if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n"
            + "    float2 v = max(cornerCoord, 0.0);\n"
            + "    return outerSign(coord) * v / max(length(v), 0.0001);\n"
            + "  } else {\n"
            + "    float gradX = step(cornerCoord.y, cornerCoord.x);\n"
            + "    return outerSign(coord) * float2(gradX, 1.0 - gradX);\n"
            + "  }\n"
            + "}\n"
            + "float circleMap(float x) { return 1.0 - sqrt(1.0 - x * x); }\n"
            + "float3 unpremulColor(half4 c) {\n"
            + "  float a = max(float(c.a), 0.05);\n"
            + "  return clamp(float3(c.rgb) / a, 0.0, 1.0);\n"
            + "}\n"
            + "half4 toHalf4(float3 color) {\n"
            + "  return half4(half(color.r), half(color.g), half(color.b), half(1.0));\n"
            + "}\n"
            + "half4 main(float2 coord) {\n"
            + "  float2 halfSize = size * 0.5;\n"
            + "  float2 local = coord + offset;\n"
            + "  float2 centeredCoord = local - halfSize;\n"
            + "  float radius = radiusAt(local, cornerRadii);\n"
            + "  float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n"
            + "  float height = max(refractionHeight, 0.5);\n"
            + "  float depth = -min(sd, 0.0);\n"
            + "  if (depth >= height) {\n"
            + "    return toHalf4(unpremulColor(content.eval(coord)));\n"
            + "  }\n"
            + "  float d = circleMap(1.0 - depth / height) * refractionAmount;\n"
            + "  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "  float2 gradRaw = gradSdRoundedRect(centeredCoord, halfSize, gradRadius)\n"
            + "      + depthEffect * float2(0.0, 1.0);\n"
            + "  float2 grad = gradRaw / max(length(gradRaw), 0.0001);\n"
            + "  return toHalf4(unpremulColor(content.eval(coord + d * grad)));\n"
            + "}\n";

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clipPath = new Path();

    private Bitmap backdrop;
    private RenderNode blurNode;
    private RenderNode lensNode;
    private RuntimeShader lensShader;
    private boolean shaderUnavailable;
    private boolean glassActiveLogged;

    private java.lang.ref.WeakReference<View> maskView;
    private java.lang.ref.WeakReference<SearchHost> captureHost;
    private boolean capturing;
    private long nextCaptureAt;
    private int fpsFrames;
    private long fpsWindowStart;
    private long lastDiag;
    private int drawFrames;
    private long drawWindowStart;
    private long captureCostMs;
    private boolean maskLogged;
    /** Last capture, kept only to answer "did the backdrop change?" cheaply. */
    private Bitmap prevBackdrop;

    private long lastPrefsLoad;
    private float prefRefractionHeight = Prefs.DEFAULT_REFRACTION_HEIGHT;
    private float prefRefractionAmount = Prefs.DEFAULT_REFRACTION_AMOUNT;
    private float prefInnerShadow = Prefs.DEFAULT_INNER_SHADOW;
    private float prefShadowW = Prefs.DEFAULT_SHADOW_W;
    private float prefBlur = Prefs.DEFAULT_GLASS_BLUR;
    private float prefRimAlpha = Prefs.DEFAULT_RIM_ALPHA;
    private float prefRimWidth = Prefs.DEFAULT_RIM_WIDTH;

    SearchGlass(Context context) {
        super(context);
        setWillNotDraw(false);
        bitmapPaint.setFilterBitmap(true);
        loadPrefs();
    }

    private void loadPrefs() {
        lastPrefsLoad = System.currentTimeMillis();
        de.robv.android.xposed.XSharedPreferences prefs = Prefs.read();
        prefRefractionHeight = Prefs.getFloat(prefs, Prefs.REFRACTION_HEIGHT,
                Prefs.DEFAULT_REFRACTION_HEIGHT);
        prefRefractionAmount = Prefs.getFloat(prefs, Prefs.REFRACTION_AMOUNT,
                Prefs.DEFAULT_REFRACTION_AMOUNT);
        prefInnerShadow = Prefs.getFloat(prefs, Prefs.INNER_SHADOW,
                Prefs.DEFAULT_INNER_SHADOW);
        prefShadowW = Prefs.getFloat(prefs, Prefs.SHADOW_W, Prefs.DEFAULT_SHADOW_W);
        prefBlur = Prefs.getFloat(prefs, Prefs.GLASS_BLUR, Prefs.DEFAULT_GLASS_BLUR);
        prefRimAlpha = Prefs.getFloat(prefs, Prefs.RIM_ALPHA, Prefs.DEFAULT_RIM_ALPHA);
        prefRimWidth = Prefs.getFloat(prefs, Prefs.RIM_WIDTH, Prefs.DEFAULT_RIM_WIDTH);
        if (prefBlur < 0f) {
            prefBlur = 0f;
        }
        if (prefRimWidth < 0f) {
            prefRimWidth = 0f;
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
                    captureCostMs = cost;
                    // The bar lives outside the scrolling list: nothing dirties
                    // it when the list scrolls, so without this the glass is
                    // painted once and stays frozen on that first backdrop.
                    // Only repaint when the captured backdrop actually changed:
                    // repainting unconditionally schedules a new frame from
                    // inside preDraw and the window then never goes idle.
                    if (backdropChanged()) {
                        invalidate();
                    }
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
        prevBackdrop = null;
        blurNode = null;
        lensNode = null;
        super.onDetachedFromWindow();
    }

    private void capture() {
        try {
            View root = getRootView();
            if (root == null || getWidth() <= 0 || getHeight() <= 0) {
                return;
            }
            int pad = dp(CAPTURE_PAD_DP);
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
            if (now - lastDiag > 5000 && backdrop.getWidth() > 0 && backdrop.getHeight() > 0) {
                lastDiag = now;
                int center = backdrop.getPixel(backdrop.getWidth() / 2,
                        backdrop.getHeight() / 2);
                StringBuilder strip = new StringBuilder();
                int y = backdrop.getHeight() / 2;
                for (int i = 0; i <= 8; i++) {
                    int x = Math.min(backdrop.getWidth() - 1, i * (backdrop.getWidth() - 1) / 8);
                    strip.append(Integer.toHexString(backdrop.getPixel(x, y))).append(' ');
                }
                XLog.i("search glass backdrop " + backdrop.getWidth() + "x"
                        + backdrop.getHeight() + " center=#" + Integer.toHexString(center)
                        + " strip=" + strip);
            }
        } catch (Throwable t) {
            XLog.w("search glass capture failed: " + t);
            backdrop = null;
        }
    }

    /**
     * Compares the fresh capture with the previous one and keeps the copy.
     * Cheap enough for 120Hz (memcmp + one blit) and it is what keeps a static
     * page from being redrawn forever.
     */
    private boolean backdropChanged() {
        Bitmap current = backdrop;
        if (current == null || current.isRecycled()) {
            return false;
        }
        boolean changed;
        if (prevBackdrop == null || prevBackdrop.getWidth() != current.getWidth()
                || prevBackdrop.getHeight() != current.getHeight()) {
            prevBackdrop = Bitmap.createBitmap(current.getWidth(), current.getHeight(),
                    Bitmap.Config.ARGB_8888);
            changed = true;
        } else {
            changed = !prevBackdrop.sameAs(current);
        }
        if (changed) {
            try {
                Canvas copy = new Canvas(prevBackdrop);
                copy.drawBitmap(current, 0f, 0f, null);
            } catch (Throwable ignored) {
            }
        }
        return changed;
    }

    private Rect computeMask(int w, int h) {
        // Clip to the native search bar background's exact bounds when one was
        // supplied; otherwise the whole 50dp bar is the capsule.
        Rect mask = new Rect(0, 0, w, h);
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
            // inset the mask by it so the glass stops at the visible pill.
            if (maskTarget.getBackground() != null) {
                Rect padding = new Rect();
                if (maskTarget.getBackground().getPadding(padding)) {
                    mask.inset(padding.left, padding.top, padding.right, padding.bottom);
                }
            }
            // The pill is 44dp tall inside the 56dp stub: centre the mask.
            if (mask.height() < h) {
                mask.offset(0, (h - mask.height()) / 2);
            }
            if (!mask.intersect(0, 0, w, h)) {
                mask.set(0, 0, w, h);
            }
            if (!maskLogged) {
                maskLogged = true;
                XLog.i("search glass mask=" + mask.toShortString() + " view=" + w + "x" + h
                        + " target=" + maskTarget.getClass().getSimpleName() + " "
                        + maskTarget.getWidth() + "x" + maskTarget.getHeight()
                        + " pad=" + (maskTarget.getBackground() == null ? "none"
                                : maskTarget.getBackground().getPadding(new Rect()) ? "yes" : "no"));
            }
        }
        return mask;
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
        long now = android.os.SystemClock.uptimeMillis();
        drawFrames++;
        if (drawWindowStart == 0) {
            drawWindowStart = now;
        } else if (now - drawWindowStart >= 2000) {
            XLog.i("search glass draw fps="
                    + (int) (drawFrames * 1000L / Math.max(1, now - drawWindowStart))
                    + " captureCostMs=" + captureCostMs);
            drawWindowStart = now;
            drawFrames = 0;
        }
        Rect mask = computeMask(w, h);
        rect.set(mask);
        float radius = Math.min(mask.width(), mask.height()) * 0.5f;
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        if (!canvas.isHardwareAccelerated()) {
            drawPlain(canvas, mask, radius);
            return;
        }
        int save = canvas.save();
        canvas.clipPath(clipPath);
        boolean drawn = !DEBUG_BLUR_ONLY && Build.VERSION.SDK_INT >= 33
                && drawLens(canvas, mask, radius);
        if (!drawn) {
            drawn = drawBlurred(canvas, mask);
        }
        if (!drawn) {
            drawShade(canvas, mask, radius);
        }
        canvas.restoreToCount(save);
    }

    /** Blur only (API 31+); used when the lens shader is unavailable. */
    private boolean drawBlurred(Canvas canvas, Rect mask) {
        try {
            RenderNode node = recordBackdrop(mask, Math.max(0, dp(prefBlur)));
            if (node == null) {
                return false;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                node.setRenderEffect(RenderEffect.createBlurEffect(
                        Math.max(0, dp(prefBlur)), Math.max(0, dp(prefBlur)),
                        Shader.TileMode.CLAMP));
            }
            drawNodeAt(canvas, node, mask);
            return true;
        } catch (Throwable t) {
            XLog.w("search glass blur failed: " + t);
            return false;
        }
    }

    /**
     * Blur → lens. Returns false without painting anything when the shader or
     * the render nodes are not usable, so the caller can fall back.
     */
    private boolean drawLens(Canvas canvas, Rect pill, float radius) {
        Bitmap bmp = backdrop;
        if (bmp == null || bmp.isRecycled()) {
            return false;
        }
        RuntimeShader shader = shader();
        if (shader == null) {
            return false;
        }
        try {
            int blurPx = Math.max(0, dp(prefBlur));
            int pad = blurPx + 2;
            int left = pill.left - pad;
            int top = pill.top - pad;
            int nodeW = pill.width() + pad * 2;
            int nodeH = pill.height() + pad * 2;
            if (nodeW <= 0 || nodeH <= 0) {
                return false;
            }

            if (blurNode == null) {
                blurNode = new RenderNode("ios_search_blur");
            }
            // Nodes are recorded at (0,0); their placement is the canvas
            // translate in drawNodeAt(). Keeping the position at the origin
            // means drawRenderNode() inside another node needs no adjustment.
            blurNode.setPosition(0, 0, nodeW, nodeH);
            Canvas record = blurNode.beginRecording(nodeW, nodeH);
            if (!drawBackdrop(record, bmp, left, top, nodeW, nodeH)) {
                blurNode.endRecording();
                return false;
            }
            blurNode.endRecording();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurPx > 0) {
                blurNode.setRenderEffect(RenderEffect.createBlurEffect(
                        blurPx, blurPx, Shader.TileMode.CLAMP));
            }

            if (lensNode == null) {
                lensNode = new RenderNode("ios_search_lens");
            }
            lensNode.setPosition(0, 0, nodeW, nodeH);
            Canvas record2 = lensNode.beginRecording(nodeW, nodeH);
            record2.drawRenderNode(blurNode);
            lensNode.endRecording();

            setLensUniforms(pill, radius, pad);
            lensNode.setRenderEffect(
                    RenderEffect.createRuntimeShaderEffect(shader, "content"));
            drawNodeAt(canvas, lensNode, new Rect(left, top, left + nodeW, top + nodeH));
            if (!glassActiveLogged) {
                glassActiveLogged = true;
                XLog.i("liquid glass active (lens + rim + inner shadow)");
            }
            return true;
        } catch (Throwable t) {
            XLog.e("liquid glass draw failed, falling back to blur", t);
            return false;
        }
    }

    private void drawNodeAt(Canvas canvas, RenderNode node, Rect at) {
        int save = canvas.save();
        canvas.translate(at.left, at.top);
        canvas.drawRenderNode(node);
        canvas.restoreToCount(save);
    }

    /** Blur node holding the captured backdrop, sized to the whole bar. */
    private RenderNode recordBackdrop(Rect mask, int blurPx) {
        Bitmap bmp = backdrop;
        if (bmp == null || bmp.isRecycled()) {
            return null;
        }
        int pad = blurPx + 2;
        int left = mask.left - pad;
        int top = mask.top - pad;
        int nodeW = mask.width() + pad * 2;
        int nodeH = mask.height() + pad * 2;
        if (nodeW <= 0 || nodeH <= 0) {
            return null;
        }
        if (blurNode == null) {
            blurNode = new RenderNode("ios_search_blur");
        }
        blurNode.setPosition(0, 0, nodeW, nodeH);
        Canvas record = blurNode.beginRecording(nodeW, nodeH);
        if (!drawBackdrop(record, bmp, left, top, nodeW, nodeH)) {
            blurNode.endRecording();
            return null;
        }
        blurNode.endRecording();
        return blurNode;
    }

    /**
     * Draws the captured backdrop into a node of {@code nodeW × nodeH} placed
     * at view coordinate {@code (left, top)}. The capture is 0.6× and padded by
     * {@link #CAPTURE_PAD_DP}, so the source rect is mapped back to view space;
     * when the node reaches past the screen edge the covered part simply stays
     * transparent instead of being stretched.
     */
    private boolean drawBackdrop(Canvas record, Bitmap bmp, int left, int top,
                                 int nodeW, int nodeH) {
        int capPad = dp(CAPTURE_PAD_DP);
        Rect src = new Rect(
                Math.round((left + capPad) * CAPTURE_SCALE),
                Math.round((top + capPad) * CAPTURE_SCALE),
                Math.round((left + nodeW + capPad) * CAPTURE_SCALE),
                Math.round((top + nodeH + capPad) * CAPTURE_SCALE));
        if (!src.intersect(0, 0, bmp.getWidth(), bmp.getHeight())) {
            return false;
        }
        RectF dst = new RectF(
                src.left / CAPTURE_SCALE - capPad - left,
                src.top / CAPTURE_SCALE - capPad - top,
                src.right / CAPTURE_SCALE - capPad - left,
                src.bottom / CAPTURE_SCALE - capPad - top);
        record.drawBitmap(bmp, src, dst, bitmapPaint);
        return true;
    }

    private RuntimeShader shader() {
        if (lensShader != null) {
            return lensShader;
        }
        if (shaderUnavailable) {
            return null;
        }
        try {
            lensShader = new RuntimeShader(LIQUID_GLASS_AGSL);
            XLog.i("liquid glass shader compiled");
            return lensShader;
        } catch (Throwable t) {
            XLog.w("full liquid glass shader rejected, trying lens-only: " + t);
        }
        try {
            lensShader = new RuntimeShader(LENS_ONLY_AGSL);
            XLog.i("lens-only glass shader compiled");
            return lensShader;
        } catch (Throwable t) {
            shaderUnavailable = true;
            XLog.e("liquid glass shaders unavailable, blur-only glass", t);
            return null;
        }
    }

    private void setLensUniforms(Rect pill, float radius, int pad) {
        RuntimeShader shader = lensShader;
        if (shader == null) {
            return;
        }
        shader.setFloatUniform("size", (float) pill.width(), (float) pill.height());
        shader.setFloatUniform("offset", (float) -pad, (float) -pad);
        shader.setFloatUniform("cornerRadii", radius, radius, radius, radius);
        shader.setFloatUniform("refractionHeight",
                Math.max(1f, dp(prefRefractionHeight)));
        shader.setFloatUniform("refractionAmount",
                DEBUG_PASS_THROUGH ? 0f : -Math.max(0f, dp(prefRefractionAmount)));
        shader.setFloatUniform("depthEffect", 0f);
        // The lens-only fallback shader has no rim/shadow uniforms: each set is
        // allowed to fail on its own.
        setUniform(shader, "rimColor", 1f, 1f, 1f,
                DEBUG_PASS_THROUGH ? 0f : Math.max(0f, Math.min(1f, prefRimAlpha)));
        setUniform(shader, "rimAngle", (float) Math.toRadians(45.0));
        setUniform(shader, "rimFalloff", 1f);
        setUniform(shader, "rimWidth", Math.max(0.5f, dp(prefRimWidth)));
        setUniform(shader, "shadowColor", 0f, 0f, 0f,
                DEBUG_PASS_THROUGH ? 0f : Math.max(0f, Math.min(1f, prefInnerShadow)));
        setUniform(shader, "shadowBand", Math.max(1f, dp(prefShadowW)));
    }

    private static void setUniform(RuntimeShader shader, String name, float... values) {
        try {
            shader.setFloatUniform(name, values);
        } catch (Throwable ignored) {
            // Expected on the lens-only fallback shader.
        }
    }

    /** No capture (or no hardware canvas): a translucent pill, never a hole. */
    private void drawShade(Canvas canvas, Rect mask, float radius) {
        boolean dark = UiTweaks.isDarkMode(getContext());
        fallbackPaint.setColor(dark ? 0x66000000 : 0x66FFFFFF);
        canvas.drawRoundRect(new RectF(mask), radius, radius, fallbackPaint);
    }

    /** Last resort when the canvas is software: draw the raw bitmap. */
    private void drawPlain(Canvas canvas, Rect mask, float radius) {
        boolean dark = UiTweaks.isDarkMode(getContext());
        fallbackPaint.setColor(dark ? 0x661C1C1E : 0x66FFFFFF);
        canvas.drawRoundRect(new RectF(mask), radius, radius, fallbackPaint);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        if (capturing) {
            return;
        }
        super.dispatchDraw(canvas);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
