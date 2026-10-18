package com.skensel.stopad;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Original matte surfaces and illustration; no reference artwork is bundled. */
final class MatteUi {
    static final int BG = 0xff222633, TEXT = 0xfff6f5fb, MUTED = 0xffa8afc1,
            PURPLE = 0xffb29aff, TEAL = 0xff68decd;
    private MatteUi() { }
    static int dp(Context c, float value) { return Math.round(value * c.getResources().getDisplayMetrics().density); }
    static GradientDrawable surface(Context c, boolean accent, float radius) {
        GradientDrawable drawable = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                accent ? new int[]{0xffb49aff, 0xff8762e2, 0xff6543ba} : new int[]{0xff3c4255, 0xff2e3445, 0xff272c3a});
        drawable.setCornerRadius(dp(c, radius));
        drawable.setStroke(dp(c, 1), accent ? 0xffc7b2ff : 0xff495166);
        return drawable;
    }
    static void card(View view, float radius) {
        view.setBackground(surface(view.getContext(), false, radius));
        view.setElevation(dp(view.getContext(), 6));
    }
    static TextView text(Context c, CharSequence value, float size, int color, boolean bold) {
        TextView text = new TextView(c); text.setText(value); text.setTextSize(size); text.setTextColor(color);
        text.setFontFeatureSettings("kern");
        text.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
        text.setIncludeFontPadding(false); return text;
    }
    static Button button(Context c, CharSequence label, boolean accent) {
        Button button = new Button(c); button.setText(label); button.setTextSize(15);
        button.setTextColor(TEXT); button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(0); button.setMinimumHeight(0); button.setMinWidth(0); button.setMinimumWidth(0);
        button.setPadding(dp(c, 16), dp(c, 15), dp(c, 16), dp(c, 15));
        GradientDrawable background = surface(c, accent, 15);
        GradientDrawable mask = new GradientDrawable(); mask.setColor(Color.WHITE); mask.setCornerRadius(dp(c, 15));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x25ffffff), background, mask));
        button.setElevation(dp(c, accent ? 8 : 3)); return button;
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
    static final class Hero extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Picture picture;
        Hero(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onSizeChanged(int w, int h, int oldW, int oldH) {
            super.onSizeChanged(w, h, oldW, oldH);
            picture = new Picture();
            drawArtwork(picture.beginRecording(w, h));
            picture.endRecording();
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (picture != null) canvas.drawPicture(picture);
        }
        private void drawArtwork(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            Path stripe = new Path(); stripe.moveTo(w * .72f, 0); stripe.lineTo(w, 0); stripe.lineTo(w, h); stripe.lineTo(w * .45f, h); stripe.close();
            p.setShader(new LinearGradient(w, 0, w * .45f, h, new int[]{0xff7b66ad, 0xff5b4788, 0xff454066}, null, Shader.TileMode.CLAMP));
            canvas.drawPath(stripe, p); p.setShader(null);
            canvas.save();
            float scale = h / 245f;
            canvas.translate(w * .77f, h * .52f); canvas.scale(scale, scale); canvas.rotate(-13);
            p.setColor(0x65000000); p.setMaskFilter(new BlurMaskFilter(10, BlurMaskFilter.Blur.NORMAL));
            canvas.drawOval(-60, 77, 76, 97, p); p.setMaskFilter(null);
            Path shield = shield(0, 0);
            canvas.save(); canvas.translate(9, 9);
            p.setShader(new LinearGradient(-50, -75, 45, 85, 0xff31394a, 0xff171d2a, Shader.TileMode.CLAMP));
            canvas.drawPath(shield, p); canvas.restore();
            p.setShadowLayer(14, 2, 12, 0x700c1020);
            p.setShader(new LinearGradient(-55, -80, 55, 90, new int[]{0xffa7afc4, 0xff636c85, 0xff343d55}, null, Shader.TileMode.CLAMP));
            canvas.drawPath(shield, p); p.clearShadowLayer();
            p.setShader(null); p.setColor(0xffc0c5d4); p.setStrokeWidth(1.2f); p.setStyle(Paint.Style.STROKE); canvas.drawPath(shield, p); p.setStyle(Paint.Style.FILL);
            canvas.save(); canvas.scale(.85f, .86f);
            p.setShader(new LinearGradient(-40, -65, 45, 85, new int[]{0xff48516b, 0xff222b42, 0xff323853}, null, Shader.TileMode.CLAMP));
            canvas.drawPath(shield, p); canvas.restore();
            Path bolt = new Path(); bolt.moveTo(6, -43); bolt.lineTo(-27, 4); bolt.lineTo(-4, 1); bolt.lineTo(-12, 44);
            bolt.lineTo(30, -12); bolt.lineTo(7, -8); bolt.lineTo(16, -43); bolt.close();
            p.setShadowLayer(10, 0, 1, 0x705de3cc);
            p.setShader(new LinearGradient(-25, -40, 28, 45, 0xffa0f7df, 0xff42a99e, Shader.TileMode.CLAMP)); canvas.drawPath(bolt, p); p.clearShadowLayer(); p.setShader(null);
            canvas.restore();
            // Floating matte tiles echo the product-card depth in the reference.
            canvas.save(); canvas.translate(w * .92f, h * .17f); canvas.rotate(12);
            float tile = h * .085f;
            p.setColor(0xff40485e); p.setShadowLayer(9, 0, 6, 0x66000000); canvas.drawRoundRect(-tile, -tile, tile, tile, tile * .32f, tile * .32f, p); p.clearShadowLayer();
            p.setColor(0xffc0b0ee); p.setStrokeWidth(Math.max(2, h * .008f)); p.setStrokeCap(Paint.Cap.ROUND);
            canvas.drawLine(-tile * .4f, 0, tile * .4f, 0, p); canvas.drawLine(0, -tile * .4f, 0, tile * .4f, p); canvas.restore();
        }
        private Path shield(float x, float y) {
            Path path = new Path(); path.moveTo(x, y - 80); path.lineTo(x + 59, y - 57);
            path.lineTo(x + 53, y + 14); path.cubicTo(x + 50, y + 48, x + 21, y + 71, x, y + 84);
            path.cubicTo(x - 21, y + 71, x - 50, y + 48, x - 53, y + 14); path.lineTo(x - 59, y - 57); path.close(); return path;
        }
    }
}
