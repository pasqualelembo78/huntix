package com.intelligame.huntix.legacy.Model;

import android.graphics.Bitmap;

import java.io.Serializable;
import java.util.Date;

import com.intelligame.huntix.legacy.R;

/**
 * Created by Lucas on 02/12/2016.
 */
public class Poi implements Serializable{
    private String id;
    private String nome;
    private transient Bitmap foto = null;
    private Double lat;
    private Double lng;
    private String descrizione;
    private Date accesso;
    private boolean disponibile;

    public Poi(){

    }

    public Poi(String ID, String Name) {
        this.id = ID;
        this.nome = Name;
        //this.foto = Photo;
        this.accesso = null;
        this.disponibile = true;
    }

    public String getID() {
        return id;
    }

    public void setID(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public Bitmap getFoto() {
        return foto;
    }

    public void setFoto(Bitmap foto) {
        this.foto = foto;
    }

    public Date getUltimoAccesso() {
        return accesso;
    }

    public void setUltimoAccesso(Date tempo) {
        this.accesso = tempo;
    }

    public boolean getDisponibile() {
        return disponibile;
    }

    public void setDisponibile(boolean disp) {
        this.disponibile = disp;
    }

    //Restituisce l'id della risorsa del drawable del poi in base a stato/distanza
    public int getIcon(boolean interactionPossible){
        if (interactionPossible && this.disponibile) {
            return R.drawable.poi_vicino;
        } else if (!interactionPossible && this.disponibile){
            return R.drawable.poi_lontano;
        } else if (interactionPossible && !this.disponibile){
            return R.drawable.poi_vicino_non_disponibile;
        } else {
            return R.drawable.poi_lontano_non_disponibile;
        }
    }
}
