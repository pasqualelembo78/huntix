package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Uovo;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.View.AdapterUova;

public class UovaActivity extends Activity implements AdapterView.OnItemClickListener {
    private List<Uovo> uova;
    private MediaPlayer mediaPlayer;
    private Toast toastSchiuso;
    private List<Uovo> uovaSchiusi = new ArrayList<Uovo>();
    private Location localizzazioneAtual = null;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uova);
        try {
            TextView txtTotal = (TextView) findViewById(R.id.txtUovaTotal);


            uova = GiocoSingleton.getInstance().getUova();
            ListView listView = (ListView) findViewById(R.id.listaUova);

            Intent it = getIntent();
            localizzazioneAtual = it.getParcelableExtra("location");

            for (int i = 0; i < uova.size(); i++) {
                if (uova.get(i).getLocalizzazione() == null && uova.get(i).getInCulla() == 1) {
                    uova.get(i).setLocalizzazione(localizzazioneAtual);
                }

                if (uova.get(i).getInCulla() == 1) {

                    double distanza = localizzazioneAtual.distanceTo(uova.get(i).getLocalizzazione()) / 1000;
                    Log.i("UOVA", "Distanza: " + distanza);
                    uova.get(i).setKmPercorso(uova.get(i).getKmPercorso() + distanza);
                    uova.get(i).setLocalizzazione(localizzazioneAtual);
                    GiocoSingleton.getInstance().setKmPercorso(uova.get(i).getIdUovo(), uova.get(i).getKmPercorso());

                    //testa se l'uovo è schiuso
                    if (uova.get(i).getKmPercorso() >= uova.get(i).getKm()) {
                        uova.get(i).setSchiuso(1);
                        GiocoSingleton.getInstance().setSchiuso(uova.get(i).getIdUovo(), 1);

                        GiocoSingleton.getInstance().getUtente().schiudi(uova.get(i).getLocalizzazione(),uova.get(i).getIdUovo());
                        //l'uovo è già stato mostrato
                        GiocoSingleton.getInstance().setMostrato(uova.get(i).getIdUovo(), 1);

                        //rimuove l'uovo dalla lista di uova
                        Uovo o = uova.get(i);
                        uovaSchiusi.add(new Uovo(o.getIdUovo(), o.getIdCreatura(), o.getIdElementoUovo(),o.getInCulla(),o.getSchiuso(),o.getMostrato(),o.getKmPercorso()));
                        uova.remove(o);

                    }
                }
            }
            //totale di uova nella lista
            int total = GiocoSingleton.getInstance().getUova().size();
            txtTotal.setText("Uova: " + total + "/9");

            AdapterUova adapterUova = new AdapterUova(uova, this);
            listView.setAdapter(adapterUova);
            listView.setOnItemClickListener(this);
            mediaPlayer = MediaPlayer.create(getBaseContext(), R.raw.tema_menu);
            mediaPlayer.setLooping(true);
            mediaPlayer.start();
            mostraSchiusi();
        }catch (Exception e){
            Log.e("UOVA", "ERRORE: " + e.getMessage());
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
            Log.e("UOVA", "ERRORE: " + e.getMessage());
        }
    }


    public void clickIndietro(View v){
        for (int i = 0; i < uova.size(); i++) {
            if (uova.get(i).getLocalizzazione() == null && uova.get(i).getInCulla() == 1) {
                uova.get(i).setLocalizzazione(localizzazioneAtual);
            }
        }
        finish();
    }

    public void mostraSchiusi(){
        Uovo o;
        if(uovaSchiusi.size() == 1) {
            LayoutInflater inflater = getLayoutInflater();
            final View layout = inflater.inflate(R.layout.toast_creatura_schiuso, (ViewGroup) findViewById(R.id.toast_creatura_schiuso));
            TextView nomeCreaturaUovo = (TextView) layout.findViewById(R.id.txtCreatura);
            ImageView fotoCreaturaUovo = (ImageView) layout.findViewById(R.id.imgCreatura);
            final String nome = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(0).getIdUovo()).getNome();
            nomeCreaturaUovo.setText("Oba! " + nome + " foi schiuso!");
            int foto = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(0).getIdUovo()).getFoto();
            fotoCreaturaUovo.setImageResource(foto);
            toastSchiuso = new Toast(getApplicationContext());
            toastSchiuso.setGravity(Gravity.CENTER_VERTICAL, 0, 100);
            toastSchiuso.setDuration(Toast.LENGTH_LONG);
            toastSchiuso.setView(layout);
            toastSchiuso.show();
            o = uovaSchiusi.get(0);
            uovaSchiusi.remove(o);

        }
        if(uovaSchiusi.size() == 2) {
            LayoutInflater inflater = getLayoutInflater();
            final View layout = inflater.inflate(R.layout.toast_creatura_schiuso2, (ViewGroup) findViewById(R.id.toast_creatura_schiuso2));
            TextView nomeCreaturaUovo = (TextView) layout.findViewById(R.id.txtCreatura);
            ImageView fotoCreaturaUovo = (ImageView) layout.findViewById(R.id.imgCreatura);
            ImageView fotoCreaturaUovo2 = (ImageView) layout.findViewById(R.id.imgCreatura2);
            final String nome = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(0).getIdUovo()).getNome();
            final String nome2 = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(1).getIdUovo()).getNome();
            nomeCreaturaUovo.setText("Oba! " + nome +" e " + nome2 + " foram schiusi!");
            int foto = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(0).getIdUovo()).getFoto();
            int foto2 = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(1).getIdUovo()).getFoto();
            fotoCreaturaUovo.setImageResource(foto);
            fotoCreaturaUovo2.setImageResource(foto2);
            toastSchiuso = new Toast(getApplicationContext());
            toastSchiuso.setGravity(Gravity.CENTER_VERTICAL, 0, 100);
            toastSchiuso.setDuration(Toast.LENGTH_LONG);
            toastSchiuso.setView(layout);
            toastSchiuso.show();
            o = uovaSchiusi.get(0);
            uovaSchiusi.remove(o);
            o = uovaSchiusi.get(0);
            uovaSchiusi.remove(o);
        }
        if(uovaSchiusi.size() == 3) {
            LayoutInflater inflater = getLayoutInflater();
            final View layout = inflater.inflate(R.layout.toast_creatura_schiuso3, (ViewGroup) findViewById(R.id.toast_creatura_schiuso3));
            TextView nomeCreaturaUovo = (TextView) layout.findViewById(R.id.txtCreatura);
            ImageView fotoCreaturaUovo = (ImageView) layout.findViewById(R.id.imgCreatura);
            ImageView fotoCreaturaUovo2 = (ImageView) layout.findViewById(R.id.imgCreatura2);
            ImageView fotoCreaturaUovo3 = (ImageView) layout.findViewById(R.id.imgCreatura3);
            final String nome = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(0).getIdUovo()).getNome();
            final String nome2 = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(1).getIdUovo()).getNome();
            final String nome3 = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(2).getIdUovo()).getNome();
            nomeCreaturaUovo.setText("Oba! " + nome + ", " + nome2 + " e " + nome3);
            int foto = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(0).getIdUovo()).getFoto();
            int foto2 = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(1).getIdUovo()).getFoto();
            int foto3 = GiocoSingleton.getInstance().getCreaturaUovo(uovaSchiusi.get(2).getIdUovo()).getFoto();
            fotoCreaturaUovo.setImageResource(foto);
            fotoCreaturaUovo2.setImageResource(foto2);
            fotoCreaturaUovo3.setImageResource(foto3);
            toastSchiuso = new Toast(getApplicationContext());
            toastSchiuso.setGravity(Gravity.CENTER_VERTICAL, 0, 100);
            toastSchiuso.setDuration(Toast.LENGTH_LONG);
            toastSchiuso.setView(layout);
            toastSchiuso.show();
            o = uovaSchiusi.get(0);
            uovaSchiusi.remove(o);
            o = uovaSchiusi.get(0);
            uovaSchiusi.remove(o);
            o = uovaSchiusi.get(0);
            uovaSchiusi.remove(o);
        }

    }


    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {

    }
}
