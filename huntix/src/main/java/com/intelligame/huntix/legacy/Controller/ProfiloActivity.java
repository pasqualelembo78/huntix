package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.List;
import java.util.Map;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Creatura;
import com.intelligame.huntix.legacy.Model.CreaturaCatturata;
import com.intelligame.huntix.legacy.Model.Utente;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.Util.DatabaseSingleton;


public class ProfiloActivity extends Activity {

    private ProgressBar progressBar;
    private int progressStatus = 0;
    private int xpMaxBar = 0;
    private String xpNumber = "";

    public final static int PERFIL_SCAMBIO = 1;
    public static final int REQUEST_ENABLE_BT = 402;

    private Button scambio;

    //Verifica se l'utente ha abilitato il Bluetooth
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode == REQUEST_ENABLE_BT) {
                if(resultCode  == RESULT_OK) {
                    Intent it = new Intent(this, ScambioListaUtentiActivity.class);
                    startActivityForResult(it,PERFIL_SCAMBIO);

                } else if (resultCode == RESULT_CANCELED){

                    Context context = getApplicationContext();
                    CharSequence text = "Il tuo Bluetooth è spento. Attivalo per effettuare lo scambio di creature.";
                    int duration = Toast.LENGTH_SHORT;

                    Toast toast = Toast.makeText(context, text, duration);
                    toast.show();
                }
            }
        } catch (Exception e) {
            Log.e("PERFIL", "ERRORE: " + e.getMessage());
        }

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profilo);

        progressBar = (ProgressBar) findViewById(R.id.progressBar);     //Riferimento della barra xp
        TextView txtXp = (TextView) findViewById(R.id.txtXp);           //Riferimento della textView xp
        TextView txtLivello = (TextView) findViewById(R.id.txtLivello);     //Riferimento della textView del livello

        Log.d("utente", "XP: " + GiocoSingleton.getInstance().getUtente().getXp());
        Log.d("utente", "Livello: " + GiocoSingleton.getInstance().getUtente().getLivello());

        Cursor utente = DatabaseSingleton.getInstance().cerca("utente", new String[]{"livello", "xp"},
                "login= '" + GiocoSingleton.getInstance().getUtente().getLogin()+"'", "");

        while(utente.moveToNext()) {
            int idxp = utente.getColumnIndex("xp");
            int idlivello = utente.getColumnIndex("livello");

            progressStatus = utente.getInt(idxp);                         //Memorizza l'xp attuale dell'utente dopo la cattura
            xpMaxBar = GiocoSingleton.getInstance().xpMassimo(GiocoSingleton.getInstance().getUtente().getLivello());      //Calcola il valore massimo della barra (importante perché finisca quando l'utente sale di livello)
            progressBar.setMax(xpMaxBar);                               //Impostando il valore massimo della progressBar
            progressBar.setProgress(progressStatus);                    //Impostando il progresso della progressBar in base all'xp attuale + xp di cattura
            xpNumber = Integer.toString(progressStatus) + "/" + Integer.toString(xpMaxBar);   //Creando la stringa della textView xp

            txtXp.setText(xpNumber);                                                                                //Impostando la textView xp su xpAttuale/xpMassimo
            txtLivello.setText("Livello " + GiocoSingleton.getInstance().getUtente().getLivello());        //Impostando la textView del livello sul livello attuale dell'utente

            Log.d("utente","XP db: " + utente.getInt(idxp));
            Log.d("utente","Livello db: " + utente.getInt(idlivello));
        }

        //Ottiene i riferimenti delle view
        ImageView imageView = (ImageView) findViewById(R.id.imgAllenatoreProfilo);
        TextView txtInicioAventura = (TextView) findViewById(R.id.txtInicioAventuraProfilo);
        TextView txtNumCatture = (TextView) findViewById(R.id.txtNumCattureProfilo);
        TextView txtNomeAllenatore = (TextView) findViewById(R.id.txtNomeAllenatoreProfilo);
        scambio = (Button) findViewById(R.id.buttonScambio);

        try {
            //Definisce il nome dell'allenatore
            txtNomeAllenatore.setText(GiocoSingleton.getInstance().getUtente().getLogin());

            //Imposta l'immagine del profilo in base al sesso dell'utente
            if(GiocoSingleton.getInstance().getUtente().getSesso().equals("M"))
                imageView.setImageResource(R.drawable.male_grande);
            else
                imageView.setImageResource(R.drawable.female_grande);

            //Imposta l'inizio dell'avventura
            txtInicioAventura.setText(GiocoSingleton.getInstance().getUtente().getDataRegistrazione());

            //Imposta il numero di creature catturate dall'utente
            int contCattura = 0;
            for (Map.Entry<Creatura,List<CreaturaCatturata>> entry : GiocoSingleton.getInstance().getUtente().getCreature().entrySet()){
                contCattura += entry.getValue().size();
            }
            txtNumCatture.setText(contCattura+"");

        }catch (Exception e){
            Log.e("PERFIL", "ERRORE: " + e.getMessage());
        }
    }

    public void clickScambio(View v) {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            if (bluetoothAdapter == null) {
                Context context = getApplicationContext();
                CharSequence text = "Il tuo dispositivo non supporta il Bluetooth: lo scambio di creature è disabilitato per te.";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();

                scambio.setEnabled(false);

                return;
            }
            else if (!bluetoothAdapter.isEnabled()) {

                if(!scambio.isEnabled())
                    scambio.setEnabled(true);

                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

                onActivityResult(REQUEST_ENABLE_BT, 8989, enableBtIntent);
            }
            else  {
                if(!scambio.isEnabled())
                    scambio.setEnabled(true);

                Intent it = new Intent(this, ScambioListaUtentiActivity.class);
                startActivityForResult(it,PERFIL_SCAMBIO);
            }

        } catch (Exception e){
            Log.e("SCAMBIO", "ERRORE: " + e.getMessage());
        }
    }

    public void clickLogout(View v){
        try {
            Log.i("LOGOUT", "Uscita...");

            AlertDialog.Builder alerta = new AlertDialog.Builder(this);
            alerta.setTitle("ESCI");
            alerta.setMessage("Vuoi chiudere questa sessione?");

            //Configura l'azione per la conferma positiva
            alerta.setPositiveButton("Sim", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    GiocoSingleton.getInstance().esci();
                    setResult(MapActivity.MENU_PERFIL);

                    finish();
                }
            });

            //Configura l'azione per la negazione dell'azione
            alerta.setNegativeButton("No", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            });

            //Mostra la finestra di conferma
            alerta.show();

        }catch (Exception e){
            Log.e("LOGOUT", "ERRORE: " + e.getMessage());
        }

    }

    public void clickIndietro(View v){
        finish();
    }
}
