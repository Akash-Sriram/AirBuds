package org.airbridge.tws;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

public class EqGraphView extends View {

    public interface OnEqChangeListener {
        void onGainChanged(int bandIdx, int gainDb);
        void onGainChangeFinished(int[] allGains);
    }

    private final int[] mGains = new int[]{0, 0, 0, 0, 0, 0}; // -6 dB to +6 dB
    private static final String[] FREQ_LABELS = {"62Hz", "250Hz", "1kHz", "4kHz", "8kHz", "16kHz"};

    private Paint mTrackPaint;
    private Paint mDashPaint;
    private Paint mCurvePaint;
    private Paint mFillPaint;
    private Paint mThumbInnerPaint;
    private Paint mThumbStrokePaint;
    private Paint mTextPaint;
    private Paint mSubTextPaint;

    private int mActiveBand = -1;
    private OnEqChangeListener mListener;

    public EqGraphView(Context context) {
        super(context);
        init();
    }

    public EqGraphView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public EqGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTrackPaint.setColor(Color.parseColor("#D0D5DD"));
        mTrackPaint.setStrokeWidth(dpToPx(1f));
        mTrackPaint.setStyle(Paint.Style.STROKE);

        mDashPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mDashPaint.setColor(Color.parseColor("#B0B7C3"));
        mDashPaint.setStrokeWidth(dpToPx(1f));
        mDashPaint.setStyle(Paint.Style.STROKE);
        mDashPaint.setPathEffect(new DashPathEffect(new float[]{dpToPx(3f), dpToPx(3f)}, 0));

        mCurvePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCurvePaint.setColor(Color.parseColor("#1677FF"));
        mCurvePaint.setStrokeWidth(dpToPx(3f));
        mCurvePaint.setStyle(Paint.Style.STROKE);
        mCurvePaint.setStrokeCap(Paint.Cap.ROUND);
        mCurvePaint.setStrokeJoin(Paint.Join.ROUND);

        mFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mFillPaint.setStyle(Paint.Style.FILL);

        mThumbInnerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mThumbInnerPaint.setColor(Color.WHITE);
        mThumbInnerPaint.setStyle(Paint.Style.FILL);

        mThumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mThumbStrokePaint.setColor(Color.parseColor("#1677FF"));
        mThumbStrokePaint.setStrokeWidth(dpToPx(2.5f));
        mThumbStrokePaint.setStyle(Paint.Style.STROKE);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(Color.parseColor("#8E8E93"));
        mTextPaint.setTextSize(spToPx(11f));
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mSubTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mSubTextPaint.setColor(Color.parseColor("#1D1D1F"));
        mSubTextPaint.setTextSize(spToPx(10.5f));
        mSubTextPaint.setTextAlign(Paint.Align.CENTER);
        mSubTextPaint.setFakeBoldText(true);
    }

    public void setOnEqChangeListener(OnEqChangeListener listener) {
        mListener = listener;
    }

    public void setGains(int[] gains) {
        if (gains != null && gains.length == 6) {
            for (int i = 0; i < 6; i++) {
                mGains[i] = Math.max(-6, Math.min(6, gains[i]));
            }
            invalidate();
        }
    }

    public int[] getGains() {
        return mGains.clone();
    }

    public void resetFlat() {
        for (int i = 0; i < 6; i++) {
            mGains[i] = 0;
        }
        invalidate();
        if (mListener != null) {
            mListener.onGainChangeFinished(mGains.clone());
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padLeft = dpToPx(44f);
        float padRight = dpToPx(24f);
        float padTop = dpToPx(28f);
        float padBottom = dpToPx(38f);

        float graphW = w - padLeft - padRight;
        float graphH = h - padTop - padBottom;
        float yZero = padTop + (graphH / 2f);

        // 1. Draw Left Y-Axis labels (+6db, 0db, -6db)
        Paint.Align prevAlign = mTextPaint.getTextAlign();
        mTextPaint.setTextAlign(Paint.Align.RIGHT);
        float labelX = padLeft - dpToPx(10f);
        canvas.drawText("+6dB", labelX, padTop + dpToPx(4f), mTextPaint);
        canvas.drawText("0dB", labelX, yZero + dpToPx(4f), mTextPaint);
        canvas.drawText("-6dB", labelX, padTop + graphH + dpToPx(4f), mTextPaint);
        mTextPaint.setTextAlign(prevAlign);

        // 2. Draw 0 dB dashed baseline
        canvas.drawLine(padLeft, yZero, w - padRight, yZero, mDashPaint);

        // Calculate node X & Y coordinates
        float[] xCoords = new float[6];
        float[] yCoords = new float[6];
        float stepX = graphW / 5f;

        for (int i = 0; i < 6; i++) {
            xCoords[i] = padLeft + (i * stepX);
            float ratio = (6f - mGains[i]) / 12f; // 0.0 at +6dB, 1.0 at -6dB
            yCoords[i] = padTop + (ratio * graphH);

            // Draw vertical track line
            canvas.drawLine(xCoords[i], padTop, xCoords[i], padTop + graphH, mTrackPaint);

            // Draw Frequency label
            canvas.drawText(FREQ_LABELS[i], xCoords[i], h - dpToPx(20f), mTextPaint);
        }

        // Subtitles for Low freq / High freq
        canvas.drawText("Low", xCoords[0], h - dpToPx(6f), mTextPaint);
        canvas.drawText("High", xCoords[5], h - dpToPx(6f), mTextPaint);

        // 3. Build Smooth Cubic Bezier Paths for Curve and Fill
        Path curvePath = new Path();
        Path fillPath = new Path();

        curvePath.moveTo(xCoords[0], yCoords[0]);
        fillPath.moveTo(xCoords[0], padTop + graphH);
        fillPath.lineTo(xCoords[0], yCoords[0]);

        for (int i = 0; i < 5; i++) {
            float x1 = xCoords[i];
            float y1 = yCoords[i];
            float x2 = xCoords[i + 1];
            float y2 = yCoords[i + 1];

            float dx = (x2 - x1) * 0.45f;
            curvePath.cubicTo(x1 + dx, y1, x2 - dx, y2, x2, y2);
            fillPath.cubicTo(x1 + dx, y1, x2 - dx, y2, x2, y2);
        }

        fillPath.lineTo(xCoords[5], padTop + graphH);
        fillPath.close();

        // 4. Draw Gradient Fill below curve
        LinearGradient fillGrad = new LinearGradient(
                0, padTop,
                0, padTop + graphH,
                new int[]{Color.argb(80, 22, 119, 255), Color.argb(10, 22, 119, 255), Color.TRANSPARENT},
                new float[]{0f, 0.7f, 1.0f},
                Shader.TileMode.CLAMP
        );
        mFillPaint.setShader(fillGrad);
        canvas.drawPath(fillPath, mFillPaint);

        // 5. Draw Blue Curve Line
        canvas.drawPath(curvePath, mCurvePaint);

        // 6. Draw 6 Circular Thumb Handles & dB value labels
        float radius = dpToPx(7f);
        float activeRadius = dpToPx(10f);

        for (int i = 0; i < 6; i++) {
            float r = (i == mActiveBand) ? activeRadius : radius;
            canvas.drawCircle(xCoords[i], yCoords[i], r, mThumbInnerPaint);
            canvas.drawCircle(xCoords[i], yCoords[i], r, mThumbStrokePaint);

            // Draw dB value text
            String db = (mGains[i] > 0 ? "+" : "") + mGains[i] + "dB";
            float textY = (mGains[i] >= 4) ? (yCoords[i] + dpToPx(18f)) : (yCoords[i] - dpToPx(11f));
            canvas.drawText(db, xCoords[i], textY, mSubTextPaint);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return super.onTouchEvent(event);

        float padLeft = dpToPx(44f);
        float padRight = dpToPx(24f);
        float padTop = dpToPx(28f);
        float padBottom = dpToPx(38f);
        float graphW = w - padLeft - padRight;
        float graphH = h - padTop - padBottom;
        float stepX = graphW / 5f;

        switch (action) {
            case MotionEvent.ACTION_DOWN: {
                int nearest = -1;
                float minDist = Float.MAX_VALUE;
                for (int i = 0; i < 6; i++) {
                    float xi = padLeft + (i * stepX);
                    float dist = Math.abs(x - xi);
                    if (dist < minDist && dist < stepX * 0.75f) {
                        minDist = dist;
                        nearest = i;
                    }
                }
                if (nearest != -1) {
                    mActiveBand = nearest;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    updateGainFromY(y, padTop, graphH);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mActiveBand != -1) {
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    updateGainFromY(y, padTop, graphH);
                    return true;
                }
                break;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (mActiveBand != -1) {
                    updateGainFromY(y, padTop, graphH);
                    mActiveBand = -1;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                    if (mListener != null) {
                        mListener.onGainChangeFinished(mGains.clone());
                    }
                    invalidate();
                    return true;
                }
                break;
            }
        }
        return super.onTouchEvent(event);
    }

    private void updateGainFromY(float y, float padTop, float graphH) {
        if (mActiveBand < 0 || mActiveBand >= 6) return;
        float clampedY = Math.max(padTop, Math.min(padTop + graphH, y));
        float ratio = (clampedY - padTop) / graphH; // 0.0 at top (+6dB), 1.0 at bottom (-6dB)
        int gain = Math.round(6f - (ratio * 12f));
        gain = Math.max(-6, Math.min(6, gain));
        if (mGains[mActiveBand] != gain) {
            mGains[mActiveBand] = gain;
            if (mListener != null) {
                mListener.onGainChanged(mActiveBand, gain);
            }
        }
        invalidate();
    }

    private float dpToPx(float dp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private float spToPx(float sp) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, getResources().getDisplayMetrics());
    }
}
