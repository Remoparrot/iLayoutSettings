package com.Remo.ilayoutsettings;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Draws the small widgets the synthetic cards need but a plain header row does
 * not support:
 *
 * <ul>
 *   <li>the red "1" badge on the "有软件更新可用" card (iOS shows a badge count
 *       there instead of a subtitle);</li>
 *   <li>the same red badge plus chevron wherever MIUI drew its own blue count —
 *       the "通用" row shows a blue "1" while a system update is waiting
 *       (16.6), so both rows read the same;</li>
 *   <li>a guaranteed AirPods glyph on the conditional headset card, in case the
 *       adapter prefers {@code iconRes} over {@code header.icon}.</li>
 * </ul>
 *
 * <p>The adapter is identified by the same {@code mHeaders} list the reorder
 * code writes, so no extra state has to be passed around.</p>
 */
final class HeaderOverlay {

    private static final String BADGE_TAG = "ios_update_badge";
    private static final String CHEVRON_TAG = "ios_badge_chevron";
    private static boolean hooked;

    /**
     * 16.6: the badge lives in MIUI's {@code widget_frame}. On a header row that
     * container is a vertical LinearLayout that can also hold an empty spacer —
     * the "1" used to sit ~11dp below the chevron because of it. Whatever else
     * the frame holds is hidden while our badge is up, and handed back when the
     * row is rebound to a header that has no badge.
     *
     * <p>The count itself can arrive after {@code onBindViewHolder} returns
     * (MIUI fades its badge in), so the swap is driven by a per-row pre-draw
     * check rather than only by the bind.</p>
     */
    private static final java.util.WeakHashMap<View, FrameState> frames =
            new java.util.WeakHashMap<>();

    private static final class FrameState {
        int gravity;
        int padLeft;
        int padTop;
        int padRight;
        int padBottom;
        final java.util.ArrayList<View> hidden = new java.util.ArrayList<>();
        android.view.ViewTreeObserver.OnPreDrawListener guard;
        View itemView;
        Context context;
        View nativeArrow;
        View titleContainer;
        boolean synthetic;
        boolean wantTitleShift;
        int titleTries;
    }

    private HeaderOverlay() {
    }

    static void install(ClassLoader cl) {
        if (hooked) {
            return;
        }
        Class<?> holder = XposedHelpers.findClassIfExists(
                "com.android.settings.MiuiSettings$HeaderViewHolder", cl);
        Class<?> adapter = XposedHelpers.findClassIfExists(
                "com.android.settings.MiuiSettings$HeaderAdapter", cl);
        if (holder == null || adapter == null) {
            XLog.e("HeaderOverlay: adapter / view holder not found", null);
            return;
        }
        hooked = true;
        try {
            XposedHelpers.findAndHookMethod(adapter, "onBindViewHolder", holder, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                decorate(param.thisObject, param.args[0], (Integer) param.args[1]);
                            } catch (Throwable t) {
                                XLog.e("header overlay", t);
                            }
                        }
                    });
            XLog.i("hooked HeaderAdapter.onBindViewHolder (badge / airpods icon)");
        } catch (Throwable t) {
            XLog.e("header overlay hook failed", t);
        }
    }

    private static void decorate(Object adapter, Object holder, int position) {
        Object listObject = HeaderData.field(adapter, "mHeaders");
        if (!(listObject instanceof List)) {
            return;
        }
        List<?> headers = (List<?>) listObject;
        if (position < 0 || position >= headers.size()) {
            return;
        }
        Object header = headers.get(position);
        View itemView = (View) HeaderData.field(holder, "itemView");
        if (header == null || itemView == null) {
            return;
        }
        Context context = itemView.getContext();
        ViewGroup frame = itemView.findViewById(android.R.id.widget_frame);
        if (frame != null) {
            boolean synthetic = HeaderData.isUpdateHeader(header);
            if (synthetic) {
                showBadge(frame, context, "1");
                FrameState state = ensureGuard(frame, itemView, context);
                state.synthetic = true;
                state.wantTitleShift = true;
            } else {
                // MIUI's own count is replaced by the same red badge, so both
                // rows read the same. A previous bind of this recycled row may
                // already have hidden the native badge.
                TextView count = nativeCount(frame, frames.get(frame));
                if (count != null) {
                    showBadge(frame, context, text(count));
                    FrameState state = ensureGuard(frame, itemView, context);
                    ensureChevron(itemView, frame, context, state);
                } else if (HeaderData.isGeneralHeader(header)) {
                    // The count can still be on its way; the guard swaps it in.
                    ensureGuard(frame, itemView, context);
                } else {
                    clearBadge(itemView, frame);
                }
            }
        }
        if (HeaderData.isAirpodsHeader(header)) {
            applyIcon(itemView, "ic_sub_airpods");
        } else if (HeaderData.isICloudHeader(header)) {
            applyIcon(itemView, "ic_cloud");
        }
    }

    private static void applyIcon(View itemView, String name) {
        View icon = itemView.findViewById(android.R.id.icon);
        if (icon instanceof ImageView) {
            android.graphics.drawable.Drawable drawable =
                    HeaderData.cardDrawable(itemView.getContext(), name);
            if (drawable != null) {
                ((ImageView) icon).setImageDrawable(drawable);
                icon.setVisibility(View.VISIBLE);
            }
        }
    }

    private static String text(TextView view) {
        CharSequence value = view.getText();
        String text = value == null ? "" : value.toString().trim();
        return text.length() == 0 ? "1" : text;
    }

    /**
     * The native badge, if this row has one: a TextView holding nothing but a
     * one- or two-digit number. Returns null for ordinary rows, whose right-hand
     * text is a summary ("已开启", a Wi-Fi name, ...). A view already hidden by a
     * previous bind of this row still counts, otherwise the recycled row would
     * flip back to MIUI's blue badge on every rebind.
     */
    private static TextView nativeCount(ViewGroup frame, FrameState state) {
        for (int i = 0; i < frame.getChildCount(); i++) {
            View child = frame.getChildAt(i);
            if (BADGE_TAG.equals(child.getTag())
                    || (state != null && child == state.nativeArrow)
                    || !(child instanceof TextView)) {
                continue;
            }
            boolean hiddenByUs = state != null && state.hidden.contains(child);
            if (child.getVisibility() != View.VISIBLE && !hiddenByUs) {
                continue;
            }
            CharSequence text = ((TextView) child).getText();
            if (text == null) {
                continue;
            }
            String value = text.toString().trim();
            if (value.length() == 0 || value.length() > 2) {
                continue;
            }
            boolean digits = true;
            for (int c = 0; c < value.length(); c++) {
                if (!Character.isDigit(value.charAt(c))) {
                    digits = false;
                    break;
                }
            }
            if (digits) {
                return (TextView) child;
            }
        }
        return null;
    }

    private static FrameState state(ViewGroup frame) {
        FrameState state = frames.get(frame);
        if (state == null) {
            state = new FrameState();
            state.gravity = frame instanceof LinearLayout
                    ? ((LinearLayout) frame).getGravity() : 0;
            state.padLeft = frame.getPaddingLeft();
            state.padTop = frame.getPaddingTop();
            state.padRight = frame.getPaddingRight();
            state.padBottom = frame.getPaddingBottom();
            frames.put(frame, state);
        }
        return state;
    }

    private static void showBadge(ViewGroup frame, Context context, String text) {
        FrameState state = state(frame);
        // The frame hands the badge to the row; the row already centres the
        // frame, so zero padding + a centred child keeps "1" level with the
        // chevron.
        if (frame instanceof LinearLayout) {
            ((LinearLayout) frame).setGravity(Gravity.CENTER_VERTICAL);
        }
        frame.setPadding(0, 0, 0, 0);
        frame.setVisibility(View.VISIBLE);
        frame.setMinimumWidth(0);

        View existing = frame.findViewWithTag(BADGE_TAG);
        if (existing instanceof TextView) {
            ((TextView) existing).setText(text);
        } else {
            frame.addView(createBadge(context, text), badgeParams(frame, context));
        }
        hideOthers(frame, frame.findViewWithTag(BADGE_TAG), state);
    }

    /**
     * One pre-draw check per badge row: MIUI fades its count in after the bind,
     * so the badge may have to be created later, and its count may change.
     */
    private static FrameState ensureGuard(final ViewGroup frame, final View itemView,
                                          final Context context) {
        final FrameState state = state(frame);
        state.itemView = itemView;
        state.context = context;
        if (state.guard != null) {
            return state;
        }
        final android.view.ViewTreeObserver.OnPreDrawListener guard =
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        try {
                            if (!state.synthetic
                                    && frame.findViewWithTag(BADGE_TAG) == null) {
                                final TextView count = nativeCount(frame, state);
                                if (count == null) {
                                    return true;
                                }
                                final String text = text(count);
                                frame.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            showBadge(frame, context, text);
                                            ensureChevron(itemView, frame, context, state);
                                        } catch (Throwable t) {
                                            XLog.w("badge swap: " + t);
                                        }
                                    }
                                });
                                return true;
                            }
                            View badge = frame.findViewWithTag(BADGE_TAG);
                            if (badge == null) {
                                return true;
                            }
                            if (state.nativeArrow != null
                                    && state.nativeArrow.getVisibility() != View.GONE) {
                                state.nativeArrow.setVisibility(View.GONE);
                            }
                            hideOthers(frame, badge, state);
                            if (state.wantTitleShift && state.titleTries < 6) {
                                shiftTitle(state);
                            }
                        } catch (Throwable t) {
                            XLog.w("badge guard: " + t);
                        }
                        return true;
                    }
                };
        try {
            frame.getViewTreeObserver().addOnPreDrawListener(guard);
            state.guard = guard;
        } catch (Throwable t) {
            XLog.w("badge guard install failed: " + t);
        }
        return state;
    }

    private static void hideOthers(ViewGroup frame, View badge, FrameState state) {
        for (int i = 0; i < frame.getChildCount(); i++) {
            View child = frame.getChildAt(i);
            if (child == badge || child.getVisibility() == View.GONE) {
                continue;
            }
            if (!state.hidden.contains(child)) {
                state.hidden.add(child);
            }
            child.setVisibility(View.GONE);
        }
    }

    /**
     * The synthetic update card has no icon, but MIUI still indents its title by
     * the icon column. Slide the title block left so the row balances: the same
     * air on the left as the chevron leaves on the right.
     */
    private static void shiftTitle(FrameState state) {
        View itemView = state.itemView;
        if (itemView == null || itemView.getWidth() <= 0) {
            return;
        }
        View title = itemView.findViewById(android.R.id.title);
        if (title == null || !(title.getParent() instanceof View)) {
            return;
        }
        View container = (View) title.getParent();
        if (container == itemView || title.getWidth() <= 0) {
            // Not measured yet — the title's own offset inside the container is
            // still 0, and shifting now would leave the text off by that padding.
            return;
        }
        state.titleTries++;
        // Where the text would sit now (layout position + what we already moved).
        int current = container.getLeft() + title.getLeft()
                + Math.round(container.getTranslationX());
        int target = dp(itemView.getContext(), 16);
        int dx = target - current;
        if (Math.abs(dx) <= 1) {
            state.titleTries = 6;
            return;
        }
        container.setTranslationX(container.getTranslationX() + dx);
        state.titleContainer = container;
    }

    /**
     * Puts the chevron of a count row back. MIUI hides the row's own arrow when a
     * count takes the widget slot, so its drawable is reused in the same slot —
     * the arrow keeps looking exactly like the one Settings draws on every other
     * row.
     */
    private static void ensureChevron(View itemView, ViewGroup frame, Context context,
                                      FrameState state) {
        android.graphics.drawable.Drawable arrowDrawable = null;
        android.content.res.ColorStateList tint = null;
        try {
            int id = itemView.getResources().getIdentifier(
                    "arrow_right", "id", "com.android.settings");
            View arrow = id == 0 ? null : itemView.findViewById(id);
            if (arrow != null) {
                state.nativeArrow = arrow;
                if (arrow instanceof ImageView) {
                    ImageView nativeArrow = (ImageView) arrow;
                    android.graphics.drawable.Drawable drawable = nativeArrow.getDrawable();
                    if (drawable != null) {
                        android.graphics.drawable.Drawable.ConstantState cs =
                                drawable.getConstantState();
                        arrowDrawable = cs == null ? null
                                : cs.newDrawable(context.getResources()).mutate();
                    }
                    tint = nativeArrow.getImageTintList();
                }
                if (arrow.getVisibility() != View.GONE) {
                    arrow.setVisibility(View.GONE);
                }
            }
        } catch (Throwable t) {
            XLog.w("badge row arrow: " + t);
        }
        if (itemView.findViewWithTag(CHEVRON_TAG) != null) {
            return;
        }
        ViewGroup parent = frame.getParent() instanceof ViewGroup
                ? (ViewGroup) frame.getParent() : null;
        if (!(parent instanceof LinearLayout)) {
            return;
        }
        ImageView chevron = new ImageView(context);
        chevron.setTag(CHEVRON_TAG);
        chevron.setImageDrawable(arrowDrawable != null ? arrowDrawable : chevronDrawable(context));
        if (tint != null) {
            chevron.setImageTintList(tint);
        }
        chevron.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                dp(context, 8.6f), dp(context, 13.8f));
        lp.gravity = Gravity.CENTER_VERTICAL;
        lp.setMarginStart(dp(context, 14.8f));
        parent.addView(chevron, parent.indexOfChild(frame) + 1, lp);
        XLog.i("badge row: chevron from settings (native=" + (arrowDrawable != null) + ")");
    }

    private static void clearBadge(View itemView, ViewGroup frame) {
        View badge = frame.findViewWithTag(BADGE_TAG);
        if (badge != null) {
            frame.removeView(badge);
        }
        View chevron = itemView.findViewWithTag(CHEVRON_TAG);
        if (chevron != null && chevron.getParent() instanceof ViewGroup) {
            ((ViewGroup) chevron.getParent()).removeView(chevron);
        }
        FrameState state = frames.remove(frame);
        if (state == null) {
            return;
        }
        if (state.titleContainer != null) {
            state.titleContainer.setTranslationX(0f);
            state.titleContainer = null;
        }
        if (state.guard != null) {
            try {
                frame.getViewTreeObserver().removeOnPreDrawListener(state.guard);
            } catch (Throwable t) {
                XLog.w("badge guard remove failed: " + t);
            }
            state.guard = null;
        }
        for (int i = 0; i < state.hidden.size(); i++) {
            View child = state.hidden.get(i);
            // The adapter has just rebound this row: a child that still carries
            // text is a summary the row wants to show, an empty one is a badge
            // that no longer applies.
            CharSequence text = child instanceof TextView
                    ? ((TextView) child).getText() : null;
            boolean keep = text != null && text.toString().trim().length() > 0;
            child.setVisibility(keep ? View.VISIBLE : View.GONE);
        }
        state.hidden.clear();
        if (frame instanceof LinearLayout) {
            ((LinearLayout) frame).setGravity(state.gravity);
        }
        frame.setPadding(state.padLeft, state.padTop, state.padRight, state.padBottom);
    }

    private static TextView createBadge(Context context, String text) {
        TextView badge = new TextView(context);
        badge.setTag(BADGE_TAG);
        badge.setText(text);
        badge.setTextColor(0xFFFFFFFF);
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        badge.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        badge.setGravity(Gravity.CENTER);
        badge.setIncludeFontPadding(false);
        GradientDrawable background = new GradientDrawable();
        background.setShape(GradientDrawable.OVAL);
        background.setColor(0xFFFF3B30);
        badge.setBackground(background);
        return badge;
    }

    private static ViewGroup.LayoutParams badgeParams(ViewGroup frame, Context context) {
        int size = dp(context, 24);
        int gap = dp(context, 8);
        if (frame instanceof LinearLayout) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(size, size);
            lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
            lp.setMarginEnd(gap);
            return lp;
        }
        if (frame instanceof RelativeLayout) {
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(size, size);
            lp.addRule(RelativeLayout.CENTER_VERTICAL);
            lp.addRule(RelativeLayout.ALIGN_PARENT_END);
            lp.setMarginEnd(gap);
            return lp;
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        lp.gravity = Gravity.CENTER_VERTICAL | Gravity.END;
        lp.setMarginEnd(gap);
        return lp;
    }

    /** Fallback ">" if the row's own arrow drawable cannot be read. */
    private static android.graphics.drawable.Drawable chevronDrawable(Context context) {
        final android.graphics.Paint paint =
                new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(android.graphics.Paint.Style.STROKE);
        paint.setStrokeWidth(dp(context, 1.8f));
        paint.setStrokeCap(android.graphics.Paint.Cap.ROUND);
        paint.setStrokeJoin(android.graphics.Paint.Join.ROUND);
        paint.setColor(UiTweaks.isDarkMode(context) ? 0xFF8E8E93 : 0xFFC4C4C6);
        final float arm = dp(context, 3.4f);
        return new android.graphics.drawable.Drawable() {
            @Override
            public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect bounds = getBounds();
                float cx = bounds.exactCenterX();
                float cy = bounds.exactCenterY();
                android.graphics.Path path = new android.graphics.Path();
                path.moveTo(cx - arm, cy - arm * 1.25f);
                path.lineTo(cx + arm * 0.6f, cy);
                path.lineTo(cx - arm, cy + arm * 1.25f);
                canvas.drawPath(path, paint);
            }

            @Override
            public void setAlpha(int alpha) {
                paint.setAlpha(alpha);
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter filter) {
                paint.setColorFilter(filter);
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        };
    }

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
