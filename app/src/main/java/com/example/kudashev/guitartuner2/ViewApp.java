package com.example.kudashev.guitartuner2;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.*;
import android.graphics.Paint.Style;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static java.lang.Math.PI;
import static java.lang.Math.abs;
import static java.lang.Math.cos;
import static java.lang.Math.sin;

class RefreshHandler extends Handler {
    ViewApp m_viewApp;

    public RefreshHandler(ViewApp v) {
        m_viewApp = v;
    }

    public void handleMessage(Message msg) {
        m_viewApp.update();
        m_viewApp.invalidate();
    }

    public void sleep(long delayMillis) {
        this.removeMessages(0);
        sendMessageDelayed(obtainMessage(0), delayMillis);
    }
};

public class ViewApp extends View implements AudioRecord.OnRecordPositionUpdateListener {
    public class FFT {

        int n, m;

        // Lookup tables. Only need to recompute when size of FFT changes.
        double[] cos;
        double[] sin;

        public FFT(int n) {
            this.n = n;
            this.m = (int) (Math.log(n) / Math.log(2));

            // Make sure n is a power of 2
            if (n != (1 << m))
                throw new RuntimeException("FFT length must be power of 2");

            // precompute tables
            cos = new double[n / 2];
            sin = new double[n / 2];

            for (int i = 0; i < n / 2; i++) {
                cos[i] = Math.cos(-2 * Math.PI * i / n);
                sin[i] = Math.sin(-2 * Math.PI * i / n);
            }

        }

        public void fft(double[] x, double[] y) {
            int i, j, k, n1, n2, a;
            double c, s, t1, t2;

            // Bit-reverse
            j = 0;
            n2 = n / 2;
            for (i = 1; i < n - 1; i++) {
                n1 = n2;
                while (j >= n1) {
                    j = j - n1;
                    n1 = n1 / 2;
                }
                j = j + n1;

                if (i < j) {
                    t1 = x[i];
                    x[i] = x[j];
                    x[j] = t1;
                    t1 = y[i];
                    y[i] = y[j];
                    y[j] = t1;
                }
            }

            // FFT
            n1 = 0;
            n2 = 1;

            for (i = 0; i < m; i++) {
                n1 = n2;
                n2 = n2 + n2;
                a = 0;

                for (j = 0; j < n1; j++) {
                    c = cos[a];
                    s = sin[a];
                    a += 1 << (m - i - 1);

                    for (k = j; k < n; k = k + n2) {
                        t1 = c * x[k + n1] - s * y[k + n1];
                        t2 = s * x[k + n1] + c * y[k + n1];
                        x[k + n1] = x[k] - t1;
                        y[k + n1] = y[k] - t2;
                        x[k] = x[k] + t1;
                        y[k] = y[k] + t2;
                    }
                }
            }
        }
    }


    MainActivity m_app;
    Rect[] c = null;
    private RefreshHandler m_refresh;
    Random rand = new Random();
    private static final int UPDATE_TIME_MS = 60;
    //background
    Bitmap m_background;
    Rect m_rectBackgroud;
    Rect m_rectDst;
    Paint m_paintBackgroud;

    //buttons
    Paint m_paintButton;
    Paint m_paintButtonManual;
    Paint m_paintSpectr;
    Paint m_paintMinSpectr;
    Paint m_paintCurrentFreq;
    Paint m_paintMaxFreqBad;
    Paint m_paintMaxFreqGood;
    Paint m_paintText;
    Paint m_paintD;
    Paint m_paintA;
    Paint m_paintE_fat;
    Paint m_paintG;
    Paint m_paintB;
    Paint m_paintE_light;
    Paint m_paintManual;
    Rect[] m_notesButtons;
    int m_lastTouchDownButtonID = -1;
    static final double m_buttonSize = 0.1;
    static final double m_buttonMarginLeft = 0.05;
    static final double m_buttonMarginTop = 0.27;
    static final double m_buttonDistance = 0.07;

    static final Map<Integer, String> notes = new HashMap<Integer, String>();
    static final Map<Integer, MediaPlayer> mediaPlayers = new HashMap<Integer, MediaPlayer>();
    static final Map<Integer, Double> notesFreq = new HashMap<Integer, Double>();

    //sound
    MediaPlayer m_player_D;
    MediaPlayer m_player_A;
    MediaPlayer m_player_E_fat;
    MediaPlayer m_player_G;
    MediaPlayer m_player_B;
    MediaPlayer m_player_E_light;
    AudioRecord m_audioRecord;
    final int m_samplerate = 16000;
    final int m_notificationPeriod = 4000;
    final int m_winSize = 16384;
    final int m_readSamplesNum = 2048;
    short[] m_audioData;
    short[] m_audioDataReaded;
    double[] m_spectr;
    double[] m_spectrTmp;
    double[] m_averageMinSpectr;
    double[] m_averageSpectr;
    final double m_maxFreq = (double) m_samplerate / 2.;
    int m_top = 0;
    FFT m_fft;
    FFT m_spectrFFT;
    double[] x;
    double[] y;
    boolean m_isActive;

    double m_currFreq = 100;
    int m_currentButtonID = -1;

    public ViewApp(MainActivity app) {
        super(app);
        m_refresh = new RefreshHandler(this);
        m_isActive = false;
        notes.put(0, "D");
        notes.put(1, "A");
        notes.put(2, "E");
        notes.put(3, "G");
        notes.put(4, "B");
        notes.put(5, "E");

        notesFreq.put(0, 146.83);
        notesFreq.put(1, 110.0);
        notesFreq.put(2, 82.41);
        notesFreq.put(3, 196.0);
        notesFreq.put(4, 246.94);
        notesFreq.put(5, 329.63);


        m_app = app;
        m_background = BitmapFactory.decodeResource(app.getResources(), R.drawable.background);
        m_rectBackgroud = new Rect(0, 0, m_background.getWidth(), m_background.getHeight());
        m_rectDst = new Rect();

        m_notesButtons = new Rect[6];
        for (int i = 0; i < m_notesButtons.length; ++i) {
            m_notesButtons[i] = new Rect();
        }

        m_paintText = new Paint();
        m_paintText.setColor(Color.WHITE);

        m_paintD = new Paint();
        m_paintD.setColor(Color.WHITE);
        m_paintA = new Paint();
        m_paintA.setColor(Color.WHITE);
        m_paintE_fat = new Paint();
        m_paintE_fat.setColor(Color.WHITE);
        m_paintG = new Paint();
        m_paintG.setColor(Color.WHITE);
        m_paintB = new Paint();
        m_paintB.setColor(Color.WHITE);
        m_paintE_light = new Paint();
        m_paintE_light.setColor(Color.WHITE);

        m_paintBackgroud = new Paint();
        m_paintBackgroud.setColor(0xFFFFFFFF);
        m_paintBackgroud.setStyle(Style.FILL);
        m_paintBackgroud.setAntiAlias(true);
        m_paintBackgroud.setAlpha(255);


        m_paintButton = new Paint();
        m_paintButton.setColor(Color.BLACK);
        //setOnTouchListener(app);

        m_paintButtonManual = new Paint();
        m_paintButtonManual.setColor(Color.RED);

        m_paintSpectr = new Paint();
        m_paintSpectr.setColor(Color.BLUE);

        m_paintMinSpectr = new Paint();
        m_paintMinSpectr.setColor(Color.BLACK);

        m_paintCurrentFreq = new Paint();
        m_paintCurrentFreq.setColor(Color.BLACK);
        m_paintCurrentFreq.setStrokeWidth(3);

        m_paintMaxFreqBad = new Paint();
        m_paintMaxFreqBad.setColor(Color.RED);
        m_paintMaxFreqBad.setStrokeWidth(3);

        m_paintMaxFreqGood = new Paint();
        m_paintMaxFreqGood.setColor(Color.GREEN);
        m_paintMaxFreqGood.setStrokeWidth(6);

        m_paintManual = new Paint();
        m_paintManual.setColor(Color.BLACK);

        m_lastTouchDownButtonID = -1;

        m_player_D = MediaPlayer.create(app, R.raw.d);
        m_player_D.setVolume(100, 100);
        m_player_A = MediaPlayer.create(app, R.raw.a);
        m_player_A.setVolume(100, 100);
        m_player_E_fat = MediaPlayer.create(app, R.raw.e_fat);
        m_player_E_fat.setVolume(100, 100);
        m_player_G = MediaPlayer.create(app, R.raw.g);
        m_player_G.setVolume(100, 100);
        m_player_B = MediaPlayer.create(app, R.raw.b);
        m_player_B.setVolume(100, 100);
        m_player_E_light = MediaPlayer.create(app, R.raw.e_light);
        m_player_E_light.setVolume(100, 100);

        mediaPlayers.put(0, m_player_D);
        mediaPlayers.put(1, m_player_A);
        mediaPlayers.put(2, m_player_E_fat);
        mediaPlayers.put(3, m_player_G);
        mediaPlayers.put(4, m_player_B);
        mediaPlayers.put(5, m_player_E_light);

        m_spectr = new double[m_winSize / 2];
        m_spectrTmp = new double[m_winSize / 2];

        m_averageMinSpectr = new double[m_winSize / 2];

        m_averageSpectr = new double[m_winSize / 2];
        x = new double[m_winSize];
        y = new double[m_winSize];

        m_fft = new FFT(m_winSize);
        m_spectrFFT = new FFT(m_winSize / 2);

        m_audioData = new short[m_winSize];
        for (int i = 0; i < m_winSize; ++i)
            m_audioData[i] = 0;
        m_audioDataReaded = new short[m_readSamplesNum];
        for (int i = 0; i < m_readSamplesNum; ++i)
            m_audioDataReaded[i] = 0;
        m_audioRecord = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
                m_samplerate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, m_samplerate * 10);
        //m_audioRecord.setRecordPositionUpdateListener(this);
        //m_audioRecord.setPositionNotificationPeriod(m_notificationPeriod);
//        ////////////////////////////////////////////
        reset();
    }

    public void reset() {
        for (int i = 0; i < m_winSize / 2; ++i)
            m_spectr[i] = 0;

        for (int i = 0; i < m_winSize / 2; ++i)
            m_averageMinSpectr[i] = 0;

        for (int i = 0; i < m_winSize / 2; ++i)
            m_averageSpectr[i] = 0;
    }

    public void update() {
        if (m_isActive) {
            int readedSamplesNum = m_audioRecord.read(m_audioDataReaded, 0, m_readSamplesNum);
            if (readedSamplesNum > 0) {
                System.arraycopy(m_audioData, m_readSamplesNum, m_audioData, 0, (m_winSize - m_readSamplesNum));
                System.arraycopy(m_audioDataReaded, 0,
                        m_audioData, (m_winSize - m_readSamplesNum), m_readSamplesNum);
                calcSpectr();
            }
            //Log.d("TUNER", "readedFramesNum = " + String.valueOf(readedSamplesNum));
        }
        m_refresh.sleep(UPDATE_TIME_MS);
    }

    void drawManualText(Canvas canvas) {
        float textSize = this.getWidth() / 30;
        m_paintManual.setTextSize(textSize);
        String mess1 = getResources().getString(R.string.manual1);
        String mess2 = getResources().getString(R.string.manual2);
        String mess3 = getResources().getString(R.string.manual3);
        canvas.drawText(mess1, this.getWidth() / 2 - mess1.length() / 2 * textSize / 2, m_top + textSize, m_paintManual);
        canvas.drawText(mess2, this.getWidth() / 2 - mess2.length() / 2 * textSize / 2, m_top + 2 * textSize, m_paintManual);
        canvas.drawText(mess3, this.getWidth() / 2 - mess3.length() / 2 * textSize / 2, m_top + 3 * textSize, m_paintManual);
    }

    void drawNotesButtons(Canvas canvas) {
        for (Rect noteButton : m_notesButtons) {
            canvas.drawRect(noteButton.left, noteButton.top, noteButton.right,
                    noteButton.bottom, m_paintButton);
            //canvas.drawText("A", noteButton.left, noteButton.bottom, m_paintText);
        }
        int width = this.getWidth();
        int size = (int) (width * m_buttonSize);
        canvas.drawText(notes.get(0), m_notesButtons[0].left + size / 5, m_notesButtons[0].bottom - size / 7, m_paintD);
        canvas.drawText(notes.get(1), m_notesButtons[1].left + size / 5, m_notesButtons[1].bottom - size / 7, m_paintA);
        canvas.drawText(notes.get(2), m_notesButtons[2].left + size / 5, m_notesButtons[2].bottom - size / 7, m_paintE_fat);
        canvas.drawText(notes.get(3), m_notesButtons[3].left + size / 5, m_notesButtons[3].bottom - size / 7, m_paintG);
        canvas.drawText(notes.get(4), m_notesButtons[4].left + size / 5, m_notesButtons[4].bottom - size / 7, m_paintB);
        canvas.drawText(notes.get(5), m_notesButtons[5].left + size / 5, m_notesButtons[5].bottom - size / 7, m_paintE_light);

        canvas.drawRect(width - size, 0, width, size, m_paintButtonManual);
        canvas.drawText("?", width - size / 2 - m_paintText.getTextSize() / 5, size - size / 7, m_paintText);
//        for (int i = 0; i < 6; i++)
//            canvas.drawText(notes.get(i), m_notesButtons[i].left + size / 5, m_notesButtons[i].bottom - size / 7, m_paintText);
    }


    protected void calcButtonsSize() {
        int width = this.getWidth();
        int height = this.getHeight();
        int marginLeft = (int) (width * m_buttonMarginLeft);
        int marginTop = (int) (width * m_buttonMarginTop);
        int distance = (int) (height * m_buttonDistance);
        int size = (int) (width * m_buttonSize);
        float scale = (float) (this.getWidth()) / (float) (m_rectBackgroud.width());
        m_top = height - (int) (scale * m_rectBackgroud.height());
        m_paintText.setTextSize(size);
        m_paintD.setTextSize(size);
        m_paintA.setTextSize(size);
        m_paintE_fat.setTextSize(size);
        m_paintG.setTextSize(size);
        m_paintB.setTextSize(size);
        m_paintE_light.setTextSize(size);
        for (int i = 0; i < 3; ++i) {
            m_notesButtons[i].set(marginLeft, m_top + marginTop + i * (size + distance),
                    marginLeft + size, m_top + marginTop + i * distance + (i + 1) * size);
        }
        for (int i = 3; i < 6; ++i) {
            m_notesButtons[i].set(width - marginLeft - size, m_top + marginTop + (i - 3) * (size + distance),
                    width - marginLeft, m_top + marginTop + (i - 3) * distance + (i - 2) * size);
        }
    }

    protected void calcSpectr() {
        double power = 0;

        for (int i = 0; i < m_winSize; ++i)
            y[i] = 0;
        for (int i = 0; i < m_winSize; ++i)
            x[i] = (double) m_audioData[i] * (0.53836 - 0.461648 * cos((2 * PI * i) / (m_winSize - 1)));

        //(x, y, m_winSize);
        m_fft.fft(x, y);

        for (int i = 0; i < m_winSize / 2; ++i) {
            double power_i = x[i] * x[i] + y[i] * y[i];
            power += power_i;
            m_spectr[i] = Math.log(power_i);
        }

        // calc average log-power spectr

        // 1. Усреднение по соседним частотам
        double freqStep = m_maxFreq / (m_winSize / 2);
        double averageFreqWin = 5.;
        int averageBinsNum = 5; //(int)(averageFreqWin/freqStep + 0.5);
        double binCoeffStep = 1. / averageBinsNum;
        for (int i = 1; i < m_spectr.length; ++i) {
            double averSpectrSum = 0;
            double coeffSum = 0;
            for (int j = i - averageBinsNum; j <= i + averageBinsNum; ++j) {
                if ((j < 1) || (j >= m_spectr.length))
                    continue;
                double binCoeff = 1. - Math.abs(j - i) * binCoeffStep;
                averSpectrSum += binCoeff * m_spectr[j];
                coeffSum += binCoeff;
            }
            if (coeffSum > 0) {
                m_averageSpectr[i] = averSpectrSum / coeffSum;
            }
        }
        System.arraycopy(m_averageSpectr, 0, m_spectrTmp, 0, m_spectr.length);

        // 2. Усреднение по гармоникам
        int averageHarmonicsNum = 4;
        for (int i = 1; i < m_spectr.length; ++i) {
            double averSpectrSum = 0;
            double count = 0;
            for (int j = 1; j <= averageHarmonicsNum; ++j) {
                if (i * j >= m_spectr.length)
                    continue;
                averSpectrSum += m_spectrTmp[i * j];
                count += 1.;
            }
            m_averageSpectr[i] = (averSpectrSum / count);
        }

        // calc minimum average spectr
        double minSearchFreqWin = 25.;
        double adaptiveCoeff = 1. * (double) m_readSamplesNum / m_samplerate;
        int averageMinSearchBinsNum = 25; //(int)(minSearchFreqWin/freqStep + 0.5);
//        for (int i = 1; i < m_spectr.length; ++i) {
//            double averSpectrSum = 0;
//            double count = 0;
//            for (int j = i - averageMinSearchBinsNum; j <= i + averageMinSearchBinsNum; ++j) {
//                if ((j < 1) || (j >= m_spectr.length))
//                    continue;
//                averSpectrSum += m_averageSpectr[j];
//                count += 1.;
//            }
//            m_averageMinSpectr[i] = (averSpectrSum / count);
//        }

        // 2. поиск локальных минимумов и линейная интерполяция
        System.arraycopy(m_averageMinSpectr, 0, m_spectrTmp, 0, m_spectr.length);
        int lastLocalMinIndx = 1;
        double lastLocalMinSpectr = m_averageSpectr[lastLocalMinIndx];
        //ArrayList<Integer> localMinsList = new ArrayList<Integer>();
        for (int i = 1; i < m_spectr.length; i += averageMinSearchBinsNum) {
            int end_i = Math.min(i + averageMinSearchBinsNum, m_spectr.length);
            double localMinSpectr = m_averageSpectr[i];
            int localMinIndx = i;
            for (int j = i; j < end_i; ++j) {
                if (m_averageSpectr[j] < localMinSpectr) {
                    localMinSpectr = m_averageSpectr[j];
                    localMinIndx = j;
                }
            }
            for (int j = lastLocalMinIndx; j < localMinIndx; ++j) {
                double coeff_j = (double) (j - lastLocalMinIndx) / (localMinIndx - lastLocalMinIndx);
                double currAverageMinSpectr_j = coeff_j * localMinSpectr + (1. - coeff_j) * lastLocalMinSpectr;
                m_averageMinSpectr[j] = adaptiveCoeff * currAverageMinSpectr_j + (1. - adaptiveCoeff) * m_averageMinSpectr[j];
            }
            lastLocalMinIndx = localMinIndx;
            lastLocalMinSpectr = localMinSpectr;
        }
        for (int i = lastLocalMinIndx; i < m_spectr.length; ++i) {
            m_averageMinSpectr[i] = adaptiveCoeff * lastLocalMinSpectr + (1. - adaptiveCoeff) * m_averageMinSpectr[i];
        }
    }

    protected void drawSpectr(Canvas canvas) {
        int width = this.getWidth();

        double minA = 0;
        double maxA = 40;
        double minVolume = 5;
        double accuracy = 2.; // точность подстройки
        double freqStep = m_maxFreq / (m_winSize / 2);

        double currentFreq = m_currFreq;
        double freqDelta = Math.min(m_currFreq - 5., 100);
        double startFreq = m_currFreq - freqDelta;
        double endFreq = m_currFreq + freqDelta;
        int startSpectr_i = (int) (startFreq / freqStep + 0.5);
        int endSpectr_i = (int) (endFreq / freqStep + 0.5);

        // DRAW current freq line
        float startX = (float) width * (float) (currentFreq - startFreq) / (float) (endFreq - startFreq);
        float stopX = startX;
        float stopY = (float) 0;
        float startY = (float) m_top;
        canvas.drawLine(startX, startY, stopX, stopY, m_paintCurrentFreq);

//        // DRAW volume line
//        startX = 0;
//        stopX = (float) width;
//        startY = stopY = (float)(m_top - m_top * (minVolume - minA) / (maxA - minA));
//        canvas.drawLine(startX, startY, stopX, stopY, m_paintCurrentFreq);

        double maxFreq = 0;
        double maxFreqVolume = -100500;
        int maxFreqIndx = 0;
        for (int i = startSpectr_i; i <= endSpectr_i; ++i) {
            float freq_i = (float) freqStep * i;
            float freq_i2 = (float) freqStep * i + (float) (0.5 * freqStep);
            double volume_i = m_averageSpectr[i] - m_averageMinSpectr[i];
            if (volume_i > maxFreqVolume) {
                maxFreqVolume = volume_i;
                maxFreq = freq_i;
                maxFreqIndx = i;
            }
            startX = (float) width * (float) (freq_i - startFreq) / (float) (endFreq - startFreq);
            float startX2 = (float) width * (float) (freq_i2 - startFreq) / (float) (endFreq - startFreq);
            stopX = startX;
            stopY = Math.max((float) (m_top - m_top * (m_averageSpectr[i] - minA) / (maxA - minA)), 0);
            startY = (float) m_top;
            canvas.drawLine(startX, startY, stopX, stopY, m_paintSpectr);

//            canvas.drawLine(
//                    startX2, startY, startX2,
//                    (float) (m_top - m_top * (m_averageMinSpectr[i] - minA) / (maxA - minA)),
//                    m_paintMinSpectr);
        }

        // DRAW maximum freq line
        if (maxFreqVolume > minVolume) {
            startX = (float) width * (float) (maxFreq - startFreq) / (float) (endFreq - startFreq);
            stopX = startX;
            stopY = (float) 0;
            startY = (float) m_top;
            if (Math.abs(maxFreq - m_currFreq) < accuracy)
                canvas.drawLine(startX, startY, stopX, stopY, m_paintMaxFreqGood);
            else
                canvas.drawLine(startX, startY, stopX, stopY, m_paintMaxFreqBad);
        }
    }

    protected void onDraw(Canvas canvas) {

        canvas.drawColor(Color.WHITE);
        m_paintBackgroud.setAlpha(255);
        float scale = (float) (this.getWidth()) / (float) (m_rectBackgroud.width());
        int topDst = this.getHeight() - (int) (scale * m_rectBackgroud.height());
        m_rectDst.set(0, topDst, this.getWidth(), topDst + (int) (scale * m_rectBackgroud.height()));
        canvas.drawBitmap(m_background, m_rectBackgroud, m_rectDst, m_paintBackgroud);

        calcButtonsSize();
        drawNotesButtons(canvas);
        drawManualText(canvas);
        drawSpectr(canvas);
    }

    int getTouchButtonID(float x, float y) {
        int size = (int) (this.getWidth() * m_buttonSize);
        for (int i = 0; i < m_notesButtons.length; ++i) {
            if ((m_notesButtons[i].top < y) && (m_notesButtons[i].bottom > y) &&
                    (m_notesButtons[i].left < x) && (m_notesButtons[i].right > x)) {
                return i;
            }
        }
        if ((y >= 0) && (y <= size) && (x<this.getWidth()) && (x>this.getWidth() - size))
            return 6;

        return -1;
    }

    public void onPause() {
        m_isActive = false;
        m_audioRecord.stop();
    }

    protected void onButtonClick(int buttonID) {
        if (buttonID < 0)
            return;
        if (m_currentButtonID != buttonID)
            reset();
        m_currentButtonID = buttonID;
        m_isActive = true;
        if (m_audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_STOPPED)
            m_audioRecord.startRecording();
        m_refresh.sleep(UPDATE_TIME_MS);
        Log.d("TUNER", "BUTTON CLICKED: " + String.valueOf(buttonID));
        // mediaPlayers.get(buttonID).start();
        switch (buttonID) {
            case 0:
                m_paintD.setColor(Color.GREEN);
                m_paintA.setColor(Color.WHITE);
                m_paintE_fat.setColor(Color.WHITE);
                m_paintG.setColor(Color.WHITE);
                m_paintB.setColor(Color.WHITE);
                m_paintE_light.setColor(Color.WHITE);
                m_player_D.start();
                m_currFreq = notesFreq.get(0);
                break;
            case 1:
                m_paintD.setColor(Color.WHITE);
                m_paintA.setColor(Color.GREEN);
                m_paintE_fat.setColor(Color.WHITE);
                m_paintG.setColor(Color.WHITE);
                m_paintB.setColor(Color.WHITE);
                m_paintE_light.setColor(Color.WHITE);
                m_player_A.start();
                m_currFreq = notesFreq.get(1);
                break;
            case 2:
                m_paintD.setColor(Color.WHITE);
                m_paintA.setColor(Color.WHITE);
                m_paintE_fat.setColor(Color.GREEN);
                m_paintG.setColor(Color.WHITE);
                m_paintB.setColor(Color.WHITE);
                m_paintE_light.setColor(Color.WHITE);
                m_player_E_fat.start();
                m_currFreq = notesFreq.get(2);
                break;
            case 3:
                m_paintD.setColor(Color.WHITE);
                m_paintA.setColor(Color.WHITE);
                m_paintE_fat.setColor(Color.WHITE);
                m_paintG.setColor(Color.GREEN);
                m_paintB.setColor(Color.WHITE);
                m_paintE_light.setColor(Color.WHITE);
                m_player_G.start();
                m_currFreq = notesFreq.get(3);
                break;
            case 4:
                m_player_B.start();
                m_paintD.setColor(Color.WHITE);
                m_paintA.setColor(Color.WHITE);
                m_paintE_fat.setColor(Color.WHITE);
                m_paintG.setColor(Color.WHITE);
                m_paintB.setColor(Color.GREEN);
                m_paintE_light.setColor(Color.WHITE);
                m_currFreq = notesFreq.get(4);
                break;
            case 5:
                m_paintD.setColor(Color.WHITE);
                m_paintA.setColor(Color.WHITE);
                m_paintE_fat.setColor(Color.WHITE);
                m_paintG.setColor(Color.WHITE);
                m_paintB.setColor(Color.WHITE);
                m_paintE_light.setColor(Color.GREEN);
                m_player_E_light.start();
                m_currFreq = notesFreq.get(5);
                break;
            case 6:
                String title = getResources().getString(R.string.test_manual);
                String message = getResources().getString(R.string.test_text);
                AlertDialog.Builder builder = new AlertDialog.Builder(m_app);
                builder.setTitle(title)
                        .setMessage(message)
                        //.setIcon(R.drawable.background)
                        .setCancelable(false)
                        .setNegativeButton("OK",
                                new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int id) {
                                        dialog.cancel();
                                    }
                                });
                AlertDialog alert = builder.create();
                alert.show();
        }
        Log.d("TUNER", "m_currFreq=" + String.valueOf(m_currFreq));
//        if (m_audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
//            m_audioRecord.startRecording();
//            Log.d("TUNER", "startRecording");
//        }
    }

    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int buttonID = getTouchButtonID(x, y);
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            m_lastTouchDownButtonID = buttonID;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (buttonID == m_lastTouchDownButtonID) {
                m_lastTouchDownButtonID = -1;
                onButtonClick(buttonID);
            }
        }

        return true;
    }

    @Override
    public void onMarkerReached(AudioRecord recorder) {

    }

    public void fft(double[] x, double[] y, int n) {
        int i, j, k, n1, n2, a;
        double c, s, t1, t2;
        int m = (int) (Math.log(n) / Math.log(2));
        // Bit-reverse
        j = 0;
        n2 = n / 2;
        for (i = 1; i < n - 1; i++) {
            n1 = n2;
            while (j >= n1) {
                j = j - n1;
                n1 = n1 / 2;
            }
            j = j + n1;

            if (i < j) {
                t1 = x[i];
                x[i] = x[j];
                x[j] = t1;
                t1 = y[i];
                y[i] = y[j];
                y[j] = t1;
            }
        }

        // FFT
        n1 = 0;
        n2 = 1;

        for (i = 0; i < m; i++) {
            n1 = n2;
            n2 = n2 + n2;
            a = 0;

            for (j = 0; j < n1; j++) {
                c = cos(a);
                s = sin(a);
                a += 1 << (m - i - 1);

                for (k = j; k < n; k = k + n2) {
                    t1 = c * x[k + n1] - s * y[k + n1];
                    t2 = s * x[k + n1] + c * y[k + n1];
                    x[k + n1] = x[k] - t1;
                    y[k + n1] = y[k] - t2;
                    x[k] = x[k] + t1;
                    y[k] = y[k] + t2;
                }
            }
        }
    }

    @Override
    public void onPeriodicNotification(AudioRecord recorder) {
        Log.d("TUNER", "onPeriodicNotification called");

        int readedFramesNum = recorder.read(m_audioData, 0, m_winSize);
        if (readedFramesNum < m_winSize)
            return;
//
//        double power = 0;
//
//        for (int i = 0; i < m_winSize; ++i)
//            y[i] = 0;
//        for (int i = 0; i < m_winSize; ++i)
//            x[i] = (double)m_audioDaa[i] * (0.53836 - 0.461648 * cos((2 * PI * i) / (m_winSize - 1)));
////
////        //(x, y, m_winSize);
////        m_fft.fft(x, y);
////
////        for (int i = 0; i < m_winSize / 2; ++i) {
////            double power_i = x[i] * x[i] + y[i] * y[i];
////            power += power_i;
////            m_spectr[i] = Math.log(power_i);
////        }
////
////
////        for (int i = 0; i < m_winSize/2; ++i)
////            y[i] = 0;
////        for (int i = 0; i < m_winSize/2; ++i)
////            x[i] = m_spectr[i];
////
////
////        m_spectrFFT.fft(x, y);
////        for (int i = 0; i < m_winSize / 4; ++i) {
////            m_averageSpectr[i] = Math.sqrt(x[i] * x[i] + y[i] * y[i]);
////        }
////
////        double maxCepstr = -100500;
////        int maxCepstr_i = -1;
////        for (int i = 1; i < m_winSize / 4; ++i) {
////            if (m_averageSpectr[i] > maxCepstr) {
////                maxCepstr = m_averageSpectr[i];
////                maxCepstr_i = i;
////            }
////        }
////t
//        Log.d("TUNER", "maxCepstr_i: " + String.valueOf(maxCepstr_i));
//
//        Log.d("TUNER", "POWER: " + String.valueOf(power));
//        Log.d("TUNER", "SIGNAL LENGTH: " + String.valueOf((double) (readedFramesNum) / m_samplerate));
//
//        //invalidate();
    }
}
