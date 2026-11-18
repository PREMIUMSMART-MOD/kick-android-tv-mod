package eu.kickmod.patcher;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Natywny interfejs TV: czytelny z kanapy, bez renderowania rozmycia w petli. */
final class StudioLayout {
    static final int GREEN = 0xff53fc18, WHITE = 0xfff2f5f2, GREY = 0xffb1bdb4;
    private static final int MUTED = 0xff92a398;
    final Activity activity;
    final LinearLayout root;
    final Button download, choose, downloads, install, save, cancel, haveFile, updates;
    final Button[] nav = new Button[3];
    final LinearLayout[] page = new LinearLayout[3];
    final TextView status, detail, percent;
    final ProgressBar progress;
    private final Typeface display, body, medium;
    private final SupportProfile support;
    private final ProfilePanel[] panels = new ProfilePanel[3];
    private final TextView[] phases = new TextView[4];
    private final TextView workTitle;
    private final LinearLayout work;
    private boolean working, presenting;
    IntConsumer select = value -> {};

    StudioLayout(Activity activity, SupportProfile support) {
        this.activity = activity;
        this.support = support;
        display = font("Sora", 500);
        body = font("Onest", 400);
        medium = font("Onest", 600);
        root = column();
        root.setPadding(d(42), d(26), d(42), d(22));
        root.setBackground(new AmbientBackground());
        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(header, new LinearLayout.LayoutParams(-1, d(46)));
        ImageView logo = new ImageView(activity);
        logo.setImageResource(activity.getResources().getIdentifier("wordmark", "drawable", activity.getPackageName()));
        header.addView(logo, new LinearLayout.LayoutParams(d(84), d(32)));
        LinearLayout brand = column();
        brand.setPadding(d(15), 0, 0, 0);
        brand.addView(text("Android TV MOD", 13, WHITE, medium));
        TextView caption = text("ON-DEVICE PATCHER", 9, MUTED, body);
        caption.setLetterSpacing(.13f);
        brand.addView(caption);
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        updates = button("Updates", false);
        LinearLayout.LayoutParams updateSize = new LinearLayout.LayoutParams(d(107), d(38));
        updateSize.rightMargin = d(12);
        header.addView(updates, updateSize);

        LinearLayout steps = row();
        steps.setPadding(d(4), d(4), d(4), d(4));
        steps.setBackground(glass(0xff121b16, 0xff304236, 24));
        header.addView(steps);
        String[] labels = {"01  Get APKM", "02  Patch", "03  Install"};
        for (int i = 0; i < nav.length; i++) {
            final int section = i;
            nav[i] = button(labels[i], false);
            nav[i].setTextSize(12);
            nav[i].setBackground(buttonBackground(false, true));
            steps.addView(nav[i], new LinearLayout.LayoutParams(d(i == 0 ? 112 : 96), d(38)));
            nav[i].setOnFocusChangeListener((v, focused) -> {
                if (focused && !working && !presenting) select.accept(section);
            });
            nav[i].setOnClickListener(v -> {
                select.accept(section);
                primary(section).requestFocus();
            });
        }
        gap(root, 19);
        LinearLayout viewport = column();
        root.addView(viewport, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < page.length; i++) {
            page[i] = row();
            page[i].setGravity(Gravity.CENTER_VERTICAL);
            viewport.addView(page[i], new LinearLayout.LayoutParams(-1, -1));
        }

        LinearLayout first = left(page[0]);
        hero(first, "01 / THE ORIGINAL FILE", "A better Kick.\nStarts here.",
            "Download the supported original from APKMirror,\nthen bring it here. Everything else happens on TV.");
        download = button("Get original APKM  ↗", true);
        haveFile = button("I have the file  →", false);
        actions(first, download, haveFile);
        footnote(first, "Opens Kick Android TV " + support.version + " — the exact variant.\nAlready downloaded it? Go straight to Patch.");
        addPanel(0);

        LinearLayout second = left(page[1]);
        hero(second, "02 / MAKE IT YOURS", "Original in.\nYour mod out.",
            "Choose your APKM. The patcher checks the source,\napplies the profile and signs a new APK locally.");
        choose = button("Choose APKM", true);
        downloads = button("Open Downloads", false);
        actions(second, choose, downloads);
        footnote(second, "Your original file stays unchanged.\nYou approve installation after the APK is ready.");
        FrameLayout patchVisual = new FrameLayout(activity);
        page[1].addView(patchVisual, new LinearLayout.LayoutParams(0, -1, 1));
        panels[1] = new ProfilePanel(1);
        patchVisual.addView(panels[1], new FrameLayout.LayoutParams(-1, -1));
        work = column();
        work.setPadding(d(24), d(22), d(24), d(22));
        work.setGravity(Gravity.CENTER_VERTICAL);
        work.setBackground(glass(0xff15251a, 0xff426349, 22));
        FrameLayout.LayoutParams workSize = new FrameLayout.LayoutParams(-1, d(294), Gravity.CENTER);
        workSize.setMargins(d(8), d(4), d(8), d(4));
        patchVisual.addView(work, workSize);
        workTitle = text("BUILDING YOUR MOD", 11, GREEN, medium);
        work.addView(workTitle);
        gap(work, 13);
        String[] names = {"01   Verify original APKM", "02   Apply modifications", "03   Sign on this TV", "04   Verify the result"};
        for (int i = 0; i < phases.length; i++) {
            phases[i] = text(names[i], 16, GREY, body);
            phases[i].setPadding(0, d(10), 0, d(10));
            work.addView(phases[i]);
        }
        work.setVisibility(View.GONE);

        LinearLayout third = left(page[2]);
        hero(third, "03 / THE FINAL STEP", "Ready when\nyou are.",
            "Install through Android's system dialog,\nor save your patched APK for later.");
        install = button("Install on this TV", true);
        save = button("Save a copy", false);
        actions(third, install, save);
        footnote(third, "Keep this patcher and its data to preserve the signing key.\nA different-key mod cannot be updated with this APK.");
        addPanel(2);

        gap(root, 15);
        LinearLayout state = column();
        state.setPadding(d(15), d(10), d(15), d(10));
        state.setBackground(glass(0xf0121c16, 0xff2d3c31, 17));
        root.addView(state);
        LinearLayout stateRow = row();
        stateRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView dot = text("•", 20, GREEN, body);
        stateRow.addView(dot, new LinearLayout.LayoutParams(d(19), -2));
        LinearLayout stateText = column();
        stateRow.addView(stateText, new LinearLayout.LayoutParams(0, -2, 1));
        status = text("Ready when you are", 12, WHITE, medium);
        detail = text("Choose the original APKM. All processing stays on this TV.", 10, GREY, body);
        stateText.addView(status);
        stateText.addView(detail);
        percent = text("ON DEVICE", 10, MUTED, medium);
        percent.setPadding(d(14), 0, 0, 0);
        stateRow.addView(percent);
        cancel = button("Cancel", false);
        cancel.setVisibility(View.GONE);
        LinearLayout.LayoutParams cancelSize = new LinearLayout.LayoutParams(d(88), d(38));
        cancelSize.leftMargin = d(10);
        stateRow.addView(cancel, cancelSize);
        state.addView(stateRow);
        progress = new ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setProgressTintList(ColorStateList.valueOf(GREEN));
        state.addView(progress, new LinearLayout.LayoutParams(-1, d(3)));
        progress.setVisibility(View.GONE);
        for (int i = 0; i < nav.length; i++) {
            nav[i].setNextFocusDownId(primary(i).getId());
            primary(i).setNextFocusUpId(nav[i].getId());
        }
        haveFile.setNextFocusUpId(nav[0].getId());
        downloads.setNextFocusUpId(nav[1].getId());
        save.setNextFocusUpId(nav[2].getId());
        updates.setNextFocusRightId(nav[0].getId());
        updates.setNextFocusDownId(download.getId());
        nav[0].setNextFocusLeftId(updates.getId());
        present(0, null);
    }

    void present(int action, File result) {
        if (working || presenting) return;
        presenting = true;
        int target = action == 0 ? 0 : action <= 2 ? 1 : 2;
        for (int i = 0; i < page.length; i++) {
            page[i].setVisibility(i == target ? View.VISIBLE : View.GONE);
            nav[i].setSelected(i == target);
        }
        panels[2].result = result;
        panels[2].setContentDescription(result == null ? "No patched APK yet" : "Verified Kick MOD " + support.version + ", signed on this TV");
        panels[2].invalidate();
        presenting = false;
    }

    void working(boolean value) {
        presenting = true;
        working = value;
        updates.setEnabled(!value);
        for (Button b : nav) b.setEnabled(!value);
        haveFile.setEnabled(!value);
        progress.setIndeterminate(false);
        progress.setVisibility(value ? View.VISIBLE : View.GONE);
        work.setVisibility(value ? View.VISIBLE : View.GONE);
        workTitle.setText("BUILDING YOUR MOD");
        panels[1].setVisibility(value ? View.GONE : View.VISIBLE);
        for (TextView phase : phases) phase.setVisibility(View.VISIBLE);
        phases[0].setText("01   Verify original APKM");
        if (value) for (int i = 0; i < page.length; i++) {
            page[i].setVisibility(i == 1 ? View.VISIBLE : View.GONE);
            nav[i].setSelected(i == 1);
        }
        else percent.setText("ON DEVICE");
        presenting = false;
    }

    void phase(int value) {
        int[] thresholds = {0, 15, 83, 96};
        int active = 0;
        for (int i = 1; i < thresholds.length; i++) if (value >= thresholds[i]) active = i;
        for (int i = 0; i < phases.length; i++) {
            boolean reached = value >= thresholds[i];
            phases[i].setTextColor(i == active ? GREEN : reached ? WHITE : MUTED);
            phases[i].setAlpha(reached ? 1f : .75f);
        }
    }

    void saving() {
        working(true);
        workTitle.setText("SAVING YOUR APK");
        for (TextView phase : phases) phase.setVisibility(View.GONE);
        phases[0].setText("Saving your APK copy…");
        phases[0].setVisibility(View.VISIBLE);
        progress.setIndeterminate(true);
        percent.setText("SAVING");
    }

    void updating() {
        working(true);
        workTitle.setText("UPDATING YOUR PATCHER");
        for (TextView phase : phases) phase.setVisibility(View.GONE);
        phases[0].setText("Download and verify update…");
        phases[0].setTextColor(WHITE);
        phases[0].setVisibility(View.VISIBLE);
        percent.setText("0%");
    }

    private Typeface font(String name, int weight) {
        return new Typeface.Builder(activity.getAssets(), "fonts/" + name + ".ttf")
            .setFontVariationSettings("'wght' " + weight).build();
    }

    private void addPanel(int section) {
        panels[section] = new ProfilePanel(section);
        page[section].addView(panels[section], new LinearLayout.LayoutParams(0, -1, 1));
    }

    private Button primary(int section) { return section == 0 ? download : section == 1 ? choose : install; }

    private LinearLayout left(LinearLayout parent) {
        LinearLayout view = column();
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(0, 0, d(34), 0);
        parent.addView(view, new LinearLayout.LayoutParams(0, -1, 1.16f));
        return view;
    }

    private void hero(LinearLayout parent, String tag, String title, String description) {
        TextView eyebrow = text(tag, 10, MUTED, medium);
        eyebrow.setLetterSpacing(.12f);
        parent.addView(eyebrow);
        gap(parent, 13);
        TextView heading = text(title, 37, WHITE, display);
        SpannableString colored = new SpannableString(title);
        int line = title.indexOf('\n');
        colored.setSpan(new ForegroundColorSpan(0xffb7e4a7), line + 1, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        heading.setText(colored);
        heading.setLineSpacing(d(1), 1);
        parent.addView(heading);
        gap(parent, 14);
        parent.addView(text(description, 13, GREY, body));
        gap(parent, 18);
    }

    private void footnote(LinearLayout parent, String value) { parent.addView(text(value, 10, MUTED, body)); }

    private void actions(LinearLayout parent, Button first, Button second) {
        LinearLayout actions = row();
        LinearLayout.LayoutParams size = new LinearLayout.LayoutParams(0, d(46), 1.2f);
        size.rightMargin = d(10);
        actions.addView(first, size);
        actions.addView(second, new LinearLayout.LayoutParams(0, d(46), 1));
        parent.addView(actions);
        gap(parent, 11);
    }

    private int d(int value) { return (int) (activity.getResources().getDisplayMetrics().density * value + .5f); }
    private LinearLayout column() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private LinearLayout row() { LinearLayout v = new LinearLayout(activity); v.setOrientation(LinearLayout.HORIZONTAL); return v; }
    private void gap(LinearLayout parent, int amount) { parent.addView(new View(activity), new LinearLayout.LayoutParams(1, d(amount))); }

    private GradientDrawable glass(int fill, int stroke, int radius) {
        GradientDrawable shape = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{fill, fill});
        shape.setCornerRadius(d(radius));
        shape.setStroke(d(1), stroke);
        return shape;
    }

    private TextView text(String value, int size, int color, Typeface font) {
        TextView view = new TextView(activity);
        view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setTypeface(font);
        view.setIncludeFontPadding(false); view.setLineSpacing(d(3), 1); view.setPadding(0, d(2), 0, d(2));
        return view;
    }

    private StateListDrawable buttonBackground(boolean primary, boolean tab) {
        int radius = tab ? 20 : 14;
        StateListDrawable states = new StateListDrawable();
        states.addState(new int[]{-android.R.attr.state_enabled}, glass(0xff152019, 0xff27362d, radius));
        states.addState(new int[]{android.R.attr.state_pressed}, glass(0xffb4ff94, WHITE, radius));
        states.addState(new int[]{android.R.attr.state_focused}, focusGlass(radius));
        states.addState(new int[]{android.R.attr.state_selected}, glass(0xff283c2e, 0xff608568, radius));
        states.addState(new int[]{}, glass(primary ? GREEN : tab ? 0x00121b16 : 0xff152019,
            primary ? GREEN : tab ? 0x00121b16 : 0xff3d5044, radius));
        return states;
    }

    private Drawable focusGlass(int radius) {
        GradientDrawable halo = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0x6053fc18, 0x2253fc18, 0x3853fc18});
        halo.setCornerRadius(d(radius + 2));
        GradientDrawable lens = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xf04a6853, 0xf02c4936, 0xf01a3324});
        lens.setCornerRadius(d(radius));
        lens.setStroke(d(1), 0xff9acda3);
        LayerDrawable layers = new LayerDrawable(new Drawable[]{halo, lens});
        layers.setLayerInset(1, d(2), d(2), d(2), d(2));
        return layers;
    }

    private Button button(String label, boolean primary) {
        Button button = new Button(activity);
        button.setId(View.generateViewId()); button.setText(label); button.setTextSize(12); button.setTypeface(medium);
        button.setAllCaps(false); button.setPadding(d(10), 0, d(10), 0);
        button.setMinimumHeight(d(36)); button.setMinHeight(d(36)); button.setFocusableInTouchMode(true);
        button.setBackground(buttonBackground(primary, false));
        button.setTextColor(new ColorStateList(new int[][]{{-android.R.attr.state_enabled},
            {android.R.attr.state_pressed}, {android.R.attr.state_focused}, {}},
            new int[]{0xff7e9183, 0xff10200c, WHITE, primary ? 0xff10200c : WHITE}));
        button.setStateListAnimator(null);
        button.setOnFocusChangeListener((v, focus) -> v.animate().scaleX(focus ? 1.025f : 1)
            .scaleY(focus ? 1.025f : 1).translationZ(focus ? d(3) : 0).setDuration(140).start());
        return button;
    }

    private final class AmbientBackground extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        @Override public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            canvas.drawColor(0xff090e0b);
            paint.setShader(new RadialGradient(bounds.width() * .88f, bounds.height() * .18f,
                bounds.width() * .58f, new int[]{0xff1c3020, 0xff090e0b}, null, Shader.TileMode.CLAMP));
            canvas.drawRect(bounds, paint);
            paint.setShader(null);
        }
        @Override public void setAlpha(int alpha) {}
        @Override public void setColorFilter(ColorFilter filter) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    /** Niefokusowalna karta: trzy rozne tresci zamiast powtarzanej dekoracji. */
    private final class ProfilePanel extends View {
        final int mode;
        File result;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ProfilePanel(int mode) {
            super(activity); this.mode = mode;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            setContentDescription(mode == 0 ? "Supported original: Kick Android TV " + support.version + " build " + support.versionCode + "; ARM32 and ARM64, 320dpi, Android 8 or later"
                : "Profile: latency controls, auto raids, audio compressor, category filters, hidden chat and 1080p defaults");
        }

        private void rect(Canvas c, float left, float top, float right, float bottom, int fill, int stroke, float radius) {
            RectF bounds = new RectF(left, top, right, bottom);
            paint.setStyle(Paint.Style.FILL); paint.setColor(fill); c.drawRoundRect(bounds, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(.7f); paint.setColor(stroke);
            c.drawRoundRect(bounds, radius, radius, paint); paint.setStyle(Paint.Style.FILL);
        }

        private void label(Canvas c, String value, float x, float y, float size, int color, Typeface font) {
            paint.setTypeface(font); paint.setTextSize(size); paint.setColor(color); paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(value, x, y, paint);
        }

        private void divider(Canvas c, float y) {
            paint.setColor(0xff334b3b); paint.setStrokeWidth(.6f); c.drawLine(28, y, 332, y, paint);
        }

        private void mark(Canvas c, float x, float y, boolean complete) {
            paint.setColor(complete ? GREEN : MUTED); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.4f);
            if (complete) {
                Path check = new Path(); check.moveTo(x, y); check.lineTo(x + 4, y + 4); check.lineTo(x + 11, y - 4);
                c.drawPath(check, paint);
            } else c.drawCircle(x + 5, y, 4, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Math.min(getWidth() / 360f, getHeight() / 326f);
            canvas.save();
            canvas.translate((getWidth() - 360 * scale) / 2, (getHeight() - 326 * scale) / 2);
            canvas.scale(scale, scale);
            paint.setShader(new LinearGradient(0, 0, 360, 326, new int[]{0xff203628, 0xff14241a, 0xff101d15}, null, Shader.TileMode.CLAMP));
            canvas.drawRoundRect(new RectF(3, 3, 357, 323), 22, 22, paint); paint.setShader(null);
            rect(canvas, 3, 3, 357, 323, 0x00000000, 0xff4a6551, 22);
            paint.setColor(GREEN); canvas.drawRoundRect(new RectF(28, 29, 33, 42), 2, 2, paint);
            label(canvas, mode == 0 ? "SUPPORTED ORIGINAL" : mode == 1 ? "INCLUDED IN YOUR MOD" : "YOUR PATCHED APP", 43, 39, 10, 0xffc2d4c5, medium);
            rect(canvas, 286, 23, 332, 46, 0xff1d3425, 0xff4a6551, 7);
            label(canvas, mode == 0 ? "APKM" : mode == 1 ? support.version : "APK", 294, 38, 9, 0xffc3d6c6, medium);
            if (mode == 0) original(canvas);
            else if (mode == 1) features(canvas);
            else output(canvas);
            canvas.restore();
        }

        private void original(Canvas c) {
            label(c, "Kick for Android TV", 28, 82, 15, WHITE, medium);
            label(c, support.version, 25, 137, 44, WHITE, display);
            label(c, "BUILD " + support.versionCode, 30, 162, 10, MUTED, medium);
            divider(c, 182);
            String[] labels = {"ARM32 + ARM64", "320dpi", "Android 8.0+"};
            float[] x = {28, 161, 238};
            for (int i = 0; i < labels.length; i++) label(c, labels[i], x[i], 209, 10, 0xffcfdbd2, body);
            rect(c, 28, 230, 332, 281, 0xff0e1b13, 0xff2d4735, 10);
            label(c, "4 original modules", 41, 252, 12, WHITE, medium);
            label(c, "Base APK + three configuration splits", 41, 269, 10, MUTED, body);
            label(c, "Only this version and variant are supported.", 28, 304, 10, MUTED, body);
        }

        private void features(Canvas c) {
            String[] title = {"Live latency", "Automatic raids", "Audio compressor", "Category filters", "Chat hidden by default", "1080p by default"};
            String[] subtitle = {"3 / 5 / 8 s target + live offset", "Follow the streamer's next channel", "Control louder and quieter moments", "Separate Sleeping and ASMR controls", "More room for the stream", "When available on the stream"};
            for (int i = 0; i < title.length; i++) {
                float y = 74 + i * 36;
                mark(c, 29, y - 2, true);
                label(c, title[i], 51, y, 12, WHITE, medium);
                label(c, subtitle[i], 51, y + 14, 9.5f, GREY, body);
            }
            divider(c, 291);
            label(c, "One profile. Controls inside the patched app.", 28, 309, 10, MUTED, body);
        }

        private void output(Canvas c) {
            boolean ready = result != null;
            label(c, ready ? "Kick Android TV MOD" : "No patched APK yet", 28, 85, 18, WHITE, display);
            label(c, ready ? String.format(Locale.ROOT, "%s   /   %.1f MB", support.version, result.length() / 1048576.0) : "Choose an original APKM in the Patch step.", 28, 111, 12, GREY, body);
            divider(c, 133);
            String[] labels = ready ? new String[]{"Output verified", "Signed on this TV", "Installation needs your approval"}
                : new String[]{"Verify the original", "Apply and sign locally", "Return here to install or save"};
            for (int i = 0; i < labels.length; i++) {
                mark(c, 29, 158 + i * 31, ready);
                label(c, labels[i], 51, 162 + i * 31, 12, ready ? WHITE : GREY, body);
            }
            rect(c, 28, 247, 332, 299, 0xff0e1b13, 0xff2d4735, 10);
            label(c, "Your key stays on this device", 41, 269, 11, 0xffc6ddc8, medium);
            label(c, "Keep the patcher installed and its data intact.", 41, 286, 9.5f, GREY, body);
        }
    }
}
