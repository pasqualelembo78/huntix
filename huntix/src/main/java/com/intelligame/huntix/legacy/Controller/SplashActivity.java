package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.R;

public class SplashActivity extends Activity {

    private final int LOCATION_PERMISSION = 1;
    boolean permissao_local = false;
    private WebView webView;
    private TextView versaoApp;
    private static int SPLASH_TIME_OUT = 6000;
    private MediaPlayer mp;
    private boolean navegou = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        configuraVistaIniziale();
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},LOCATION_PERMISSION);
        else
            configuraSuonoApertura();

        // Fallback: se la musica non parte/termina, passa comunque alla prossima schermo
        new android.os.Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                vaiAllaProssimaSchermata();
            }
        }, SPLASH_TIME_OUT + 3000);
    }

    protected void vaiAllaProssimaSchermata(){
        if(navegou || isFinishing()) return;
        navegou = true;
        try { if (mp != null) mp.stop(); } catch (Exception ignored) {}
        if(GiocoSingleton.getInstance().sessione()) {
            Intent i = new Intent(getBaseContext(), MapActivity.class);
            startActivity(i);
            finish();
        }else {
            Intent i = new Intent(getBaseContext(), LoginActivity.class);
            startActivity(i);
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    protected void configuraVistaIniziale(){
        //Mantiene lo schermo di splash acceso mentre viene mostrato.
        WindowManager.LayoutParams params = this.getWindow().getAttributes();
        params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;

        //Configura la web view loader per il caricamento dell'app
        webView = (WebView) findViewById(R.id.loaderSplash);
        webView.loadUrl("file:///android_asset/loading.gif");
        webView.setBackgroundColor(Color.TRANSPARENT);

        //mostra la versione dell'app dinamicamente
        versaoApp = (TextView) findViewById(R.id.versaoApp);

        //OTTIEVE LA VERSIONE DELL'APPLICAZIONE
        try {
            PackageInfo pInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            String version = pInfo.versionName;
            versaoApp.setText("v. " + version);
        }catch (Exception e){
            Log.e("SPLASH","ERRORE: " + e.getMessage());
        }
    }

    protected void configuraSuonoApertura(){
        try {
            mp = MediaPlayer.create(this, R.raw.abertura2);
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    //Esegue quando finisce la musica di apertura
                    vaiAllaProssimaSchermata();
                }
            });
            mp.start();
        }catch (Exception e){
            Log.e("SPLASH","ERRORE: " + e.getMessage());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode){
            case LOCATION_PERMISSION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Permesso concesso", 5000);
                    Log.d("PERMISSIONE", "HA CONSENTITO L'USO DELLA CAMERA");
                    permissao_local=true;
                    configuraSuonoApertura();
                }
                else {
                    Toast.makeText(this, "Permesso necessario per conoscere la tua posizione", 5000);
                    Log.d("PERMISSIONE", "NON HA CONSENTITO L'USO DEL GPS");
                    permissao_local=true;
                    configuraSuonoApertura();
                }
            }
        }
    }
}
