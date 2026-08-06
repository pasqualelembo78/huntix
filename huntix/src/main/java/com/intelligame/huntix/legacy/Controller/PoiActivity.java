package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Date;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.InterazionePoi;
import com.intelligame.huntix.legacy.Model.Poi;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.Util.DatabaseSingleton;
import com.intelligame.huntix.legacy.Util.MyApp;

public class PoiActivity extends Activity {
    private TextView placeName;
    private TextView placeInfo;
    private ImageView imgPoiIcon;
    private Date tempoPoi;
    private Poi poi;
    private boolean Pegou = false;
    public String Portuga;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_poi);

        placeName = (TextView) findViewById(R.id.placeName);
        placeInfo = (TextView) findViewById(R.id.placeInfo);
        imgPoiIcon = (ImageView) findViewById(R.id.imgPoiIcon);
        //ContentValues valori = new ContentValues();
        //DatabaseSingleton.getInstance().inserisci("Poi",valori);
        Intent it = getIntent();
        Poi poi = (Poi) it.getSerializableExtra("poi");
        byte[] byteArray = it.getByteArrayExtra("foto");
        this.poi = poi;
        Cursor cTradutor = DatabaseSingleton.getInstance().cerca("traduzione trad",
                new String[]{"trad.italiano italiano"},
                "trad.chiave = '" + poi.getDescrizione() + "'",
                "");
        if (cTradutor.getCount()>0) {
            while (cTradutor.moveToNext()) {
                int coluna = cTradutor.getColumnIndex("italiano");
                Portuga = cTradutor.getString(coluna);
            }
        } else {
            Portuga = " ";
        }
        placeName.setText(poi.getNome());
        placeInfo.setText(Portuga);
        if(byteArray != null)
            imgPoiIcon.setImageBitmap(BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length));
        tempoPoi = poi.getUltimoAccesso();
        cTradutor.close();
    }

    public void clickReturnBtn(View btnReturn){
        Intent it = new Intent(this, MapActivity.class);
        it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        it.putExtra("tempo", tempoPoi);
        it.putExtra("poi", poi);
        startActivity(it);
        finish();
    }

    @Override
    public void onBackPressed() {
        Intent it = new Intent(this, MapActivity.class);
        it.putExtra("tempo", tempoPoi);
        it.putExtra("poi", poi);
        it.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(it);
        finish();
    }

    public void prendiUovo(View view) {
        Pegou = false;
        Date TempoAtual = Calendar.getInstance().getTime();

        InterazionePoi inter = GiocoSingleton.getInstance().getUltimaInterazione(poi);

        if(inter.getUltimoAccesso() == null){
            GiocoSingleton.getInstance().interagisciPoi(poi, TempoAtual);
            Pegou = true;
        }
        else{
            double diff = TempoAtual.getTime() - inter.getUltimoAccesso().getTime();
            int diffSec = (int)diff/1000;
            if(diffSec > 300){

                GiocoSingleton.getInstance().interagisciPoi(poi, TempoAtual);

                //Prende l'uovo
                Pegou = true;
            }
            else{
                Toast toastEspere = Toast.makeText(MyApp.getAppContext(),"Espere mais "+ String.valueOf(300-diffSec) +" segundos",Toast.LENGTH_SHORT);
                toastEspere.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL,0,0);
                toastEspere.show();
            }

        }

        if(Pegou) {
            Integer xp = GiocoSingleton.getInstance().getXpEvento("poi");
            Integer uova = GiocoSingleton.getInstance().getUova().size();
            if(uova > 8){
                Toast toastInventario = Toast.makeText(MyApp.getAppContext(), "Inventário de uova está cheio!", Toast.LENGTH_LONG);
                toastInventario.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL,0,0);
                toastInventario.show();
            }
            else{
                Toast toastUovo = Toast.makeText(MyApp.getAppContext(), "Pegou Uovo ", Toast.LENGTH_LONG);
                toastUovo.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL,0,0);
                toastUovo.show();
            }

        }
    }

}

