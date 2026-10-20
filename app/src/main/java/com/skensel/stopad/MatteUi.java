package com.skensel.stopad;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Original matte surfaces and icons; no reference artwork is bundled. */
final class MatteUi {
    static final int BG = 0xff201f38, TEXT = 0xfff7f5ff, MUTED = 0xffb5b0cc,
            PURPLE = 0xffc1a1ff, TEAL = 0xff72e4df;
    private MatteUi() { }
    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable surface(Context c, boolean accent, float radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                accent ? new int[]{0xff9871e5, 0xff8556c7, 0xff6e4eb1} : new int[]{0xff44415f, 0xff383650, 0xff302e48});
        drawable.setCornerRadius(dp(c, radius));
        drawable.setStroke(dp(c, 1), accent ? 0xffb194ee : 0xff57516f);
        return drawable;
    }
    static void card(View view, float radius) {
        view.setBackground(surface(view.getContext(), false, radius));
        view.setElevation(dp(view.getContext(), 5));
    }
    static TextView text(Context c, CharSequence value, float size, int color, boolean bold) {
        TextView text = new TextView(c); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setFontFeatureSettings("kern");
        text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        text.setIncludeFontPadding(false); return text;
    }
    static Button button(Context c, CharSequence label, boolean accent) {
        Button button = new Button(c); button.setText(label); button.setTextSize(15);
        button.setTextColor(accent ? Color.WHITE : TEXT); button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(0); button.setMinimumHeight(0); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15));
        GradientDrawable background = surface(c, accent, 15);
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(dp(c, 15));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(accent ? 0x25ffffff : 0x15000000), background, mask));
        button.setElevation(dp(c, accent ? 3 : 0)); return button;
    }
    @android.annotation.SuppressLint("ViewConstructor") // Created programmatically with an explicit glyph.
    static final class Icon extends View {
        final String kind; int color;
        final Path path = new Path();
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Icon(Context c, String kind, int color) { super(c); this.kind = kind; this.color = color; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); canvas.save();
            float scale = Math.min(getWidth(), getHeight()) / 24f;
            canvas.translate((getWidth() - 24 * scale) / 2, (getHeight() - 24 * scale) / 2); canvas.scale(scale, scale);
            paint.setColor(color); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.7f);
            paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
            path.reset();
            switch (kind) {
                case "shield":
                    path.moveTo(12, 3); path.lineTo(20, 6); path.lineTo(19, 14); path.quadTo(18, 18, 12, 21);
                    path.quadTo(6, 18, 5, 14); path.lineTo(4, 6); path.close(); canvas.drawPath(path, paint);
                    canvas.drawLine(9, 12, 11, 14, paint); canvas.drawLine(11, 14, 15, 9, paint); break;
                case "games":
                    path.moveTo(7, 7); path.quadTo(3, 7, 2.5f, 17); path.quadTo(3, 21, 8, 16);
                    path.lineTo(16, 16); path.quadTo(21, 21, 21.5f, 17); path.quadTo(21, 7, 17, 7); path.close();
                    canvas.drawPath(path, paint); canvas.drawLine(6, 11, 10, 11, paint); canvas.drawLine(8, 9, 8, 13, paint);
                    canvas.drawCircle(16, 10, .65f, paint); canvas.drawCircle(18, 13, .65f, paint); break;
                case "settings":
                    canvas.drawLine(4, 6, 20, 6, paint); canvas.drawLine(4, 12, 20, 12, paint); canvas.drawLine(4, 18, 20, 18, paint);
                    paint.setStyle(Paint.Style.FILL); canvas.drawRoundRect(7, 3, 10, 9, 1, 1, paint);
                    canvas.drawRoundRect(14, 9, 17, 15, 1, 1, paint); canvas.drawRoundRect(8, 15, 11, 21, 1, 1, paint); break;
                case "arrow": canvas.drawLine(5, 12, 19, 12, paint); canvas.drawLine(14, 7, 19, 12, paint); canvas.drawLine(19, 12, 14, 17, paint); break;
                case "plus": canvas.drawLine(5, 12, 19, 12, paint); canvas.drawLine(12, 5, 12, 19, paint); break;
                case "list":
                    for (int y = 6; y <= 18; y += 6) { canvas.drawCircle(4, y, .5f, paint); canvas.drawLine(9, y, 20, y, paint); } break;
                case "info": canvas.drawCircle(12, 12, 9, paint); canvas.drawPoint(12, 8, paint); canvas.drawLine(12, 11, 12, 16, paint); break;
                case "refresh":
                    canvas.drawArc(4, 4, 20, 20, 35, 280, false, paint);
                    canvas.drawLine(20, 4, 20, 10, paint); canvas.drawLine(14, 9, 20, 10, paint); break;
                default: canvas.drawCircle(12, 12, 7, paint); break;
            }
            canvas.restore();
        }
    }
}
