package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.RotateAnimation;
import android.widget.AbsoluteLayout;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

import com.intelligame.huntix.legacy.Model.Apparizione;
import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Creatura;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.Util.ViewUnitsUtil;
import com.intelligame.huntix.legacy.View.CameraPreview;


public class CatturaActivity extends Activity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor sensor;
    private Sensor accelerometer;
    public ImageView img;
    public ImageView secchiello;
    public int dimenX;  //dimensione orizzontale dello schermo in pixel
    public int dimenY;  //dimensione verticale dello schermo in pixel
    public float centerX;   //centro orizzontale regolato
    public float centerY;   //centro verticale regolato
    public float escalaX;   //usata per convertire le letture in pixel
    public float escalaY;   //usata per convertire le letture in pixel

    public float centerXsecchiello;   //centro orizzontale regolato
    public float centerYsecchiello;   //centro verticale regolato

    public float grauXtotal = 0;
    public float grauYtotal = 0;
    public float grauZtotal = 0;

    public float grauXnuovo = 0;
    public float grauYnuovo = 0;
    public float grauZnuovo = 0;

    public float grauXant = 0;
    public float grauYant = 0;
    public float grauZant = 0;

    float distanzaTopoY;
    float distanzaBaseY;
    float distanzaEsquerdaX;
    float distanzaDireitaX;

    public float percentImageCreatura = (float) 0.5;
    public boolean immagineCreaturaPreparada = false;
    public float larghezzaImgCreatura = 0;
    public float altezzaImgCreatura = 0;
    public float[] limitesCreatura;

    public float percentImageSecchiello = (float) 0.15;
    public boolean immagineSecchielloPreparada = false;
    public float larghezzaImgSecchiello = 0;
    public float altezzaImgSecchiello = 0;
    public float[] limitesSecchiello;

    public boolean capturou = false;

    public float xInicioTouch = 0;
    public float yInicioTouch = 0;
    public float xFimTouch = 0;
    public float yFimTouch = 0;
    public long tempoInicial = 0;
    public long tempoFinal = 0;
    float diferencaX;
    float diferencaY;
    long duracaoTouch;
    float velocidadeX; //pixel per millisecondo
    float velocidadeY; //pixel per millisecondo
    float velocidadeXoriginal;
    float velocidadeYoriginal;
    //public MovimentoSecchiello deslocamentoSecchiello;

    public MediaPlayer mp;
    public int countSound = 0; // initialise outside listener to prevent looping

    private CameraPreview mPreview;
    private Camera mCamera;

    private TextView nomeCreaturaCattura;

    private Creatura creatura;
    private Apparizione ap;

    private MediaPlayer mpBattle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //setContentView(R.layout.view_cattura);
        preparaCamera();

        //Ottiene il gestore dei sensori
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        //Ottiene i sensori da utilizzare
        sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        //Ottiene la risoluzione dello schermo
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        dimenX = size.x;
        dimenY = size.y;

        //Toast.makeText(this, "X: " + dimenX + "px Y: " + dimenY + "px", Toast.LENGTH_LONG).show();

        //Avvia il tema della battaglia creatura
        mpBattle = MediaPlayer.create(getBaseContext(), R.raw.battle);
        mpBattle.setLooping(true);
        impostaVolumeMediaPlayer(mpBattle, 90); //riduce il volume del tema della battaglia per compensare l'audio troppo alto
        mpBattle.start();
    }

    @Override
    protected void onResume() {
        super.onResume();

        //Riceve Intent con Apparizione proveniente dalla mappa
        Intent it = getIntent();
        ap = (Apparizione) it.getSerializableExtra("creatura");
        creatura = ap.getCreatura();

        //TODO: RISOLTO - cerca nella lista di creature del gestore la creatura ricevuta dallo schermo precedente.
        //creatura = GiocoSingleton.getInstance().convertCreaturaSerializableToObject(creatura);

        //Imposta l'etichetta avvisando se la creatura è conosciuta o nuova
        TextView labelCreaturaNuovo = (TextView) findViewById(R.id.labelCreaturaNuovo);
        if(GiocoSingleton.getInstance().getUtente().getQuantitaCatture(creatura) > 0)
            labelCreaturaNuovo.setText("conhecido");
        else
            labelCreaturaNuovo.setText("nuovo");

        //imposta l'etichetta con il nome della creatura da catturare
        nomeCreaturaCattura = (TextView) findViewById(R.id.txtNomeCreaturaCattura);
        //garantisce di eseguire solo dopo che le view sono a schermo
        nomeCreaturaCattura.post(new Runnable() {
            @Override
            public void run() {
                nomeCreaturaCattura.setText(creatura.getNome());
                nomeCreaturaCattura.measure(0,0);
                //Posiziona il nome della creatura sul lato superiore destro con margine di 8dp a destra
                nomeCreaturaCattura.setX(dimenX - nomeCreaturaCattura.getMeasuredWidth() - ViewUnitsUtil.convertDpToPixel(8));
            }
        });

        img = (ImageView) findViewById(R.id.creatura);
        img.setImageResource(creatura.getFoto());

        secchiello = (ImageView) findViewById(R.id.secchiello);

        configuraSecchiello();
        configuraCreatura();

        if(sensor != null){
            //Inizia ad ascoltare i sensori utilizzati
            sensorManager.registerListener(this, sensor,SensorManager.SENSOR_DELAY_GAME);
        }

        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        //Ferma di ascoltare i sensori
        sensorManager.unregisterListener(this);
        immagineCreaturaPreparada = false;
        immagineSecchielloPreparada = false;

        //mette in pausa la musica della battaglia
        mpBattle.pause();

        //mPreview.releaseCamera();
        //releaseCamera();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        //continua la musica della battaglia
        mpBattle.start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        //ferma la musica della battaglia e restituisce la risorsa al sistema
        mpBattle.pause();
        mpBattle.release();
    }

    private void animate(double fromDegrees, double toDegrees, long durationMillis, ImageView img) {
        final RotateAnimation rotate = new RotateAnimation((float) fromDegrees, (float) toDegrees,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f,
                RotateAnimation.RELATIVE_TO_SELF, 0.5f);
        img.clearAnimation();
        rotate.setDuration(durationMillis);
        rotate.setFillEnabled(true);
        rotate.setFillAfter(true);
        img.startAnimation(rotate);
    }

    private HashMap<Integer, Long> timestamp = new HashMap<>();

    private double getSensorElapsedSeconds(SensorEvent event) {
        Long lastTimestamp = timestamp.put(event.sensor.getType(), event.timestamp);

        if (lastTimestamp == null)
            return 0;

        return (event.timestamp - lastTimestamp) / 1000000000f;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_GYROSCOPE:
                onGyroscopeChanged(event);
                break;
            case Sensor.TYPE_LINEAR_ACCELERATION:
                onAccelerationChanged(event);
                break;
            default:
                throw new IllegalStateException("Unreachable");
        }

    }

    double accelerationNoise, speed, distance;
    long accelerationSamples;

    private double clamp(double value, double min, double max) {
        return value < min ? min : (value > max ? max : value);
    }

    private void onAccelerationChanged(SensorEvent event) {
        double elapsed = getSensorElapsedSeconds(event);

        double accelerationSensor = -event.values[2];
        double accelerationSensorMagnitude = Math.abs(accelerationSensor);
        double accelerationSensorDirection = Math.signum(accelerationSensor);

        accelerationNoise += (1 / ++accelerationSamples) * (accelerationSensorMagnitude - accelerationNoise);

        if (immagineCreaturaPreparada && immagineSecchielloPreparada) {
            //Atenua il rumore nell'accelerazione.
            double acceleration = Math.max(accelerationSensorMagnitude - accelerationNoise, 0) * accelerationSensorDirection;

            speed = clamp(speed + acceleration * elapsed, -0.25f, +0.25f);
            distance = clamp(distance + speed * elapsed, 0.25f, 0.75f);

            percentImageCreatura = 1f - (float) distance;
            configuraCreatura();

            Log.i("Accel", String.format("A=%.1f S=%.1f D=%.1f N=%.1f", acceleration, speed, distance, accelerationNoise));
        }
    }

    public void onGyroscopeChanged(SensorEvent event) {
        if(immagineCreaturaPreparada && immagineSecchielloPreparada) {
            float xNuovo = img.getX();
            float yNuovo = img.getY();

            //mantiene lo schermo acceso
            WindowManager.LayoutParams params = this.getWindow().getAttributes();
            params.flags = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            this.getWindow().setAttributes(params);

            //Lettura dei valori dell'accelerometro - USARE QUANDO L'ORIENTAMENTO È BLOCCATO IN PORTRAIT
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            //ottiene i gradi spostati dall'ultima misurazione
            grauXnuovo = (float) ((x * 57.2958) * 0.02); //0.02 secondi a causa del SENSOR_DELAY_GAME
            grauYnuovo = (float) ((y * 57.2958) * 0.02); //0.02 secondi a causa del SENSOR_DELAY_GAME
            grauZnuovo = (float) ((z * 57.2958) * 0.02); //0.02 secondi a causa del SENSOR_DELAY_GAME

            //totale di gradi spostati dall'inizio
            grauXtotal += grauXnuovo;
            grauYtotal += grauYnuovo;
            grauZtotal += grauZnuovo;

            Log.i("Posizione", "X: " + x + " Y: " + y + " Z: " + z);
            Log.i("GrauNuovo", "X: " + grauXnuovo + " Y: " + grauYnuovo + " Z: " + grauZnuovo);
            Log.i("Grau", "X: " + grauXtotal + " Y: " + grauYtotal + " Z: " + grauZtotal);

            //aggiorna la posizione dell'immagine se il dispositivo viene spostato notevolmente
            if (grauYtotal > grauYant + 0.01 || grauYtotal < grauYant - 0.01) {
                xNuovo = img.getX() + (grauYnuovo * escalaX);
                img.setX(xNuovo);
            } else {
                grauYtotal = grauYant; //elimina i piccoli rumori del sensore con dispositivo fermo
            }

            //aggiorna la posizione dell'immagine se il dispositivo viene spostato notevolmente
            if (grauXtotal > grauXant + 0.01 || grauXtotal < grauXant - 0.01) {
                yNuovo = img.getY() + (grauXnuovo * escalaY);
                img.setY(yNuovo);
            } else {
                grauXtotal = grauXant; //elimina i piccoli rumori del sensore con dispositivo fermo
            }

            //aggiorna la posizione dell'immagine se il dispositivo viene spostato notevolmente
            if (grauZtotal > grauZant + 0.01 || grauZtotal < grauZant - 0.01) {
                //rodarImmagine(img,grauZtotal);
                //TODO - compensare la modifica di larghezza e altezza dell'immagine dopo la rotazione evitando il taglio dell'immagine

                //animate(0,grauZtotal,0,img);

                Log.d("Pivot", "X: " + img.getPivotX() + " Y: " + img.getPivotY());

                img.setRotation(grauZtotal);
            } else {
                grauZtotal = grauZant; //elimina i piccoli rumori del sensore con dispositivo fermo
            }

            //memorizza la misurazione per confrontarla con la prossima
            grauXant = grauXtotal;
            grauYant = grauYtotal;
            grauZant = grauZtotal;

            Log.i("IMMAGINE", "X: " + img.getX() + " Y: " + img.getY());

            //RIPORTANDO L'IMMAGINE ALLO SCHERMO PER COMPLETARE IL GIRO DI 360°-------------------------------

            //ruotando orizzontalmente verso destra
            if (grauYtotal < 0) {
                //ottiene la differenza in gradi rispetto al centro
                if (Math.abs(Math.abs(grauYtotal) - 360) <= distanzaDireitaX / escalaX) {
                    img.setX(dimenX - 10); //compensa i limiti perché il confronto sia possibile, dato che il suo primo valore sarà sempre positivo
                    centerX = img.getX();
                    distanzaEsquerdaX = centerX;
                    distanzaDireitaX = dimenX - centerX;
                    grauYtotal = 0;
                }
            }

            //ruotando orizzontalmente verso sinistra
            //sistemare il rientro compensando la larghezza dell'immagine - FATTO
            if (grauYtotal > 0) {
                //ottiene la differenza in gradi rispetto al centro
                if (Math.abs(Math.abs(grauYtotal) - 360) <= (distanzaEsquerdaX + larghezzaImgCreatura) / escalaX) {
                    //if(Math.abs(Math.abs(grauYtotal) - 360) <= distanzaEsquerdaX/escalaX){

                    Log.d("Passato", "DA: " + distanzaEsquerdaX + " DimX: " + dimenX + " PI: " + percentImageCreatura + " EX: " + escalaX);
                    Log.d("Passato", "sono stato qui orizzontale " + grauYtotal + " " + (distanzaEsquerdaX + larghezzaImgCreatura) / escalaX);

                    //img.setX(5); //compensa i limiti
                    //img.setX(-img.getMeasuredWidth());

                    img.setX(-larghezzaImgCreatura);
                    centerX = img.getX();
                    //distanzaEsquerdaX = centerX;
                    //distanzaEsquerdaX = escalaX; //per forçar quoziente 1 dopo primo loop
                    distanzaEsquerdaX = escalaX - larghezzaImgCreatura; //per forzare il quoziente 1 dopo il primo loop
                    distanzaDireitaX = dimenX - centerX;
                    grauYtotal = 0;
                }
            }

            //ruotando verticalmente verso l'alto
            //sistemare il rientro compensando l'altezza dell'immagine - FATTO
            if (grauXtotal > 0) {
                //ottiene la differenza in gradi rispetto al centro
                if (Math.abs(Math.abs(grauXtotal) - 360) <= (distanzaTopoY + altezzaImgCreatura) / escalaY) {
                    //if(Math.abs(Math.abs(grauXtotal) - 360) <= distanzaTopoY/escalaY){

                    Log.d("Passato", "sono stato qui verticale " + grauXtotal + " " + (distanzaTopoY + altezzaImgCreatura) / escalaY);

                    //img.setY(5); //compensa i limiti
                    //img.setY(-img.getMeasuredHeight());

                    img.setY(-altezzaImgCreatura);
                    centerY = img.getY();
                    //distanzaTopoY = centerY;
                    //distanzaTopoY = escalaY; //per forçar quoziente 1 dopo primo loop
                    distanzaTopoY = escalaY - altezzaImgCreatura; //per forzare il quoziente 1 dopo il primo loop
                    distanzaBaseY = dimenY - centerY;
                    grauXtotal = 0;
                }
            }

            //ruotando verticalmente verso il basso
            if (grauXtotal < 0) {
                //ottiene la differenza in gradi rispetto al centro
                if (Math.abs(Math.abs(grauXtotal) - 360) <= distanzaBaseY / escalaY) {
                    img.setY(dimenY - 10); //compensa i limiti
                    centerY = img.getY();
                    distanzaTopoY = centerY;
                    distanzaBaseY = dimenY - centerY;
                    grauXtotal = 0;
                }
            }

            //ottiene i limiti della creatura
            //TODO: ottenere i limiti anche fuori da onSensorChanged per permettere ai telefoni senza giroscopio di giocare
            limitesCreatura = getLeftRightTopBottomImage(img.getX(),img.getY(),altezzaImgCreatura,larghezzaImgCreatura);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
        //cambiata la precisione
    }

    public void preparaCamera(){
        // Hide the window title.
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_cattura);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Create an instance of Camera
       // mCamera = getCameraInstance();

        // Create our Preview view and set it le the content of our activity.
        mPreview = new CameraPreview(this);
        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.camera_preview);
        frameLayout.addView(mPreview);

        AbsoluteLayout absolutLayoutControls = (AbsoluteLayout) findViewById(R.id.immagineAR);
        absolutLayoutControls.bringToFront();

    }

    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open();
        }
        catch (Exception e){
            // Camera is not available (in use or does not exist)
        }
        return c; // returns null if camera is unavailable
    }

    public float[] getLeftRightTopBottomImage(float x, float y, float altezza, float larghezza){
        float limites[] = new float[4];
        limites[0] = x;
        limites[1] = x + larghezza;
        limites[2] = y;
        limites[3] = y + altezza;

        Log.d("Limites", "E: " + limites[0] + " D: " + limites[1] + " C: " + limites[2] + " B: " + limites[3]);
        return limites;
    }

    public void configuraCreatura(){
        //garantisce di eseguire solo dopo che le view sono a schermo
        img.post(new Runnable() {
            @Override
            public void run() {
                //imposta la larghezza della creatura in rapporto alla dimensione dello schermo
                larghezzaImgCreatura = dimenX * percentImageCreatura;
                //ottiene la proporzione dell'immagine ridimensionata
                float proporcaoCreatura = (larghezzaImgCreatura * 100) / img.getMeasuredWidth();
                //imposta l'altezza della creatura in modo proporzionale
                altezzaImgCreatura = img.getMeasuredHeight() * proporcaoCreatura / 100;

                //ottiene il centro dello schermo
                //POSIZIONARLO CASUALE Più avanti
                centerX = dimenX / 2 - (((int) larghezzaImgCreatura) / 2);
                centerY = dimenY / 2 - (((int) altezzaImgCreatura) / 2);

                //X: 1200 Y: 1834 CX: 300.0 CY: 459.0 IMG_X: 600 IMG_Y: 917

                //modifica la dimensione dell'immagine e la centra sullo schermo
                AbsoluteLayout.LayoutParams params = new AbsoluteLayout.LayoutParams((int) larghezzaImgCreatura, (int) altezzaImgCreatura, (int) centerX, (int) centerY);
                img.setLayoutParams(params);

                //calcola le distanze iniziali
                distanzaTopoY = centerY;
                distanzaBaseY = dimenY - centerY;
                distanzaEsquerdaX = centerX;
                distanzaDireitaX = dimenX - centerX;

                //calcola la scala
                escalaX = dimenX / 72; //ogni grado vale pixel di scala - 72º è il campo visivo considerato
                escalaY = dimenY / 72;

                Log.i("Dimensione", "X: " + dimenX + " Y: " + dimenY + " CX: " + centerX + " CY: " + centerY +
                        " IMG_X: " + (int) larghezzaImgCreatura + " IMG_Y: " + (int) altezzaImgCreatura);

                immagineCreaturaPreparada = true;
            }
        });
    }

    public void configuraEffettoCattura(){
        mp = MediaPlayer.create(getBaseContext(), R.raw.quicando);
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener(){
            int maxCount = 3;
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if(countSound < maxCount) {
                    countSound++;
                    mediaPlayer.seekTo(0); //TODO: ricercare cosa faccia il metodo seekTo di MediaPlayer
                    mediaPlayer.start();

                    //anima il secchiello insieme al suono di rimbalzo
                    if(countSound % 2 == 0) {
                        //secchiello.setRotation(0);
                        secchiello.animate().rotation(0).start();

                    } else {
                        //secchiello.setRotation(-20);
                        secchiello.animate().rotation(-20).start();
                    }


                }else{
                    countSound = 0;

                    MediaPlayer mp2 = MediaPlayer.create(getBaseContext(), R.raw.sucesso);
                    mp2.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            //TODO: inviare la cattura al server prima di chiudere la schermata. Forse farlo direttamente nel metodo catturare
                            GiocoSingleton.getInstance().getUtente().catturare(ap);
                            GiocoSingleton.getInstance().aumentaXp("cattura");   //aggiorna XP dell'utente dopo una cattura
                            finish(); //chiude il modulo di cattura quando finisce la musica di successo
                        }
                    });

                    //mette in pausa la musica della battaglia e inizia quella di successo
                    mpBattle.pause();
                    mp2.start();

                    Toast.makeText(getBaseContext(),creatura.getNome() + " è stata catturata! \\o/",Toast.LENGTH_LONG).show();
                }
            }});
        //riduce il volume del tema della battaglia prima che il secchiello rimbalzi
        impostaVolumeMediaPlayer(mpBattle, 85);

        mp.start();

        //sostituisce l'immagine della creatura con l'esplosione
        img.setImageResource(R.drawable.explosion);

        img.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(350);
                    img.setVisibility(View.INVISIBLE);
                } catch (Exception e) {

                }
            }
        });
    }

    public void impostaVolumeMediaPlayer(MediaPlayer mediaPlayer, int volume){
        int maxVolume = 100;
        float log1=(float)(Math.log(maxVolume-volume)/Math.log(maxVolume));
        mediaPlayer.setVolume(1-log1,1-log1);
    }

    public void configuraSecchiello(){
        //garantisce di eseguire solo dopo che le view sono a schermo
        secchiello.post(new Runnable() {
            @Override
            public void run() {
                //imposta la larghezza del secchiello in rapporto alla dimensione dello schermo
                larghezzaImgSecchiello = dimenX * percentImageSecchiello;
                //ottiene la proporzione dell'immagine ridimensionata
                float proporcaoSecchiello = (larghezzaImgSecchiello * 100) / secchiello.getMeasuredWidth();
                //imposta l'altezza del secchiello in modo proporzionale
                altezzaImgSecchiello = secchiello.getMeasuredHeight() * proporcaoSecchiello / 100;

                //ottiene il centro dello schermo
                centerXsecchiello = dimenX / 2 - (((int) larghezzaImgSecchiello) / 2);
                centerYsecchiello = dimenY - (int) altezzaImgSecchiello - 75; //meno 40 per compensare la barra delle attività di Android

                //X: 1200 Y: 1834 CX: 300.0 CY: 459.0 IMG_X: 600 IMG_Y: 917

                //modifica la dimensione dell'immagine e la centra sullo schermo
                AbsoluteLayout.LayoutParams params = new AbsoluteLayout.LayoutParams((int) larghezzaImgSecchiello, (int) altezzaImgSecchiello, (int) centerXsecchiello, (int) centerYsecchiello);
                secchiello.setLayoutParams(params);

                Log.i("Dimensione", "X: " + dimenX + " Y: " + dimenY + " CX_secchiello: " + centerXsecchiello + " CY_secchiello: " + centerYsecchiello +
                        " IMG_X_secchiello: " + (int) larghezzaImgSecchiello + " IMG_Y_secchiello: " + (int) altezzaImgSecchiello);

                immagineSecchielloPreparada = true;
            }
        });

        configuraTouchSecchiello();
    }

    public void configuraTouchSecchiello(){
        //configura il listener di tocco sul secchiello
        secchiello.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                //TODO: RICERCARE IL COMPORTAMENTO ESATTO DEI METODI getRawX() e getRawY()
                float x = event.getRawX();
                float y = event.getRawY();
                Log.d("Muovi Secchiello", "X: " + x + " Y: " + y);

                switch (event.getAction() & MotionEvent.ACTION_MASK) {
                    case MotionEvent.ACTION_DOWN:
                    case MotionEvent.ACTION_POINTER_DOWN:
                        //gestione se ha premuto l'immagine
                        mp = MediaPlayer.create(getBaseContext(), R.raw.arremesso);
                        mp.start();

                        tempoInicial = System.currentTimeMillis();
                        xInicioTouch = secchiello.getX();
                        yInicioTouch = secchiello.getY();

                        Log.d("Muovi Secchiello", "Toccato il secchiello");
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_POINTER_UP:
                        //gestione se ha rimosso il dito dall'immagine

                        tempoFinal = System.currentTimeMillis();
                        xFimTouch = secchiello.getX();
                        yFimTouch = secchiello.getY();

                        diferencaX = Math.abs(xInicioTouch - xFimTouch);
                        diferencaY = Math.abs(yInicioTouch - yFimTouch);
                        duracaoTouch = tempoFinal - tempoInicial;

                        velocidadeX = diferencaX / duracaoTouch; //pixel per millisecondo
                        velocidadeY = diferencaY / duracaoTouch; //pixel per millisecondo

                        //avvia il thread
                        //deslocamentoSecchiello = new MovimentoSecchiello();
                        //deslocamentoSecchiello.execute(); //MÉTODO doInBackground della classe MovimentoSecchiello è eseguito

                        velocidadeXoriginal = velocidadeX;
                        velocidadeYoriginal = velocidadeY;

                        while (velocidadeX > 0 && velocidadeY > 0 && !capturou) {
                            int tempo = 25; //compensa la velocità per far accelerare di più il secchiello
                            //secchiello lanciato verso destra
                            if (xFimTouch >= xInicioTouch) {
                                secchiello.setX(secchiello.getX() + (tempo * velocidadeX));
                            } else {
                                secchiello.setX(secchiello.getX() - (tempo * velocidadeX));
                            }

                            //secchiello lanciato verso l'alto
                            if (yFimTouch <= yInicioTouch) {
                                secchiello.setY(secchiello.getY() - (tempo * velocidadeY));
                            } else {
                                secchiello.setY(secchiello.getY() + (tempo * velocidadeY));
                            }

                            //---------------VERIFICA CAPTURA-------------
                            //ottiene i limiti del secchiello
                            limitesSecchiello = getLeftRightTopBottomImage(secchiello.getX(), secchiello.getY(), altezzaImgSecchiello, larghezzaImgSecchiello);
                            Log.d("LimSecchiello", "E: " + limitesSecchiello[0] + " D: " + limitesSecchiello[1] + " C: " + limitesSecchiello[2] + " B: " + limitesSecchiello[3]);

                            //verifica se c'è stata cattura tramite l'intersezione delle immagini
                            if (isCatturato(limitesSecchiello, limitesCreatura, capturou)) {
                                capturou = true;
                                configuraEffettoCattura();
                                Log.w("Muovi Secchiello", "Catturato con lancio " + getTime());
                                break;
                            }
                            //--------------------------------------------

                            Log.d("Movimento", "Aggiornato..VX: " + velocidadeX + " VY: " + velocidadeY);

                            //riducendo la velocità del secchiello
                            velocidadeX = velocidadeX - (velocidadeXoriginal * (float) 0.045);
                            velocidadeY = velocidadeY - (velocidadeYoriginal * (float) 0.045);
                        }

                        if(secchiello.getX() > dimenX || secchiello.getX() < 0 ||
                                secchiello.getY() > dimenY || secchiello.getY() < 0){

                            Toast.makeText(getBaseContext(),"Riprova...",Toast.LENGTH_SHORT).show();

                            secchiello.setX(centerXsecchiello);
                            secchiello.setY(centerYsecchiello);
                        }

                        Log.d("Muovi Secchiello", "Rimosso il dito dal secchiello");
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        //gestione per trascinare l'immagine
                        Log.d("Muovi Secchiello", "Spostato il secchiello");

                        //secchiello.setX(x - (larghezzaImgSecchiello / 2));
                        //secchiello.setY(y - (altezzaImgSecchiello / 2));

                        secchiello.setX(x - (larghezzaImgSecchiello / 2));
                        secchiello.setY((y - (altezzaImgSecchiello / 3)) - (altezzaImgSecchiello / 2)); //-(altezzaImgSecchiello / 3) per smorzare il movimento

                        //---------------VERIFICA CAPTURA-------------
                        //ottiene i limiti del secchiello
                        limitesSecchiello = getLeftRightTopBottomImage(secchiello.getX(), secchiello.getY(), altezzaImgSecchiello, larghezzaImgSecchiello);
                        Log.d("LimSecchiello", "E: " + limitesSecchiello[0] + " D: " + limitesSecchiello[1] + " C: " + limitesSecchiello[2] + " B: " + limitesSecchiello[3]);

                        //verifica se c'è stata cattura tramite l'intersezione delle immagini
                        if (isCatturato(limitesSecchiello, limitesCreatura, capturou)) {
                            Log.w("Muovi Secchiello", "Catturato con tocco " + getTime());
                            capturou = true;
                            configuraEffettoCattura();
                        }
                        //---------------------------------------------

                        return true;

                    default:
                        Log.d("Muovi Secchiello", "Evento non classificato nel secchiello");
                }

                return false;
            }
        });
    }

    public String getTime(){
        Calendar c = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("dd:MMMM:yyyy HH:mm:ss a");
        String strDate = sdf.format(c.getTime());
        return strDate;
    }

    //verifica l'intersezione
    public boolean isCatturato(float pkball[],float creatura[], boolean cap){
        if(pkball[0] <= creatura[1] &&
                creatura[0] <= pkball[1] &&
                pkball[2] <= creatura[3] &&
                creatura[2] <= pkball[3] && !cap){
            return true;
        }else{
            return false;
        }
    }

   /*private void releaseCamera(){
        if (mCamera != null){
            mCamera.release();        // release the camera è other applications
            mCamera = null;
        }
    }*/

    class MovimentoSecchiello extends AsyncTask<String, String, String> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            /*pDialog = new ProgressDialog(Colaboracao.this);
            pDialog.setMessage("INVIO...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(false);
            pDialog.show();*/
        }

        protected String doInBackground(String... args) {
            /*
                        tempoFinal = System.currentTimeMillis();
                        xFimTouch = secchiello.getX();
                        yFimTouch = secchiello.getY();

                        diferencaX = Math.abs(xInicioTouch - xFimTouch);
                        diferencaY = Math.abs(yInicioTouch - yFimTouch);
                        duracaoTouch = tempoFinal - tempoInicial;

                        velocidadeX = diferencaX/duracaoTouch; //pixel per millisecondo
                        velocidadeY = diferencaY/duracaoTouch; //pixel per millisecondo
             */

            velocidadeY = velocidadeY*3;
            velocidadeX = velocidadeX*3;

            velocidadeXoriginal = velocidadeX;
            velocidadeYoriginal = velocidadeY;

            while(velocidadeX > 0 && velocidadeY > 0){
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        int tempo = 100;
                        //secchiello lanciato verso destra
                        if(xFimTouch >= xInicioTouch) {
                            secchiello.setX(secchiello.getX() + (tempo * velocidadeX));
                        }else{
                            secchiello.setX(secchiello.getX() - (tempo * velocidadeX));
                        }

                        //secchiello lanciato verso l'alto
                        if(yFimTouch <= yInicioTouch) {
                            secchiello.setY(secchiello.getY() - (tempo * velocidadeY));
                        }else{
                            secchiello.setY(secchiello.getY() + (tempo * velocidadeY));
                        }

                        try {
                            Thread.sleep(tempo);
                        }catch (Exception e){
                            Log.e("ERRORE", "sleep asyncTask");
                        }

                        Log.d("Movimento", "Aggiornato..VX: " + velocidadeX + " VY: " + velocidadeY);

                        velocidadeX = velocidadeX - (velocidadeXoriginal*(float)0.03);
                        velocidadeY = velocidadeY - (velocidadeYoriginal*(float)0.03);

                        if(velocidadeX < 0 || velocidadeY < 0)
                            return;

                    }
                });

            }



            return null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            /*pDialog.dismiss();
            Log.i("CANCELLATO", "CANCELLATO ASYNC TASK");*/
        }

        protected void onPostExecute(String file_url) {
            //pDialog.dismiss();
            Log.d("Movimento", "finito..");
        }
    }

}


