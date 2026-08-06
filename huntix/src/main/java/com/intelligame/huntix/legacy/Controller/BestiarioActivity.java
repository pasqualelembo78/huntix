package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Creatura;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.View.AdapterBestiario;

public class BestiarioActivity extends Activity implements AdapterView.OnItemClickListener{

    private List<Creatura> creature;
    private MediaPlayer mediaPlayer;

    //Costanti ausiliarie per il metodo startActivityForResult()
    private final int COD_REQUISICAO = 7;
    private final int ATUALIZAR_TELA = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bestiario);

        try {
            //Configura il totale delle creature catturate
            TextView txtTotal = (TextView) findViewById(R.id.txtBestiarioTotal);
            int total = GiocoSingleton.getInstance().getUtente().getCreature().size();
            txtTotal.setText("Catturati: " + total + "   Mancano: " + (151-total));

            //Prepara la listview personalizzata del bestiario
            creature = GiocoSingleton.getInstance().getCreature();
            ListView listView = (ListView) findViewById(R.id.listaBestiario);

            AdapterBestiario adapterBestiario = new AdapterBestiario(creature, this);
            listView.setAdapter(adapterBestiario);
            listView.setOnItemClickListener(this);
            //Avvia la musica tema del menu
            mediaPlayer = MediaPlayer.create(getBaseContext(), R.raw.tema_menu);
            mediaPlayer.setLooping(true);
            mediaPlayer.start();

        }catch (Exception e){
            Log.e("BESTIARIO", "ERRORE: " + e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        try {
            //ferma la musica tema del menu e restituisce la risorsa al sistema
            mediaPlayer.pause();
            mediaPlayer.release();
        }catch (Exception e){
            Log.e("BESTIARIO", "ERRORE: " + e.getMessage());
        }
    }

    public void clickIndietro(View v){
        finish();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        try {
            //Click su un elemento della listView personalizzata
            Creatura creatura = (Creatura) parent.getAdapter().getItem(position);

            //verifica se la creatura selezionata è già stata catturata almeno una volta
            if (GiocoSingleton.getInstance().getUtente().getQuantitaCatture(creatura) > 0) {

                //Toast.makeText(this, "Dettagli del " + creatura.getNome(), Toast.LENGTH_SHORT).show();

                Intent it = new Intent(this,DettagliBestiarioActivity.class);
                it.putExtra("creatura",creatura);

                startActivityForResult(it,COD_REQUISICAO);
            }

        }catch (Exception e){
            Log.e("BESTIARIO", "ERRORE al click: " + e.getMessage());
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent it) {
        if(it == null){
            Log.i("ACTIVITY_RESULT", "Non è stato inviato nessun valore");
            return;
        }
        else if(requestCode == COD_REQUISICAO){
                if(resultCode == ATUALIZAR_TELA){
                    Log.i("ACTIVITY_RESULT", "Ricevuto codice per aggiornare lo schermo");
                    //Avvia un nuovo bestiario
                    Intent itBestiario = new Intent(this, BestiarioActivity.class);
                    startActivity(itBestiario);
                    //Termina il bestiario precedente
                    finish();
                }
                else{
                    Log.i("ACTIVITY_RESULT", "Codice non valido");
                }
        }
    }
}
