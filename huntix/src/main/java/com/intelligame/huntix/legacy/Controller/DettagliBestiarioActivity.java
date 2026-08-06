package com.intelligame.huntix.legacy.Controller;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.session.MediaSessionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.intelligame.huntix.legacy.Model.Apparizione;
import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Creatura;
import com.intelligame.huntix.legacy.Model.CreaturaCatturata;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.Util.DatabaseSingleton;
import com.intelligame.huntix.legacy.View.AdapterBestiario;

public class DettagliBestiarioActivity extends Activity implements LocationListener {

    private Creatura creatura;
    private Creatura creaturaEvolucao;
    public LocationManager lm;
    public Criteria criteria;
    public String provider;
    public int TEMPO = 5000;
    public int DIST = 0;
    public Location atual;

    private int caramelleNecessarie;
    private int caramelleOttenuti;

    //Costante ausiliaria per il metodo setResult()
    private final int ATUALIZAR_TELA = 1;

    public void configuraCriterioLocation() {
        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        criteria = new Criteria();

        PackageManager packageManager = getPackageManager();
        boolean hasGPS = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);

        if (hasGPS) {
            criteria.setAccuracy(Criteria.ACCURACY_FINE);
            Log.i("LOCATION", "usando GPS");
        } else {
            criteria.setAccuracy(Criteria.ACCURACY_COARSE);
            Log.i("LOCATION", "usando WI-FI o dati");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dettagli_bestiario);

        Intent it = getIntent();
        creatura = (Creatura) it.getSerializableExtra("creatura");
        creaturaEvolucao = creatura.getEvoluzione();
        caramelleNecessarie = creatura.getCaramelleNecessarie();
        caramelleOttenuti = creatura.getCaramelleOttenuti();

        //TODO: RISOLTO - cerca nella lista di creature del gestore la creatura ricevuta dallo schermo precedente.
        //creatura = GiocoSingleton.getInstance().convertCreaturaSerializableToObject(creatura);

        //Recupera le view
        ImageView foto = (ImageView) findViewById(R.id.imgDettaglioCreatura);
        TextView txtTitoloDettagli = (TextView) findViewById(R.id.txtTitoloDettagliBestiario);
        TextView txtNum = (TextView) findViewById(R.id.txtNumCreaturaDettagli);
        TextView txtNome = (TextView) findViewById(R.id.txtNomeCreaturaDettagli);
        TextView txtCatturatos = (TextView) findViewById(R.id.txtCreaturaCatturatiDettagli);
        TextView txtQuantCaramelle = (TextView) findViewById(R.id.txtQuantitaCaramelleDettagli); //riga add
        TextView txtElemento1 = (TextView) findViewById(R.id.txtElemento1CreaturaDettagli);
        TextView txtElemento2 = (TextView) findViewById(R.id.txtElemento2CreaturaDettagli);

        Button btn_evolvi = (Button) findViewById(R.id.btnEvolviDettagli);

        //Ajusta il colore del pulsante evolvi
        if(caramelleOttenuti < caramelleNecessarie|| creaturaEvolucao == null || !creatura.eDisponibile(false)){
            btn_evolvi.setBackgroundResource(R.drawable.roundshape_bottone_cinza);
        }
        else
            btn_evolvi.setBackgroundResource(R.drawable.bottone_style);

        //Tenta di inserire i valori provenienti dalla creatura tramite navigazione
        try {
            foto.setImageResource(creatura.getFoto());

            //ajusta il numero di zeri
            if(creatura.getNumero() < 10){
                txtNum.setText("#00"+creatura.getNumero());
            }else if(creatura.getNumero() < 100){
                txtNum.setText("#0"+creatura.getNumero());
            }else{
                txtNum.setText("#"+creatura.getNumero());
            }

            txtTitoloDettagli.setText("Dettagli " + creatura.getNome());
            txtNome.setText(creatura.getNome());
            txtCatturatos.setText("Catturati: " + GiocoSingleton.getInstance().getUtente().getQuantitaCatture(creatura));

            txtQuantCaramelle.setText("Caramelle ottenute: " + caramelleOttenuti);

            //Ajusta testo e colori degli elementi
            setTextViewBackground(txtElemento1,creatura.getElementi().get(0).getNome());
            if(creatura.getElementi().size() > 1)
                setTextViewBackground(txtElemento2,creatura.getElementi().get(1).getNome());
            else
                txtElemento2.setVisibility(View.INVISIBLE);

        }catch (Exception e){
            Log.e("DETTAGLI", "ERRORE: " + e.getMessage());
        }

        configuraCriterioLocation();
    }

    @SuppressLint("MissingPermission")
    @Override
    protected void onStart() {
        super.onStart();
        Log.i("PROVIDER", "start");

        provider = lm.getBestProvider(criteria, true);

        if (provider == null) {
            Log.e("PROVIDER", "Nessun provider trovato");
        } else {
            Log.i("PROVIDER", "È in uso il provider " + provider);

            lm.requestLocationUpdates(provider, TEMPO, DIST, this);
        }
    }

    public void clickIndietroDettaglio(View v){
        finish();
    }

    public void clickLuoghi(View v){
        //Toast.makeText(this, "Luoghi del " + creatura.getNome(), Toast.LENGTH_SHORT).show();

        Intent it = new Intent(this,MappaCattureActivity.class);
        it.putExtra("creatura",creatura);
        startActivity(it);
    }

    private void evolvi(){
        Apparizione ap = new Apparizione();
        ap.setLatitude(atual.getLatitude());
        ap.setLongitude(atual.getLongitude());
        ap.setCreatura(creaturaEvolucao);
        Log.i("EVOLUZIONE", "NOME DELL'EVOLUZIONE: " + ap.getCreatura().getNome());

        //Invia la cattura al server prima di chiudere la schermata.
        GiocoSingleton.getInstance().getUtente().catturare(ap);
        Log.i("EVOLUZIONE","Evoluzione catturata");

        //Sottrae le caramelle utilizzate nell'evoluzione
        GiocoSingleton.getInstance().getUtente().sommaCaramelle(creatura, -caramelleNecessarie-3);
        Log.i("EVOLUZIONE","Creatura evoluta");

        //Mostrando messaggio di successo sullo schermo
        Toast.makeText(getBaseContext(),creatura.getNome() + " è evoluta! \\o/",Toast.LENGTH_LONG).show();

        //Avviando activity della creatura evoluta
        Intent it = new Intent(this, DettagliBestiarioActivity.class);
        it.putExtra("creatura", ap.getCreatura());
        startActivity(it);

        //Inviando richiesta per aggiornare lo schermo del Bestiario al termine di questa activity
        Intent itResult = new Intent();
        setResult(ATUALIZAR_TELA,itResult);

        //Chiudendo activity
        finish();
    }

    public void clickEvolvi(View v){
        int restante = caramelleNecessarie-caramelleOttenuti;

        //Verificando se la creatura possiede un'evoluzione
        if(creaturaEvolucao == null){
            Toast.makeText(this,"Questa creatura non ha evoluzioni!",Toast.LENGTH_LONG).show();
        }

        //Verificando la quantità di caramelle
        else if(caramelleNecessarie > caramelleOttenuti){
            Toast.makeText(this,"Servono "+ restante +" caramelle per evolvere!",Toast.LENGTH_LONG).show();
        }

        //Evolvendo la creatura se esiste una creatura disponibile
        else if (creatura.eDisponibile(true)){ //Se questo accade, aggiorniamo già il flag 'bloccato' della tabella creatura_utente nel database
            evolvi();
            GiocoSingleton.getInstance().aumentaXp("evolui");   //aggiorna XP dell'utente dopo aver evoluto una Creatura
            CreaturaCatturata paraEditar = null;
            for (CreaturaCatturata capt: GiocoSingleton.getInstance().getUtente().getCreature().get(creatura) ) {
                if(capt.getBloccato() == 0) {
                    capt.setBloccato(1);
                    paraEditar = capt;
                    break;
                }
            }
        }

        //Se non c'è una creatura disponibile, lo comunichiamo via Toast
        else{
            Toast.makeText(getBaseContext(),"Non ci sono creature di nome " + creatura.getNome() + " disponibili per l'evoluzione!",Toast.LENGTH_LONG).show();
        }
    }

    private void setTextViewBackground(TextView txt, String elemento){
        txt.setText(elemento);

        if(elemento.equals("Normale"))
            txt.setBackgroundColor(Color.parseColor("#a8a878"));
        else if(elemento.equals("Fuoco"))
            txt.setBackgroundColor(Color.parseColor("#f08030"));
        else if(elemento.equals("Lotta"))
            txt.setBackgroundColor(Color.parseColor("#c03028"));
        else if(elemento.equals("Acqua"))
            txt.setBackgroundColor(Color.parseColor("#6890f0"));
        else if(elemento.equals("Volo"))
            txt.setBackgroundColor(Color.parseColor("#a890f0"));
        else if(elemento.equals("Erba"))
            txt.setBackgroundColor(Color.parseColor("#78c850"));
        else if(elemento.equals("Veleno"))
            txt.setBackgroundColor(Color.parseColor("#a040a0"));
        else if(elemento.equals("Elettro"))
            txt.setBackgroundColor(Color.parseColor("#f8d030"));
        else if(elemento.equals("Terra"))
            txt.setBackgroundColor(Color.parseColor("#e0c068"));
        else if(elemento.equals("Psico"))
            txt.setBackgroundColor(Color.parseColor("#f85888"));
        else if(elemento.equals("Roccia"))
            txt.setBackgroundColor(Color.parseColor("#b8a038"));
        else if(elemento.equals("Ghiaccio"))
            txt.setBackgroundColor(Color.parseColor("#98d8d8"));
        else if(elemento.equals("Coleottero"))
            txt.setBackgroundColor(Color.parseColor("#a8b820"));
        else if(elemento.equals("Drago"))
            txt.setBackgroundColor(Color.parseColor("#7038f8"));
        else if(elemento.equals("Spettro"))
            txt.setBackgroundColor(Color.parseColor("#705898"));
        else if(elemento.equals("Buio"))
            txt.setBackgroundColor(Color.parseColor("#705848"));
        else if(elemento.equals("Acciaio"))
            txt.setBackgroundColor(Color.parseColor("#b8b8d0"));
        else if(elemento.equals("Fata"))
            txt.setBackgroundColor(Color.parseColor("#ee99ac"));

    }

    @Override
    public void onLocationChanged(Location location) {
        if(location != null)
            atual = location;
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
        Log.d("PROVIDER", "Provider cambiato di stato");
    }

    @Override
    public void onProviderEnabled(String s) {
        Log.d("PROVIDER", "Abilitato il provider");
    }

    @Override
    public void onProviderDisabled(String s) {
        Log.d("PROVIDER", "Disabilitato il provider");
    }
}
