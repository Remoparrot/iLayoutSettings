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
 *   <li>a guaranteed AirPods glyph on the conditional headset card, in case the
 *       adapter prefers {@code iconRes} over {@code header.icon}.</li>
 * </ul>
 *
 * <p>The adapter is identified by the same {@code mHeaders} list the reorder
 * code writes, so no extra state has to be passed around.</p>
 */
final class HeaderOverlay {

    private static final String BADGE_TAG = "ios_update_badge";
    private static boolean hooked;

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
            View existing = frame.findViewWithTag(BADGE_TAG);
            if (HeaderData.isUpdateHeader(header)) {
                frame.setVisibility(View.VISIBLE);
                frame.setMinimumWidth(0);
                if (existing == null) {
                    frame.addView(createBadge(context), badgeParams(frame, context));
                } else {
                    existing.setVisibility(View.VISIBLE);
                }
            } else if (existing != null) {
                frame.removeView(existing);
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

    private static TextView createBadge(Context context) {
        TextView badge = new TextView(context);
        badge.setTag(BADGE_TAG);
        badge.setText("1");
        badge.setTextColor(0xFFFFFFFF);
        badge.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 11.5f);
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
        int size = dp(context, 20);
        int gap = dp(context, 6);
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

    private static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
