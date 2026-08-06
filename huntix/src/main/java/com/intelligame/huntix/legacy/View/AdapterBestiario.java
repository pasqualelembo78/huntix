package com.intelligame.huntix.legacy.View;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Creatura;
import com.intelligame.huntix.legacy.R;

/**
 * Created by Lucas on 20/12/2016.
 */
public class AdapterBestiario extends BaseAdapter {

    private List<Creatura> creature;
    private Activity act;
    //List<Bitmap> bitmapCache;

    public AdapterBestiario(List<Creatura> creature, Activity act) {
        try {
            this.creature = creature;
            this.act = act;
            //this.bitmapCache = new ArrayList<Bitmap>();

            //carregarBitmapsNoCache();
        }catch (Exception e){
            Log.e("BESTIARIO", "ERRORE: " + e.getMessage());
        }
    }

    @Override
    public int getCount() {
        return creature.size();
    }

    @Override
    public Object getItem(int position) {
        return creature.get(position);
    }

    @Override
    public long getItemId(int position) {
        return creature.get(position).getNumero();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        try {
            View view = act.getLayoutInflater().inflate(R.layout.lista_bestiario_personalizzata, parent, false);

            Creatura creatura = creature.get(position);

            Log.i("BESTIARIO", "Creando lista bestiario per " + creatura.getNome());

            TextView nomeCreatura = (TextView)
                    view.findViewById(R.id.txtNomeCreaturaBestiario);
            TextView numeroCreatura = (TextView)
                    view.findViewById(R.id.txtNumeroCreaturaBestiario);
            ImageView immagine = (ImageView)
                    view.findViewById(R.id.immagineCreaturaBestiario);

            //Decide se avrà le informazioni della creatura o no
            if(GiocoSingleton.getInstance().getUtente().getQuantitaCatture(creatura) > 0) {
                nomeCreatura.setText(creatura.getNome());

                //ajusta l'aspetto del numero aggiungendo zeri accanto
                if(creatura.getNumero() < 10)
                    numeroCreatura.setText("#00"+creatura.getNumero());
                else if(creatura.getNumero() < 100)
                    numeroCreatura.setText("#0"+creatura.getNumero());
                else
                    numeroCreatura.setText("#" + creatura.getNumero());

                immagine.setImageResource(creatura.getIcona());
            }else {
                nomeCreatura.setText("???");

                //ajusta l'aspetto del numero aggiungendo zeri accanto
                if(creatura.getNumero() < 10)
                    numeroCreatura.setText("#00"+creatura.getNumero());
                else if(creatura.getNumero() < 100)
                    numeroCreatura.setText("#0"+creatura.getNumero());
                else
                    numeroCreatura.setText("#"+creatura.getNumero());

                immagine.setImageResource(R.drawable.help);
            }

            return view;
        }catch (Exception e){
            Log.e("BESTIARIO", "ERRORE: " + e.getMessage());
            return null;
        }
    }

}
