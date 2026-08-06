package com.intelligame.huntix.legacy.View;

import android.app.Activity;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Uovo;
import com.intelligame.huntix.legacy.R;

public class AdapterUova extends BaseAdapter {

    private List<Uovo> uova;
    private Activity act;
    private Uovo uovo;

    public AdapterUova(List<Uovo> uova, Activity act) {
        try {
            this.uova = uova;
            this.act = act;
        } catch (Exception e) {
            Log.e("UOVO", "ERRORE: " + e.getMessage());
        }
    }


    @Override
    public int getCount() { return uova.size(); }

    @Override
    public Uovo getItem(int position) {
        return uova.get(position);
    }

    @Override
    public long getItemId(int position) {
        return uova.get(position).getIdUovo();
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        try {
            View view = act.getLayoutInflater().inflate(R.layout.lista_uova_personalizzata, parent, false);

            //Log.i("UOVA", "Creando lista di uova per " + uovo.getIdUovo());

            final ImageView immagine = (ImageView)
                    view.findViewById(R.id.immagineUovoUova);
            //Log.i("UOVA", "Creando lista di uova per " + uovo.getIdUovo());
            final TextView kmAndou = (TextView)
                    view.findViewById(R.id.kmAndou);

            final Button incubare = (Button)
                    view.findViewById(R.id.bottoneIncubar);


            if(uova.get(position).getInCulla() == 1) {
                if(uova.get(position).getSchiuso() == 0) {
                    immagine.setImageResource(uova.get(position).getFotoInCulla());
                    kmAndou.setText(String.format("%.2f", uova.get(position).getKmPercorso()) + "/" + String.valueOf(uova.get(position).getKm()) + "km");
                    incubare.setEnabled(false);
                }

            }else {
                kmAndou.setText(String.valueOf(uova.get(position).getKm()) + "km");
                immagine.setImageResource(uova.get(position).getFoto());

                incubare.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if(GiocoSingleton.getInstance().quantitaUovaInCulla() < 3) {
                            immagine.setImageResource(uova.get(position).getFotoInCulla());
                            incubare.setEnabled(false);
                            kmAndou.setText("0" + "/" + String.valueOf(uova.get(position).getKm()) + "km");

                            //Log.i("UOVA", "Incubare uovo: " + uova.get(position).getIdUovo());
                            uova.get(position).setInCulla(1);
                            GiocoSingleton.getInstance().setInCulla(uova.get(position).getIdUovo(), 1);
                        }
                        else{
                            Toast.makeText(act, "Le tue termoculle sono occupate. ", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }

            return view;
        }catch (Exception e){
            Log.e("UOVO", "ERRORE: " + e.getMessage());
            return null;
        }
    }

}
