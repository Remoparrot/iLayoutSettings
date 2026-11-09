package com.abradant.iossettings;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Plain "about / how to use" screen (v14.8 behaviour). */
public final class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFFF2F2F7);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(32), dp(24), dp(40));
        scroll.addView(root);

        root.addView(text("iOS 设置布局", 30, Typeface.DEFAULT_BOLD));

        TextView sub = text("LSPosed 模块 · v14.8", 14, Typeface.DEFAULT);
        sub.setTextColor(0xFF8E8E93);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(6);
        root.addView(sub, subLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable cardBg = new GradientDrawable();
        cardBg.setColor(0xFFFFFFFF);
        cardBg.setCornerRadius(dp(16));
        card.setBackground(cardBg);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        cardLp.topMargin = dp(22);
        root.addView(card, cardLp);

        card.addView(text("使用方式", 17, Typeface.DEFAULT_BOLD));
        TextView body = text("在 LSPosed 里启用本模块，作用域勾选「设置」，然后重启设置。\n\n"
                + "首页入口会按 iOS 顺序重排，搜索栏移到底部，二级页面入口带图标；"
                + "背景、行样式、点击全部保持澎湃原生。", 15, Typeface.DEFAULT);
        body.setTextColor(0xFF3C3C43);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(10);
        card.addView(body, bodyLp);

        setContentView(scroll);
    }

    private TextView text(String value, int sizeSp, Typeface typeface) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTypeface(typeface);
        view.setTextColor(0xFF000000);
        return view;
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
