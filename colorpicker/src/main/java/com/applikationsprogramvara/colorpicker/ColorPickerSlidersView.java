package com.applikationsprogramvara.colorpicker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.util.AttributeSet;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

public class ColorPickerSlidersView extends RelativeLayout {

    private ImageView ivColorBefore;
    private TextView tvColorBefore;
    private ImageView ivColorAfter;
    private TextView tvColorAfter;
    private SliderWithNumber snHue;
    private SliderWithNumber snSat;
    private SliderWithNumber snBri;
    private SliderWithNumber snTra;

    private float hue;
    private float saturation;
    private float lightness;
    private float transparency;

    private int initialColor;
    private boolean transparentColorsAvailable;
    private boolean manipulatorsInitialized;

    public ColorPickerSlidersView(Context context) {
        super(context);
        init(context, null);
    }

    public ColorPickerSlidersView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public ColorPickerSlidersView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        TypedArray attributes = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerSlidersView);
        if (initialColor == 0)
            initialColor = attributes.getColor(R.styleable.ColorPickerSlidersView_initialColor, Color.RED);
        if (!transparentColorsAvailable)
            transparentColorsAvailable = attributes.getBoolean(R.styleable.ColorPickerSlidersView_transparency, false);
        attributes.recycle();

        View view = inflate(getContext(), R.layout.colorpickersliders, null);
        addView(view);

        ivColorBefore = view.findViewById(R.id.ivColorBefore);
        tvColorBefore = view.findViewById(R.id.tvColorBefore);
        ivColorAfter = view.findViewById(R.id.ivColorAfter);
        tvColorAfter = view.findViewById(R.id.tvColorAfter);

        snHue = view.findViewById(R.id.snHue);
        snSat = view.findViewById(R.id.snSat);
        snBri = view.findViewById(R.id.snBri);
        snTra = view.findViewById(R.id.snTra);

        snHue.setOnProgressChangeListener((int progress, boolean fromUser) -> {
            if (fromUser)
                changeColor(progress, -1, -1, -1);

            snHue.setColorFilter(Color.HSVToColor(new float[]{progress, 1f, 1f}));

            changeGradientSaturation();
            changeGradientBrightness();
            changeGradientTransparency();
        });

        snSat.setOnProgressChangeListener((int progress, boolean fromUser) -> {
            if (fromUser)
                changeColor(-1, snSat.normalize(progress), -1, -1);
            changeGradientBrightness();
            changeGradientTransparency();
        });

        snBri.setOnProgressChangeListener((int progress, boolean fromUser) -> {
            if (fromUser)
                changeColor(-1, -1, snBri.normalize(progress), -1);
            changeGradientSaturation();
            changeGradientTransparency();
        });

        snTra.setOnProgressChangeListener((int progress, boolean fromUser) -> {
            if (fromUser)
                changeColor(-1, -1, -1, snTra.normalize(progress));
        });

        snHue.setOnLayoutChangeListener(this::changeGradientHue);

        snSat.setOnLayoutChangeListener((sb) -> changeGradientSaturation());

        snBri.setOnLayoutChangeListener((sb) -> changeGradientBrightness());

        snTra.setOnLayoutChangeListener((sb) -> changeGradientTransparency());

        View.OnClickListener resetColor = view2 -> setInitialParameters(initialColor, transparentColorsAvailable);
        ivColorBefore.setOnClickListener(resetColor);
        tvColorBefore.setOnClickListener(resetColor);

        View.OnClickListener confirmColor = view2 -> {}; //nothing yet
        ivColorAfter.setOnClickListener(confirmColor);
        tvColorAfter.setOnClickListener(confirmColor);

        setInitialParameters(initialColor, transparentColorsAvailable);

        manipulatorsInitialized = false;
    }

    public void setInitialParameters(int initialColor, boolean transparentColorsAvailable) {
        this.initialColor = initialColor;
        this.transparentColorsAvailable = transparentColorsAvailable;

        float[] hsv = new float[3];
        Color.colorToHSV(initialColor, hsv);

        hue = hsv[0];
        saturation = hsv[1];
        lightness = hsv[2];
        transparency = transparentColorsAvailable ? (float) Color.alpha(initialColor) / 255f : 1f;

        snTra.setVisibility(transparentColorsAvailable ? View.VISIBLE : View.GONE);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed)
            initManipulators();
    }

    private void initManipulators() {
        if (manipulatorsInitialized) return;

        int color = getColor();

        snHue.setProgress(hue / 360f);
        snSat.setProgress(saturation);
        snBri.setProgress(lightness);
        snTra.setProgress(transparency);
        updateManipulators(color);
        updateTextAndColorPatch(color, ivColorBefore, tvColorBefore, true);

        manipulatorsInitialized = true;
    }

    private void updateManipulators(int color) {
        snSat.setColorFilter(Color.HSVToColor(new float[] {hue, saturation, lightness}));
        snBri.setColorFilter(Color.HSVToColor(new float[] {hue, saturation, lightness}));
        snTra.setColorFilter(color);

        updateTextAndColorPatch(color, ivColorAfter, tvColorAfter, false);
    }

    private void updateTextAndColorPatch(int color, ImageView iv, TextView tv, boolean before) {

        // option 1
//        ShapeDrawable shape = new ShapeDrawable(mask);
//        shape.getPaint().setColor(color);
//        iv.setBackground(shape);

//        iv.setBackgroundColor(color);

        if (iv.getWidth() == 0 || iv.getHeight() == 0) return;

        // create bitmap
        Bitmap bitmap = Bitmap.createBitmap(iv.getWidth(), iv.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // draw checker if color is transparent
        if (Color.alpha(color) < 0xFF) {
            Drawable checkerboard = ContextCompat.getDrawable(getContext(), R.drawable.checkerboard_background);
            checkerboard.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            checkerboard.draw(canvas);
        }

        canvas.drawColor(color);

        Bitmap maskBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas maskCanvas = new Canvas(maskBitmap);

        // prepare a shape for outline an mask
        float rLeft = before ? getResources().getDisplayMetrics().density * 10 : 0;
        float rRight = before ? 0 : getResources().getDisplayMetrics().density * 10;

        RoundRectShape maskShape = new RoundRectShape(new float[] {rLeft, rLeft, rRight, rRight, rRight, rRight, rLeft, rLeft}, null, null);
//        maskShape.resize(maskBitmap.getWidth(), maskBitmap.getHeight());
        ShapeDrawable shapeDrawable = new ShapeDrawable(maskShape);
        shapeDrawable.setBounds((int) -rRight, 0, (int) (bitmap.getWidth() + rLeft), bitmap.getHeight());
        shapeDrawable.draw(maskCanvas);

        shapeDrawable.getPaint().set(SliderWithNumber.getPaintOutline(getContext()));
        shapeDrawable.draw(canvas);

        // mask the final bitmap and assign to the patch
        canvas.drawBitmap(maskBitmap, 0, 0, new Paint() {{ setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN)); }});
        iv.setImageBitmap(bitmap);
//        iv.setScaleType(ImageView.ScaleType.CENTER);
//        iv.setBackground(null);

        tv.setText(getContext().getString(R.string.color_summary,
                (int) (hue),
                (int) (saturation * 100f),
                (int) (lightness * 100f),
                transparentColorsAvailable ? "\n" + ((int) (transparency * 100f)) + "%" : ""));
    }

    public int getColor() {
        return Color.HSVToColor((int) (transparency * 255f), new float[] {hue, saturation, lightness} );
    }

    private void changeColor(float h, float s, float l, float t) {
        if (h >= 0) // 0-360
            hue = h;

        if (s >= 0) // 0-1
            saturation = s;

        if (l >= 0) // 0-1
            lightness = l;

        if (t >= 0) // 0-1
            transparency = t;

        int color = getColor();
        updateManipulators(color);
    }

    private void changeGradientHue(SeekBar sb) {
        Rect r = sb.getThumb().getBounds();
        LinearGradient rainbow = new LinearGradient(0.f, 0.f, sb.getWidth() - r.width(), sb.getHeight(), //SweepGradient
                new int[] {
                        0xFFFF0000, // 1 red
                        0xFFFFFF00, // 2 red + green = yellow
                        0xFF00FF00, // 3 green
                        0xFF00FFFF, // 4 green + blue = cyan
                        0xFF0000FF, // 5 blue
                        0xFFFF00FF, // 6 blue + red = magenta
                        0xFFFF0000  // 7 back to red
                },
                null, Shader.TileMode.CLAMP);


        ShapeDrawable shape = new ShapeDrawable(SliderWithNumber.getRect(sb));
        shape.getPaint().setShader(rainbow);

        sb.setProgressDrawable(shape);
    }

    private void changeGradientSaturation() {
        snSat.changeGradient(
                Color.HSVToColor(new float[]{hue, 0f, lightness}), // from unsaturated = gray
                Color.HSVToColor(new float[]{hue, 1f, lightness})  // to saturated
        );
    }

    private void changeGradientBrightness() {
        snBri.changeGradient(
                Color.BLACK,                                        // from black
                Color.HSVToColor(new float[] {hue, saturation, 1f}) // to the lightest
        );
    }

    private void changeGradientTransparency() {
        snTra.changeGradient(
                Color.TRANSPARENT,                                         // from transparent
                Color.HSVToColor(new float[] {hue, saturation, lightness}) // to opaque
        );
    }

}
