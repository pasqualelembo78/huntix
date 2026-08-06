package com.intelligame.huntix.legacy.Model;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;

import com.intelligame.huntix.legacy.Util.DatabaseSingleton;

/**
 * Created by Lucas on 02/12/2016.
 */
public class Creatura implements Serializable{
    private int numero;
    private String nome;
    private String categoria;
    private int foto;
    private int icona;
    private List<Elemento> elementi;
    private int idCaramella;
    private int idCreaturaBase;
    Creatura evolucao;

    public Creatura(){

    }

    protected Creatura(int numero, String nome, String categoria, int foto, int icona, int idCaramella, int idCreaturaBase, GiocoSingleton cg){
        this.numero = numero;
        this.nome = nome;
        this.categoria = categoria;
        this.foto = foto;
        this.icona = icona;
        this.elementi = new ArrayList<Elemento>();
        this.idCaramella = idCaramella;
        this.idCreaturaBase = idCreaturaBase;

        popolaElementi(cg);
    }

    private void popolaElementi(GiocoSingleton cg){
        //Select t.idElemento idElemento from creatura p, elemento t, creaturaelemento pt where p.idCreatura = pt.idCreatura and t.idElemento = pt.idElemento and p.idCreatura = numero
        Cursor cElemento = DatabaseSingleton.getInstance().cerca("creatura p, elemento t, creaturaelemento pt",
                new String[]{"t.idElemento idElemento"},
                "p.idCreatura = pt.idCreatura AND t.idElemento = pt.idElemento AND p.idCreatura = " + this.numero,
                "");

        while (cElemento.moveToNext()){
            int idT = cElemento.getColumnIndex("idElemento");

            //cerca l'elemento restituito dal database nella lista di elementi del gestore generale
            for(Elemento t : cg.getElementi()){
                if(t.getIdElemento() == cElemento.getInt(idT)){
                    this.elementi.add(t);
                }
            }
        }
        cElemento.close();
    }

    public List<Elemento> getElementi() {
        return elementi;
    }

    public int getNumero() {
        return numero;
    }

    public void setNumero(int numero) {
        this.numero = numero;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }

    public int getFoto() {
        return foto;
    }

    public void setFoto(int foto) {
        this.foto = foto;
    }

    public int getIcona() {
        return icona;
    }

    public void setIcona(int icona) {
        this.icona = icona;
    }

    public int getIdCaramella(){return idCaramella;}

    public int getIdCreaturaBase(){return idCreaturaBase;}

    public Creatura getEvoluzione() {
        Cursor cCreatura = DatabaseSingleton.getInstance().cerca("creatura p, creatura pe",
                new String[]{"pe.idCreatura idCreatura", "pe.nome nome", "pe.categoria categoria", "pe.foto foto", "pe.icona icona",
                        "pe.idCaramella idCaramella", "pe.idCreaturaBase idCreaturaBase"},
                "pe.idCreaturaBase = p.idCreatura AND pe.idCreaturaBase = " + this.numero, "");

        //Se la ricerca non trova nulla, restituisce già null
        if(!cCreatura.moveToNext()) {
            cCreatura.close();
            return null;
        }

        //Caso speciale: Multiovetto (possiede 3 evoluzioni dirette)
        if(this.nome.equals("Multiovetto")) {
            Random evolEeve = new Random();
            int n = evolEeve.nextInt(3);
            Log.i("EVOLUZIONE MULTIOVETTO", "n = " + n);
            while (n>0){
                cCreatura.moveToNext();
                n--;
            }
        }

        int numero = cCreatura.getColumnIndex("idCreatura");
        int nome = cCreatura.getColumnIndex("nome");
        int categoria = cCreatura.getColumnIndex("categoria");
        int foto = cCreatura.getColumnIndex("foto");
        int icona = cCreatura.getColumnIndex("icona");
        int idCaramella = cCreatura.getColumnIndex("idCaramella");
        int idCreaturaBase = cCreatura.getColumnIndex("idCreaturaBase");

        evolucao = new Creatura(cCreatura.getInt(numero),cCreatura.getString(nome),cCreatura.getString(categoria),
                cCreatura.getInt(foto),cCreatura.getInt(icona),cCreatura.getInt(idCaramella), cCreatura.getInt(idCreaturaBase),
                GiocoSingleton.getInstance());

        cCreatura.close();

        return evolucao;
    }

    public boolean eDisponibile(boolean aggiornaFlagBloqueado){
        // SELECT pu.accesso accesso, pu.idCreatura idCreatura, pu.latitude latitude,pu.longitude longitude,pu.dataCattura dataCattura
        // FROM creatura p, creatura_utente pu
        // WHERE pu.bloccato = 0 AND p.idCreatura = pu.idCreatura AND pu.idCreatura = this.numero
        Cursor c = DatabaseSingleton.getInstance().cerca("creatura p, creatura_utente pu",
                new String[]{"pu.login login", "pu.idCreatura idCreatura", "pu.latitude latitude",
                        "pu.longitude longitude","pu.dataCattura dataCattura" },
                "pu.bloccato = 0 AND p.idCreatura = pu.idCreatura AND pu.idCreatura = " + this.numero, "");

        //Se la ricerca non trova nulla, restituisce false
        if(!c.moveToNext()){
            c.close();
            return false;
        }

        //Aggiornando il flag bloccato a true
        if(aggiornaFlagBloqueado) {
            int login = c.getColumnIndex("login");
            int idCreatura = c.getColumnIndex("idCreatura");
            int latitude = c.getColumnIndex("latitude");
            int longitude = c.getColumnIndex("longitude");
            int dataCattura = c.getColumnIndex("dataCattura");

            //Prepara i valori per essere persistiti nel database
            ContentValues valores = new ContentValues();
            valores.put("login", c.getString(login));
            valores.put("idCreatura", c.getInt(idCreatura));
            valores.put("latitude", c.getDouble(latitude));
            valores.put("longitude", c.getDouble(longitude));
            valores.put("dataCattura", c.getString(dataCattura));
            valores.put("bloccato", 1);

            //Aggiorna il database
            DatabaseSingleton.getInstance().aggiorna("creatura_utente",valores,"login = '" + c.getString(login) +
                    "' AND idCreatura = '" + c.getInt(idCreatura) + "' AND dataCattura = '" + c.getString(dataCattura) + "'");
        }

        c.close();
        return true;
    }

    public int getCaramelleNecessarie() {
        switch (this.categoria) {
            case "C":
                return 25;
            case "I":
                return 50;
            case "R":
                return 75;
            default:
                return 100;
        }
    }

    public int getCaramelleOttenuti(){
        Cursor cCaramella = DatabaseSingleton.getInstance().cerca("creatura p, caramella d",
                new String[]{"d.quantita quantita"},
                "p.idCaramella = d.idCaramella and d.idCaramella = '" + this.getIdCaramella() + "'",null);
        cCaramella.moveToNext(); //nota: fuori dal while perché deve esserci una sola riga di risposta

        return cCaramella.getInt(cCaramella.getColumnIndex("quantita"));
    }

    @Override
    public boolean equals(Object obj) {
        try {
            //Verificando se il secondo partecipante è nullo
            if (obj == null)
                return false;

            //verifica se sono della stessa classe
            if (this.getClass() != obj.getClass())
                return false;

            //verifica se occupano lo stesso posto in memoria
            if (super.equals(obj))
                return true;

            Creatura creatura = (Creatura) obj;

            //Confronta i due oggetti tramite lo stato interno
            if(this.getNumero() == creatura.getNumero())
                return true;
            else
                return false;

        }catch (Exception e){
            return false;
        }
    }

    @Override
    public int hashCode() {
        //generazione propria della hashCode per evitare collisioni - oggetti di classi diverse con la stessa hashCode
        //evita anche che NON si restituisca la stessa hashCode per lo stesso oggetto
        try {
            int numPrimo = 17;
            int hash = 1;

            //TECNICA: sommare gli hashCodes di tutti gli attributi della classe e moltiplicare per un numero primo
            hash = numPrimo * hash + ((this.nome == null) ? 0 : this.nome.hashCode());
            hash = numPrimo * hash + ((this.categoria == null) ? 0 : this.categoria.hashCode());
            hash = numPrimo * hash + (this.numero);
            hash = numPrimo * hash + (this.foto);
            hash = numPrimo * hash + (this.icona);
            hash = numPrimo * hash + (this.elementi.get(0).getIdElemento());
            hash = numPrimo * hash + ((this.elementi.get(0).getNome() == null) ? 0 : this.elementi.get(0).getNome().hashCode());
            if (this.elementi.size() > 1){
                hash = numPrimo * hash + (this.elementi.get(1).getIdElemento());
                hash = numPrimo * hash + ((this.elementi.get(1).getNome() == null) ? 0 : this.elementi.get(1).getNome().hashCode());
            }

            return hash;
        }catch (Exception e){
            return super.hashCode();
        }
    }
}
