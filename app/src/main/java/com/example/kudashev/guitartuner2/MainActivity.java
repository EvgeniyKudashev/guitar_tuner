package com.example.kudashev.guitartuner2;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.view.*;
//import android.widget.*;

import java.util.Locale;
import android.util.Log;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.content.res.*;
import android.graphics.*;
import android.widget.Toast;
//import android.view.ViewGroup.LayoutParams;





// ****************************************************************************

public class MainActivity extends Activity implements  OnCompletionListener, View.OnTouchListener
{
    // ********************************************
    // CONST
    // ********************************************
    public static final int	VIEW_INTRO		= 0;
    public static final int	VIEW_GAME       = 1;


    // *************************************************
    // DATA
    // *************************************************
    int						m_viewCur = -1;

    AppIntro				m_app;
    ViewIntro			    m_viewIntro;
    ViewApp				    m_viewApp;


    // screen dim
    int						m_screenW;
    int						m_screenH;


    // *************************************************
    // METHODS
    // *************************************************
    protected void onCreate(Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                1);
        //overridePendingTransition(0, 0);
        // No Status bar
        final Window win = getWindow();
        win.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // Application is never sleeps
        win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Display display = getWindowManager().getDefaultDisplay();
        Point point = new Point();
        display.getSize(point);
        m_screenW = point.x;
        m_screenH = point.y;

        Log.d("THREE", "Screen size is " + String.valueOf(m_screenW) + " * " +  String.valueOf(m_screenH) );

        // Detect language
        String strLang = Locale.getDefault().getDisplayLanguage();
        int language;
        if (strLang.equalsIgnoreCase("english"))
        {
            Log.d("THREE", "LOCALE: English");
            language = AppIntro.LANGUAGE_ENG;
        }
        else if (strLang.equalsIgnoreCase("русский"))
        {
            Log.d("THREE", "LOCALE: Russian");
            language = AppIntro.LANGUAGE_RUS;
        }
        else
        {
            Log.d("THREE", "LOCALE unknown: " + strLang);
            language = AppIntro.LANGUAGE_UNKNOWN;
            //AlertDialog alertDialog;
            //alertDialog = new AlertDialog.Builder(this).create();
            //alertDialog.setTitle("Language settings");
            //alertDialog.setMessage("This application available only in English or Russian language.");
            //alertDialog.show();
        }
        // Create application
        m_app = new AppIntro(this, language);
        // Create view
        setView(VIEW_INTRO);


    }
    public void setView(int viewID)
    {
        if (m_viewCur == viewID)
        {
            Log.d("THREE", "setView: already set");
            return;
        }

        m_viewCur = viewID;
        if (m_viewCur == VIEW_INTRO)
        {
            m_viewIntro = new ViewIntro(this);
            setContentView(m_viewIntro);
        }
        if (m_viewCur == VIEW_GAME)
        {
            m_viewApp = new ViewApp(this);
            Log.d("THREE", "Switch to m_viewGame" );
            setContentView(m_viewApp);
           // m_viewApp.start();
        }
    }

    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.

        // delayedHide(100);
    }
    public void onCompletion(MediaPlayer mp)
    {
        Log.d("THREE", "onCompletion: Video play is completed");
        //switchToGame();
    }


    public boolean onTouch(View v, MotionEvent evt)
    {
        int x = (int)evt.getX();
        int y = (int)evt.getY();
        int touchType = AppIntro.TOUCH_DOWN;

        //if (evt.getAction() == MotionEvent.ACTION_DOWN)
        //  Log.d("THREE", "Touch pressed (ACTION_DOWN) at (" + String.valueOf(x) + "," + String.valueOf(y) +  ")"  );

        if (evt.getAction() == MotionEvent.ACTION_MOVE)
            touchType = AppIntro.TOUCH_MOVE;
        if (evt.getAction() == MotionEvent.ACTION_UP)
            touchType = AppIntro.TOUCH_UP;

        if (m_viewCur == VIEW_INTRO)
            return m_viewIntro.onTouch( x, y, touchType);
        /*if (m_viewCur == VIEW_GAME)
            return m_viewGame.onTouch(x, y, touchType);*/
        {
        }
        return true;
    }
    public boolean onKeyDown(int keyCode, KeyEvent evt)
    {
        if (keyCode == KeyEvent.KEYCODE_BACK)
        {
            //Log.d("THREE", "Back key pressed");
            //boolean wantKill = m_app.onKey(Application.KEY_BACK);
            //if (wantKill)
            //		finish();
            //return true;
        }
        boolean ret = super.onKeyDown(keyCode, evt);
        return ret;
    }
    public AppIntro getApp()
    {
        return m_app;
    }

    protected void onResume()
    {
        super.onResume();
        if (m_viewCur == VIEW_INTRO)
            m_viewIntro.start();
        /*if (m_viewCur == VIEW_GAME)
            m_viewGame.start();*/
        //Log.d("THREE", "App onResume");
    }
    protected void onPause()
    {
        // stop anims
        if (m_viewCur == VIEW_INTRO)
            m_viewIntro.stop();
        /*if (m_viewCur == VIEW_GAME)
            m_viewGame.onPause();*/

        // complete system
        super.onPause();
        //Log.d("THREE", "App onPause");
    }
    protected void onDestroy()
    {
        /*if (m_viewCur == VIEW_GAME)
            m_viewGame.onDestroy();*/
        super.onDestroy();
    }
    public void onConfigurationChanged(Configuration confNew)
    {
        super.onConfigurationChanged(confNew);
        m_viewIntro.onConfigurationChanged(confNew);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 1: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                } else {
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                    Toast.makeText(MainActivity.this, "Permission denied to read your External storage", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

}

