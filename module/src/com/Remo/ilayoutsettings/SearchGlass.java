package com.Remo.ilayoutsettings;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
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
 * <p>Pipeline per frame (Kyant0's backdrop 2.0.1, Apache-2.0): capture the
 * window behind the bar -> blur it -> run the rounded-rect lens shader over it
 * (edge refraction), clipped to the bar's capsule. 16.6 replaces MIUI's own
 * {@code miuix_appcompat_search_mode_edit_text_bg_light/dark} 9-patch: this
 * view is now the only surface, so it also paints the translucent body tint
 * and the rim.</p>
 *
 * <p>The AGSL lens below is a Java port of {@code com.kyant.backdrop}
 * ({@code Shaders.kt}: {@code RoundedRectRefractionShaderString}), kept close
 * to the original maths. The rim follows Kyant's {@code HighlightStyle.Default}
 * idea (directional SDF highlight) but is restricted to the vertical normal, so
 * only the top and bottom edges light up; the end caps stay clear. There is no
 * inner shadow: on the light theme it read as a dirty band. Two deviations from
 * Kyant are deliberate and documented in {@code docs/06-pitfalls.md}:
 * {@code sign()} is replaced by a never-zero {@code outerSign()} plus a
 * length-guarded normal (the original {@code sign(0)} collapsed the SDF normal
 * and painted a 1px seam across the bar), and the captured bitmap is
 * un-premultiplied before the alpha is forced to 1 (otherwise the translucent
 * window capture turns the glass grey).</p>
 *
 * <p>16.6 also switches the outline from a circular round-rect to Kyant's
 * continuous-curvature (G2) rounded rectangle ({@code Kyant0/Shapes} 1.2.1,
 * {@code ContinuousCurvatureRoundedRectangleCornerBuilder}), so the capsule
 * keeps iOS' curvature-continuous corners instead of the curvature jump a plain
 * stadium has.</p>
 *
 * <p>Every stage fails soft: shader -> blur + tint + flat top/bottom rim ->
 * tinted G2 pill. The native search bar stays clickable either way.</p>
 */
final class SearchGlass extends FrameLayout {

    /** Captured at 0.6x: the glass is a blur, the extra pixels are wasted work. */
    private static final float CAPTURE_SCALE = 0.6f;
    /** Temporary diagnostic: draw the blurred capture alone, without the lens. */
    private static final boolean DEBUG_BLUR_ONLY = false;
    /** Temporary diagnostic: lens with no displacement, rim or shadow. */
    private static final boolean DEBUG_PASS_THROUGH = false;
    /** Temporary diagnostic: publish the draw decision as the view's content description. */
    private static final boolean DEBUG_DIAG = false;
    /** 120Hz: at most one capture per display frame. */
    private static final long CAPTURE_INTERVAL_MS = 8;
    /**
     * How far around the bar the backdrop is captured. The blur needs real
     * pixels outside the pill, otherwise its edge fades into clamping streaks.
     */
    private static final int CAPTURE_PAD_DP = 16;

    /**
     * Kyant's rounded-rect refraction (backdrop 2.0.1 {@code Shaders.kt}) plus
     * the 16.6 body tint and its directional rim. The rim is in the shader on
     * purpose: it multiplies the SDF normal's vertical component, so only the
     * top and bottom of the capsule light up (the left/right ends stay clear) —
     * exactly the "上下才有光边" look. There is no inner shadow any more; the
     * old dark band read as dirt on the light theme.
     */
    private static final String LIQUID_GLASS_AGSL =
            "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 offset;\n"
            + "uniform float4 cornerRadii;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float depthEffect;\n"
            + "uniform float4 tintColor;\n"
            + "uniform float tintTop;\n"
            + "uniform float tintBottom;\n"
            + "uniform float4 rimColor;\n"
            + "uniform float rimWidth;\n"
            + "uniform float rimAlpha;\n"
            + "uniform float rimFalloff;\n"
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
            + "  float tintT = clamp(local.y / max(size.y, 1.0), 0.0, 1.0);\n"
            + "  float tintAlpha = mix(tintTop, tintBottom, tintT);\n"
            // Kyant 2.0.1: pixels deeper than the refraction band are copied
            // untouched, so only the edge ring pays for the shader.
            + "  if (-sd >= refractionHeight) {\n"
            + "    float3 inside = unpremulColor(content.eval(coord));\n"
            + "    inside = mix(inside, tintColor.rgb, tintAlpha);\n"
            + "    return toHalf4(inside);\n"
            + "  }\n"
            + "  sd = min(sd, 0.0);\n"
            + "  float depth = -sd;\n"
            + "  float d = circleMap(1.0 - depth / refractionHeight) * refractionAmount;\n"
            + "  float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "  float2 depthDir = centeredCoord / max(length(centeredCoord), 0.0001);\n"
            + "  float2 gradRaw = gradSdRoundedRect(centeredCoord, halfSize, gradRadius)\n"
            + "      + depthEffect * depthDir;\n"
            + "  float2 grad = gradRaw / max(length(gradRaw), 0.0001);\n"
            + "  float3 color = unpremulColor(content.eval(coord + d * grad));\n"
            + "  color = mix(color, tintColor.rgb, tintAlpha);\n"
            // Rim: |normal.y|^falloff confines the highlight to the horizontal
            // edges, equally on the top and the bottom (the first pass made the
            // top brighter and it read as a blown highlight). No shadow, no rim
            // on the end caps.
            + "  float rimBand = 1.0 - smoothstep(0.0, max(rimWidth, 0.5), depth);\n"
            + "  float rim = pow(abs(grad.y), rimFalloff) * rimBand * rimAlpha;\n"
            + "  color = color + rimColor.rgb * rim;\n"
            + "  return toHalf4(color);\n"
            + "}\n";

    private final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fallbackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tintPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 16.6: the visible outline, a Kyant G2 continuous-curvature capsule. */
    private final Path clipPath = new Path();

    /**
     * Body tint alpha. The 9-patch is no longer drawn under the glass, so the
     * veil comes from here. It is deliberately uniform: with a top-to-bottom
     * gradient the same additive rim looked much brighter at the darker bottom,
     * and the reference has equal top/bottom light edges.
     */
    private static final float TINT_DARK_ALPHA = 0.10f;
    private static final float TINT_LIGHT_ALPHA = 0.50f;
    /**
     * Kyant0/Shapes 1.2.1 {@code ContinuousCurvatureRoundedRectangleCornerBuilder}
     * control points for a horizontal capsule (its tH = 1, tV = 0 branch). Each
     * pair is (x, y) in units of the corner radius; identical for every capsule
     * whose width is at least twice its height, which is always true here.
     */
    private static final float[] G2_CAPSULE = {
            -0.666667f, 0.000000f,
            -0.096298f, 0.000000f,
            0.180052f, 0.000000f,
            0.413445f, 0.096674f,
            0.663991f, 0.200454f,
            0.835377f, 0.403357f,
            0.924201f, 0.617798f,
            1.000000f, 0.800793f,
            1.000000f, 0.954345f,
            1.000000f, 1.000000f};
    private int shapeLeft = Integer.MIN_VALUE;
    private int shapeTop;
    private int shapeWidth;
    private int shapeHeight;
    /** Visible pill inside the mask view, when MIUI's 9-patch was removed. */
    private Rect pillVisibleRect;
    private LinearGradient rimGradient;
    private int rimGradientW;
    private int rimGradientH;
    private boolean rimGradientDark;
    private LinearGradient tintGradient;
    private int tintGradientW;
    private int tintGradientH;
    private boolean tintGradientDark;

    /**
     * OS4 capture path: copy the glass rectangle out of the window surface.
     * The result is scaled back to the backdrop bitmap the lens shader expects.
     */
    private void captureWithPixelCopy() {
        if (pixelCopyInFlight || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        final android.app.Activity activity = activityOf(getContext());
        if (activity == null) {
            return;
        }
        try {
            int pad = dp(CAPTURE_PAD_DP);
            int[] loc = new int[2];
            getLocationInWindow(loc);
            android.graphics.Rect src = new android.graphics.Rect(
                    Math.max(0, loc[0] - pad),
                    Math.max(0, loc[1] - pad),
                    loc[0] + getWidth() + pad,
                    loc[1] + getHeight() + pad);
            View decor = activity.getWindow().getDecorView();
            src.intersect(0, 0, decor.getWidth(), decor.getHeight());
            if (src.width() <= 0 || src.height() <= 0) {
                return;
            }
            final int outW = Math.max(1, Math.round((getWidth() + pad * 2) * CAPTURE_SCALE));
            final int outH = Math.max(1, Math.round((getHeight() + pad * 2) * CAPTURE_SCALE));
            final Bitmap direct = Bitmap.createBitmap(src.width(), src.height(),
                    Bitmap.Config.ARGB_8888);
            pixelCopyInFlight = true;
            android.view.PixelCopy.request(activity.getWindow(), src, direct,
                    new android.view.PixelCopy.OnPixelCopyFinishedListener() {
                        @Override
                        public void onPixelCopyFinished(int result) {
                            pixelCopyInFlight = false;
                            if (result != android.view.PixelCopy.SUCCESS) {
                                lastCaptureError = "pixelcopy:" + result;
                                direct.recycle();
                                return;
                            }
                            try {
                                Bitmap scaled = Bitmap.createScaledBitmap(direct, outW, outH, true);
                                if (direct != scaled) {
                                    direct.recycle();
                                }
                                backdrop = scaled;
                                captureCount++;
                                lastCenterColor = scaled.getWidth() > 0 && scaled.getHeight() > 0
                                        ? scaled.getPixel(scaled.getWidth() / 2,
                                                scaled.getHeight() / 2) : 0;
                                if (backdropChanged()) {
                                    invalidate();
                                }
                            } catch (Throwable t) {
                                lastCaptureError = t.getClass().getSimpleName();
                            }
                        }
                    }, new android.os.Handler(android.os.Looper.getMainLooper()));
        } catch (Throwable t) {
            pixelCopyInFlight = false;
            lastCaptureError = "pc:" + t.getClass().getSimpleName();
        }
    }

    private static android.app.Activity activityOf(android.content.Context context) {
        android.content.Context c = context;
        while (c instanceof android.content.ContextWrapper) {
            if (c instanceof android.app.Activity) {
                return (android.app.Activity) c;
            }
            c = ((android.content.ContextWrapper) c).getBaseContext();
        }
        return null;
    }

    private Bitmap backdrop;
    /** One PixelCopy request at a time; the callback is asynchronous. */
    private boolean pixelCopyInFlight;
    private RenderNode blurNode;
    private RenderNode lensNode;
    private RuntimeShader lensShader;
    private boolean shaderUnavailable;
    private static boolean prefsLogged;
    private boolean glassActiveLogged;

    private java.lang.ref.WeakReference<View> maskView;
    private java.lang.ref.WeakReference<View> cachedMaskTarget;
    private int cachedMaskWidth;
    private int cachedMaskHeight;
    private Rect cachedVisibleBounds;
    private boolean visibleProbeDone;
    /** How often the settings screen's changes are picked up, in ms. */
    private static final long PREFS_RELOAD_MS = 500;
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
    private long diagAt;
    private String lastCaptureError;
    private int captureCount;
    private int changeCount;
    private int lastCenterColor;
    private float prefRefractionHeight = Prefs.DEFAULT_REFRACTION_HEIGHT;
    private float prefRefractionAmount = Prefs.DEFAULT_REFRACTION_AMOUNT;
    private float prefBlur = Prefs.DEFAULT_GLASS_BLUR;

    SearchGlass(Context context) {
        super(context);
        setWillNotDraw(false);
        bitmapPaint.setFilterBitmap(true);
        loadPrefs();
    }

    private void loadPrefs() {
        lastPrefsLoad = android.os.SystemClock.uptimeMillis();
        android.content.Context context = getContext();
        float height = Math.max(1f, Prefs.getFloat(context, Prefs.REFRACTION_HEIGHT,
                Prefs.DEFAULT_REFRACTION_HEIGHT));
        float amount = Math.max(0f, Prefs.getFloat(context, Prefs.REFRACTION_AMOUNT,
                Prefs.DEFAULT_REFRACTION_AMOUNT));
        float blur = Math.max(0f, Prefs.getFloat(context, Prefs.GLASS_BLUR,
                Prefs.DEFAULT_GLASS_BLUR));
        if (!prefsLogged) {
            prefsLogged = true;
            XLog.i("glass prefs via provider: blur=" + blur + " amount=" + amount
                    + " height=" + height);
        }
        if (blur == prefBlur && amount == prefRefractionAmount && height == prefRefractionHeight) {
            return;
        }
        prefBlur = blur;
        prefRefractionAmount = amount;
        prefRefractionHeight = height;
        XLog.i("glass tuning: blur=" + prefBlur + " refraction=" + prefRefractionAmount
                + "/" + prefRefractionHeight);
        invalidate();
    }

    /** The settings screen writes prefs at any time; pick changes up live. */
    private void reloadPrefsIfStale() {
        if (android.os.SystemClock.uptimeMillis() - lastPrefsLoad >= PREFS_RELOAD_MS) {
            loadPrefs();
        }
    }

    void setMaskView(View view) {
        maskView = view == null ? null : new java.lang.ref.WeakReference<>(view);
        cachedMaskTarget = null;
        cachedVisibleBounds = null;
        visibleProbeDone = false;
        XLog.i("search glass mask: " + (view == null ? "none (full bar)"
                : view.getClass().getName()));
    }

    /**
     * 16.6: the visible pill inside {@code maskView}, captured from MIUI's
     * 9-patch before the installer removes that background. Without it the mask
     * would fall back to the full (taller) stub and the glass would grow ~12dp.
     */
    void setPillVisibleRect(Rect visible) {
        pillVisibleRect = visible == null ? null : new Rect(visible);
        XLog.i("search glass pill: " + (pillVisibleRect == null ? "none"
                : pillVisibleRect.toShortString()));
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
                    // The bar lives outside the scrolling list, so a static page
                    // never redraws it. Poll the settings file here: preDraw runs
                    // every frame, and a changed value invalidates the glass.
                    reloadPrefsIfStale();
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
                    // pages (appearance / standby / wallpaper / control centre) alive instead of
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
        // HyperOS 4 keeps the Settings list inside a hardware layer, so drawing
        // the view tree into a software bitmap returns the same frozen frame on
        // every pass (the glass backdrop stopped following the scroll). Read
        // the window surface instead: PixelCopy always returns the frame that
        // is actually on screen, including the scrolled list.
        if (Build.VERSION.SDK_INT >= 37) {
            captureWithPixelCopy();
            return;
        }
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
            captureCount++;
            lastCenterColor = backdrop.getWidth() > 0 && backdrop.getHeight() > 0
                    ? backdrop.getPixel(backdrop.getWidth() / 2, backdrop.getHeight() / 2) : 0;
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
            lastCaptureError = t.getClass().getSimpleName() + (t.getMessage() == null
                    ? "" : (":" + t.getMessage()));
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
            changeCount++;
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
            Rect visible = pillVisibleRect != null ? pillVisibleRect
                    : visibleBackgroundBounds(maskTarget);
            if (visible != null) {
                // The theme bakes transparent margins into the capsule drawable
                // (getPadding() reports nothing), so the view bounds can be
                // wider/taller than the painted pill. Use the painted pixels;
                // they already carry the real position inside the target.
                mask.set(left + visible.left, top + visible.top,
                        left + visible.right, top + visible.bottom);
            } else {
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
            }
            if (!mask.intersect(0, 0, w, h)) {
                mask.set(0, 0, w, h);
            }
            if (!maskLogged) {
                maskLogged = true;
                XLog.i("search glass mask=" + mask.toShortString() + " view=" + w + "x" + h
                        + " target=" + maskTarget.getClass().getSimpleName() + " "
                        + maskTarget.getWidth() + "x" + maskTarget.getHeight()
                        + " visible=" + (visible == null ? "none" : visible.toShortString())
                        + " bg=" + (maskTarget.getBackground() == null ? "none"
                                : maskTarget.getBackground().getClass().getName()));
            }
        }
        return mask;
    }

    /**
     * Renders the native capsule drawable and returns the bounds of the pixels
     * it actually paints. Theme capsules are 9-patches with transparent margins
     * baked into the PNG, so {@code getPadding()} returns nothing while the
     * visible pill is several dp smaller than its view.
     */
    private Rect visibleBackgroundBounds(View target) {
        if (cachedMaskTarget != null && cachedMaskTarget.get() == target
                && cachedMaskWidth == target.getWidth()
                && cachedMaskHeight == target.getHeight()) {
            return cachedVisibleBounds;
        }
        if (visibleProbeDone) {
            return null;
        }
        visibleProbeDone = true;
        Rect rect = paintedBackgroundBounds(target);
        if (rect == null) {
            cachedMaskTarget = null;
            cachedVisibleBounds = null;
            return null;
        }
        cachedMaskTarget = new java.lang.ref.WeakReference<>(target);
        cachedMaskWidth = target.getWidth();
        cachedMaskHeight = target.getHeight();
        cachedVisibleBounds = rect;
        return rect;
    }

    /**
     * Renders a view's background drawable at its current size and returns the
     * bounds of the pixels it actually paints. Package-visible so the bottom
     * search installer can log what each candidate capsule really covers.
     */
    static Rect paintedBackgroundBounds(View target) {
        int w = target.getWidth();
        int h = target.getHeight();
        android.graphics.drawable.Drawable background = target.getBackground();
        if (w <= 0 || h <= 0 || background == null) {
            return null;
        }
        return paintedBounds(background, w, h);
    }

    /**
     * 16.6: same probe, but for a drawable that was already detached from its
     * view. The bottom-bar installer has to remove the 9-patch before the first
     * layout (otherwise the capsule flashes), so it measures the saved drawable
     * once the pill has a real size.
     */
    static Rect paintedBounds(android.graphics.drawable.Drawable background, int w, int h) {
        if (w <= 0 || h <= 0 || background == null) {
            return null;
        }
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Rect oldBounds = new Rect();
            background.copyBounds(oldBounds);
            try {
                background.setBounds(0, 0, w, h);
                background.draw(canvas);
            } finally {
                background.setBounds(oldBounds);
            }
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            int minX = w;
            int minY = h;
            int maxX = -1;
            int maxY = -1;
            for (int y = 0; y < h; y++) {
                int row = y * w;
                for (int x = 0; x < w; x++) {
                    if ((pixels[row + x] >>> 24) >= 72) {
                        if (x < minX) {
                            minX = x;
                        }
                        if (x > maxX) {
                            maxX = x;
                        }
                        if (y < minY) {
                            minY = y;
                        }
                        if (y > maxY) {
                            maxY = y;
                        }
                    }
                }
            }
            if (maxX < minX || maxY < minY) {
                return null;
            }
            return new Rect(minX, minY, maxX + 1, maxY + 1);
        } catch (Throwable t) {
            XLog.w("search glass visible-bounds probe failed: " + t);
            return null;
        } finally {
            if (bitmap != null) {
                bitmap.recycle();
            }
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
        float radius = Math.min(mask.width(), mask.height()) * 0.5f;
        buildShape(mask);

        if (!canvas.isHardwareAccelerated()) {
            drawPlain(canvas, mask);
            return;
        }
        int save = canvas.save();
        canvas.clipPath(clipPath);
        boolean lens = !DEBUG_BLUR_ONLY && Build.VERSION.SDK_INT >= 33
                && drawLens(canvas, mask, radius);
        boolean blurred = false;
        if (!lens) {
            blurred = drawBlurred(canvas, mask);
        }
        if (!lens && !blurred) {
            drawShade(canvas, mask);
            drawFlatRim(canvas, mask);
        }
        publishDiag(canvas.isHardwareAccelerated(), mask, lens, blurred);
        canvas.restoreToCount(save);
    }

    /**
     * Temporary on-device diagnostics. Written into the view's content
     * description so {@code adb shell uiautomator dump} can read the draw
     * decision without logcat (HyperOS 4 keeps the log buffers unreadable
     * for the shell user).
     */
    private void publishDiag(boolean hardware, Rect mask, boolean lens, boolean blurred) {
        if (!DEBUG_DIAG) {
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - diagAt < 1500L) {
            return;
        }
        diagAt = now;
        try {
            View target = maskView == null ? null : maskView.get();
            setContentDescription("GL hw=" + hardware
                    + " bd=" + (backdrop != null)
                    + " lens=" + lens
                    + " blur=" + blurred
                    + " err=" + lastCaptureError
                    + " cap=" + captureCount
                    + " chg=" + changeCount
                    + " px=" + Integer.toHexString(lastCenterColor)
                    + " mask=" + mask.toShortString()
                    + " pill=" + (target == null ? "null" : target.getWidth() + "x" + target.getHeight()));
        } catch (Throwable ignored) {
        }
    }

    /**
     * Builds the visible outline. 16.6 uses Kyant's continuous-curvature (G2)
     * capsule instead of a circular stadium: the curvature is continuous at the
     * point where the straight top/bottom edges meet the end caps, which is the
     * "iOS squircle" look. Falls back to a plain rounded rect for shapes that
     * are not wide capsules.
     */
    private void buildShape(Rect mask) {
        if (clipPath.isEmpty() || shapeLeft != mask.left || shapeTop != mask.top
                || shapeWidth != mask.width() || shapeHeight != mask.height()) {
            shapeLeft = mask.left;
            shapeTop = mask.top;
            shapeWidth = mask.width();
            shapeHeight = mask.height();
            clipPath.reset();
            int w = mask.width();
            int h = mask.height();
            float r = Math.min(w, h) * 0.5f;
            if (w >= h * 2f && r > 0.5f) {
                buildG2Capsule(w, h, r);
            } else {
                clipPath.addRoundRect(new RectF(0f, 0f, w, h), r, r, Path.Direction.CW);
            }
            clipPath.offset(mask.left, mask.top);
        }
    }

    /**
     * Port of Kyant Shapes' {@code continuousCurvatureRoundedRectanglePath} for
     * a horizontal capsule. The control points are precomputed in
     * {@link #G2_CAPSULE} because the corner builder only depends on
     * width/height/radius, and a capsule always takes its (tH=1, tV=0) branch.
     */
    private void buildG2Capsule(int w, int h, float r) {
        float[] p = G2_CAPSULE;
        float x = w - r;
        float y = 0f;
        clipPath.moveTo(x + p[0] * r, y + p[1] * r);
        clipPath.cubicTo(x + p[2] * r, y + p[3] * r,
                x + p[4] * r, y + p[5] * r,
                x + p[6] * r, y + p[7] * r);
        clipPath.cubicTo(x + p[8] * r, y + p[9] * r,
                x + p[10] * r, y + p[11] * r,
                x + p[12] * r, y + p[13] * r);
        clipPath.cubicTo(x + p[14] * r, y + p[15] * r,
                x + p[16] * r, y + p[17] * r,
                x + p[18] * r, y + p[19] * r);

        x = w - r;
        y = h;
        clipPath.lineTo(x + p[18] * r, y - p[19] * r);
        clipPath.cubicTo(x + p[16] * r, y - p[17] * r,
                x + p[14] * r, y - p[15] * r,
                x + p[12] * r, y - p[13] * r);
        clipPath.cubicTo(x + p[10] * r, y - p[11] * r,
                x + p[8] * r, y - p[9] * r,
                x + p[6] * r, y - p[7] * r);
        clipPath.cubicTo(x + p[4] * r, y - p[5] * r,
                x + p[2] * r, y - p[3] * r,
                x + p[0] * r, y - p[1] * r);

        x = r;
        y = h;
        clipPath.lineTo(x - p[0] * r, y - p[1] * r);
        clipPath.cubicTo(x - p[2] * r, y - p[3] * r,
                x - p[4] * r, y - p[5] * r,
                x - p[6] * r, y - p[7] * r);
        clipPath.cubicTo(x - p[8] * r, y - p[9] * r,
                x - p[10] * r, y - p[11] * r,
                x - p[12] * r, y - p[13] * r);
        clipPath.cubicTo(x - p[14] * r, y - p[15] * r,
                x - p[16] * r, y - p[17] * r,
                x - p[18] * r, y - p[19] * r);

        x = r;
        y = 0f;
        clipPath.lineTo(x - p[18] * r, y + p[19] * r);
        clipPath.cubicTo(x - p[16] * r, y + p[17] * r,
                x - p[14] * r, y + p[15] * r,
                x - p[12] * r, y + p[13] * r);
        clipPath.cubicTo(x - p[10] * r, y + p[11] * r,
                x - p[8] * r, y + p[9] * r,
                x - p[6] * r, y + p[7] * r);
        clipPath.cubicTo(x - p[4] * r, y + p[5] * r,
                x - p[2] * r, y + p[3] * r,
                x - p[0] * r, y + p[1] * r);
        clipPath.close();
    }

    /** Translucent body of the glass; only used by the blur-only fallback. */
    private void drawTint(Canvas canvas, Rect mask) {
        boolean dark = UiTweaks.isDarkMode(getContext());
        if (tintGradient == null || tintGradientW != mask.width()
                || tintGradientH != mask.height() || tintGradientDark != dark) {
            tintGradientW = mask.width();
            tintGradientH = mask.height();
            tintGradientDark = dark;
            int top = tintColor(dark ? TINT_DARK_ALPHA : TINT_LIGHT_ALPHA, dark);
            int bottom = tintColor(dark ? TINT_DARK_ALPHA : TINT_LIGHT_ALPHA, dark);
            tintGradient = new LinearGradient(0f, mask.top, 0f, mask.bottom,
                    top, bottom, Shader.TileMode.CLAMP);
        }
        tintPaint.setShader(tintGradient);
        tintPaint.setColor(0xFFFFFFFF);
        tintPaint.setStyle(Paint.Style.FILL);
        canvas.drawPath(clipPath, tintPaint);
    }

    private static int tintColor(float alpha, boolean dark) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | (dark ? 0x00FFFFFF : 0x00E6E6EB);
    }

    /**
     * Fallback rim for the blur-only path (the normal path draws it in the
     * shader). A vertical gradient stroke clipped to the middle of the pill, so
     * only the top and bottom edges are lit — same rule as the shader: no rim on
     * the end caps, no shadow.
     */
    private void drawFlatRim(Canvas canvas, Rect mask) {
        float widthPx = dp(Prefs.DEFAULT_RIM_WIDTH);
        if (widthPx <= 0f) {
            return;
        }
        boolean dark = UiTweaks.isDarkMode(getContext());
        if (rimGradient == null || rimGradientW != mask.width()
                || rimGradientH != mask.height() || rimGradientDark != dark) {
            rimGradientW = mask.width();
            rimGradientH = mask.height();
            rimGradientDark = dark;
            float alpha = Math.max(0f, Math.min(1f, Prefs.DEFAULT_RIM_ALPHA));
            rimGradient = new LinearGradient(0f, mask.top, 0f, mask.bottom,
                    new int[]{alphaColor(alpha), 0x00FFFFFF, 0x00FFFFFF,
                            alphaColor(alpha)},
                    new float[]{0f, 0.24f, 0.76f, 1f}, Shader.TileMode.CLAMP);
        }
        int save = canvas.save();
        float inset = mask.height() * 0.55f;
        canvas.clipRect(mask.left + inset, mask.top, mask.right - inset, mask.bottom);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeJoin(Paint.Join.ROUND);
        rimPaint.setStrokeCap(Paint.Cap.ROUND);
        rimPaint.setShader(rimGradient);
        rimPaint.setStrokeWidth(widthPx * 2f);
        canvas.drawPath(clipPath, rimPaint);
        canvas.restoreToCount(save);
    }

    private static int alphaColor(float alpha) {
        int a = Math.round(Math.max(0f, Math.min(1f, alpha)) * 255f);
        return (a << 24) | 0x00FFFFFF;
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
            drawTint(canvas, mask);
            return true;
        } catch (Throwable t) {
            XLog.w("search glass blur failed: " + t);
            return false;
        }
    }

    /**
     * Blur -> lens. Returns false without painting anything when the shader or
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
                XLog.i("liquid glass active (lens + tint + top/bottom rim)");
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
     * Draws the captured backdrop into a node of {@code nodeW x nodeH} placed
     * at view coordinate {@code (left, top)}. The capture is 0.6x and padded by
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
            XLog.i("liquid glass shader compiled (tint + G2 rim)");
            return lensShader;
        } catch (Throwable t) {
            shaderUnavailable = true;
            XLog.e("liquid glass shader unavailable, blur-only glass", t);
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
        // 16.6 body tint: the 9-patch is gone, so the glass paints its own veil.
        // Light mode tints with a light grey so the capsule still reads on a
        // white page; dark mode uses white over the captured black backdrop.
        boolean dark = UiTweaks.isDarkMode(getContext());
        if (dark) {
            shader.setFloatUniform("tintColor", 1f, 1f, 1f, 1f);
        } else {
            shader.setFloatUniform("tintColor", 0.902f, 0.902f, 0.922f, 1f);
        }
        shader.setFloatUniform("tintTop", dark ? TINT_DARK_ALPHA : TINT_LIGHT_ALPHA);
        shader.setFloatUniform("tintBottom", dark ? TINT_DARK_ALPHA : TINT_LIGHT_ALPHA);
        // 16.6 rim: |normal.y| keeps the highlight on the top/bottom edges only;
        // rimFalloff sharpens it so the end caps stay clear. No inner shadow.
        shader.setFloatUniform("rimColor", 1f, 1f, 1f, 1f);
        shader.setFloatUniform("rimWidth", Math.max(0.5f, dp(Prefs.DEFAULT_RIM_WIDTH)));
        shader.setFloatUniform("rimAlpha", Prefs.DEFAULT_RIM_ALPHA);
        shader.setFloatUniform("rimFalloff", 2.0f);
    }

    /** No capture (or no hardware canvas): a tinted G2 pill, never a hole. */
    private void drawShade(Canvas canvas, Rect mask) {
        boolean dark = UiTweaks.isDarkMode(getContext());
        fallbackPaint.setShader(null);
        fallbackPaint.setColor(dark ? 0xCC1C1C1E : 0xCCF2F2F7);
        canvas.drawPath(clipPath, fallbackPaint);
        drawTint(canvas, mask);
    }

    /** Last resort when the canvas is software: an opaque G2 pill. */
    private void drawPlain(Canvas canvas, Rect mask) {
        boolean dark = UiTweaks.isDarkMode(getContext());
        fallbackPaint.setShader(null);
        fallbackPaint.setColor(dark ? 0xF21C1C1E : 0xF2F2F2F7);
        canvas.drawPath(clipPath, fallbackPaint);
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
