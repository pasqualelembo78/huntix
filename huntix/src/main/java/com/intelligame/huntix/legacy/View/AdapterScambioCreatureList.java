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
public class AdapterScambioCreatureList extends BaseAdapter {

    private List<Creatura> creature;
    private Activity act;
    private boolean areAllEnabled = true;
    private int selected = -1;

    public AdapterScambioCreatureList(List<Creatura> creature, Activity act) {
        try {
            this.creature = creature;
            this.act = act;


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
            View view = act.getLayoutInflater().inflate(R.layout.lista_bestiario_personalizzata_scambi, parent, false);
            View view2 = act.getLayoutInflater().inflate(R.layout.lista_bestiario_personalizzata_null, parent, false);
            Creatura creatura = creature.get(position);

            Log.i("BESTIARIO", "Creando lista bestiario per " + creatura.getNome());

            TextView nomeCreatura = (TextView)
                    view.findViewById(R.id.txtNomeCreaturaBestiario);
            TextView numeroPossedute = (TextView)
                    view.findViewById(R.id.txtNumeroPosseduteBestiario);
            TextView numeroCreatura = (TextView)
                    view.findViewById(R.id.txtNumeroCreaturaBestiario);
            ImageView immagine = (ImageView)
                    view.findViewById(R.id.immagineCreaturaBestiario);

            int numPossedute = GiocoSingleton.getInstance().getUtente().getQuantitaCatture(creatura,false);
            //Decide se avrà le informazioni della creatura o no
            if(numPossedute > 0) {
                nomeCreatura.setText(creatura.getNome());
                numeroPossedute.setText("Qte: " + String.valueOf(numPossedute));

                //ajusta l'aspetto del numero aggiungendo zeri accanto
                if(creatura.getNumero() < 10)
                    numeroCreatura.setText("#00"+creatura.getNumero());
                else if(creatura.getNumero() < 100)
                    numeroCreatura.setText("#0"+creatura.getNumero());
                else
                    numeroCreatura.setText("#" + creatura.getNumero());

                immagine.setImageResource(creatura.getIcona());
            }else {
                return view2;
            }

            return view;
        }catch (Exception e){
            Log.e("BESTIARIO", "ERRORE: " + e.getMessage());
            return null;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return areAllEnabled;
    }

    @Override
    public boolean isEnabled(int position) {
        return (position != selected) && areAllItemsEnabled();
    }

    public void setAreAllEnabled(boolean areAllEnabled) {
        this.areAllEnabled = areAllEnabled;
    }

    public void setSelected(int selected) {
        this.selected = selected;
    }
}
