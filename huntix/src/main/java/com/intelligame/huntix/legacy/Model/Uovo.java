package com.intelligame.huntix.legacy.Model;

import android.content.ContentValues;
import android.database.Cursor;
import android.location.Location;
import android.util.Log;

import java.io.Serializable;

import com.intelligame.huntix.legacy.Util.DatabaseSingleton;

public class Uovo implements Serializable {
    private int idUovo;
    private int idCreatura;
    private String idElementoUovo;
    private int inCulla;
    private int schiuso;
    private int mostrato;
    private int Foto;
    private int FotoInc;
    private double Km;
    private double KmPercorso;
    Location localizzazione = null;

    public Uovo(int idUovo, int idCreatura, String idElementoUovo, int inCulla, int schiuso, int mostrato,double KmPercorso) {
        this.idUovo = idUovo;
        this.idCreatura = idCreatura;
        this.idElementoUovo = idElementoUovo;
        this.inCulla = inCulla;
        this.schiuso = schiuso;
        this.mostrato = mostrato;
        this.KmPercorso = KmPercorso;
    }

    public int getIdUovo(){ return idUovo; }
    public int getIdCreatura(){ return idCreatura; }
    public String getIdElementoUovo() { return idElementoUovo; }
    public int getInCulla(){ return inCulla;}

    public int getFoto(){
        Cursor c = DatabaseSingleton.getInstance().cerca("uovo o, elemento_uovo t", new String[]{"t.foto ft"}, "o.idElementoUovo = t.idElementoUovo AND o.idUovo = '"+idUovo+"'","");
        while (c.moveToNext()) {
            int idFoto = c.getColumnIndex("ft");
            Foto = c.getInt(idFoto);
        }
        c.close();
        return Foto;
    }

    public int getFotoInCulla(){
        Cursor c = DatabaseSingleton.getInstance().cerca("uovo o, elemento_uovo t", new String[]{"t.fotoTermoculla ftInc"}, "o.idElementoUovo = t.idElementoUovo AND o.idUovo = '"+idUovo+"'","");
        while (c.moveToNext()) {
            int idFotoInc = c.getColumnIndex("ftInc");
            FotoInc = c.getInt(idFotoInc);
        }
        c.close();
        return FotoInc;
    }

    public double getKm(){
        Cursor c = DatabaseSingleton.getInstance().cerca("uovo o, elemento_uovo t", new String[]{"t.chilometraggio km"}, "o.idElementoUovo = t.idElementoUovo AND o.idUovo = '"+idUovo+"'","");
        while (c.moveToNext()) {
            int idKm = c.getColumnIndex("km");
            Km = c.getDouble(idKm);
        }
        c.close();
        return Km;
    }

    public Location getLocalizzazione(){
        return localizzazione;
    }
    public double getKmPercorso(){
        return KmPercorso;
    }
    public void setIdUovo(int idUovo) {
        this.idUovo = idUovo;
    }

    public void setIdCreatura(int idCreatura) {
        this.idCreatura = idCreatura;
    }

    public void setIdElementoUovo(String idElementoUovo) { this.idElementoUovo = idElementoUovo; }

    public void setInCulla(int inc){
        ContentValues valores = new ContentValues();
        valores.put("inCulla", inc);
        Log.i("UOVA", "idUovo: " + idUovo);
        DatabaseSingleton.getInstance().aggiorna("uovo",valores,"idUovo = '"+idUovo+"'");
        this.inCulla = inc;
    }
    public void setLocalizzazione(Location localizzazione){
        this.localizzazione = localizzazione;
    }
    public void setKmPercorso(double KmPercorso){
        this.KmPercorso = KmPercorso;
    }

    public int getSchiuso() {
        return schiuso;
    }

    public void setSchiuso(int schiuso){
        this.schiuso = schiuso;
    }

    public int getMostrato() {
        return mostrato;
    }

    public void setMostrato(int mostrato) {
        this.mostrato = mostrato;
    }
}
