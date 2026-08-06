package com.intelligame.huntix.legacy.Model;

import android.content.ContentValues;
import android.database.Cursor;
import android.location.Location;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.intelligame.huntix.legacy.Util.DatabaseSingleton;
import com.intelligame.huntix.legacy.Util.MyApp;
import com.intelligame.huntix.legacy.Util.TimeUtil;

/**
 * Created by Lucas on 08/12/2016.
 */
public class Utente {
    private String login;
    private String password;
    private String nome;
    private String sesso;
    private String foto;
    private String dataRegistrazione;
    private Map<Creatura,List<CreaturaCatturata>> creature;
    private int livello;
    private int xp;

    public Utente(){

    }

    protected Utente(String lg) {
        this.login = lg;
        creature = new HashMap<Creatura, List<CreaturaCatturata>>();

        popolaCatture();
    }

    public String getLogin() {
        return login;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSesso() {
        return sesso;
    }

    public void setSesso(String sesso) {
        this.sesso = sesso;
    }

    public String getFoto() {
        return foto;
    }

    public void setFoto(String foto) {
        this.foto = foto;
    }

    public String getDataRegistrazione() {
        return dataRegistrazione;
    }

    public void setDataRegistrazione(String dataRegistrazione) {
        this.dataRegistrazione = dataRegistrazione;
    }

    public Map<Creatura, List<CreaturaCatturata>> getCreature() {
        return creature;
    }

    public int getLivello() {
        return livello;
    }

    public void setLivello(int livello) {
        this.livello = livello;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    private void popolaCatture(){
        //TODO: verificare se è necessario sincronizzare con il server prima di questa operazione. Sarà necessaria una nuova operazione del gestore per questo!

        try {
            Log.i("DAO_USER", "Compilando catture...");

            //Select p.idCreatura idCreatura, pu.latitude latitude, pu.longitude longitude, pu.dataCattura dataCattura from creatura p, utente u, creatura_utente pu where p.idCreatura = pu.idCreatura and u.accesso = pu.accesso and u.accesso = accesso
            Cursor cCreatura = DatabaseSingleton.getInstance().cerca("creatura p, utente u, creatura_utente pu",
                    new String[]{"p.idCreatura idCreatura", "pu.latitude latitude", "pu.longitude longitude", "pu.dataCattura dataCattura", "pu.bloccato bloccato"},
                    "p.idCreatura = pu.idCreatura and u.login = pu.login and u.login = '" + this.login + "'",
                    "p.idCreatura asc");

            //ottiene la lista di creature dal gestore generale
            List<Creatura> listCreatura = GiocoSingleton.getInstance().getCreature();

            while (cCreatura.moveToNext()) {

                int idCreatura = cCreatura.getColumnIndex("idCreatura");
                int lat = cCreatura.getColumnIndex("latitude");
                int lng = cCreatura.getColumnIndex("longitude");
                int dataCattura = cCreatura.getColumnIndex("dataCattura");
                int bloccato = cCreatura.getColumnIndex("bloccato");

                //cerca la creatura restituita dal database nella lista di creature del gestore generale
                for (Creatura creatura : listCreatura) {
                    if (creatura.getNumero() == cCreatura.getInt(idCreatura)) {

                        //crea l'oggetto CreaturaCatturata con le informazioni provenienti dal database
                        CreaturaCatturata pc = new CreaturaCatturata();
                        pc.setLatitude(cCreatura.getDouble(lat));
                        pc.setLongitude(cCreatura.getDouble(lng));
                        pc.setDataCattura(cCreatura.getString(dataCattura));
                        pc.setBloccato(cCreatura.getInt(bloccato));

                        //verifica se la lista di qualche creatura non esiste ancora
                        if(creature.get(creatura) == null) {
                            creature.put(creatura, new ArrayList<CreaturaCatturata>());
                            Log.i("DAO_USER", "Compilando cattura nuova");
                        }else{
                            Log.i("DAO_USER", "Compilando cattura conosciuta");
                        }

                        //aggiunge la creatura alla lista della sua categoria
                        creature.get(creatura).add(pc);
                    }
                }
            }
            cCreatura.close();
        }catch (Exception e){
            Log.e("DAO_USER", "ERRORE: " + e.getMessage());
        }

    }

    public void sommaCaramelle(Creatura p, int num){
        //Ottiene la riga della tabella delle Caramelle relative alla creatura p
        Cursor cCaramella = DatabaseSingleton.getInstance().cerca("creatura p, caramella d",
                new String[]{"d.idCaramella idCaramella","d.nome nome","d.quantita quantita"},
                "p.idCaramella = d.idCaramella and d.idCaramella = '" + p.getIdCaramella() + "'",null);
        cCaramella.moveToNext(); //nota: fuori dal while perché deve esserci una sola riga di risposta

        //TODO: seria bom usar um try-catch?

        //Salvando gli indici della tabella
        int idCaramella = cCaramella.getColumnIndex("idCaramella");
        int nome = cCaramella.getColumnIndex("nome");
        int quantCaramelle = cCaramella.getColumnIndex("quantita");

        //Prepara i valori per essere persistiti nel database
        ContentValues valoresCaramella = new ContentValues();
        valoresCaramella.put("idCaramella",cCaramella.getInt(idCaramella));
        valoresCaramella.put("nome",cCaramella.getString(nome));
        valoresCaramella.put("quantita",cCaramella.getInt(quantCaramelle)+num); //somma 3 alla quantità di caramelle

        //Aggiornando il database
        DatabaseSingleton.getInstance().aggiorna("caramella",valoresCaramella,"idCaramella = " + "'" +p.getIdCaramella() + "'");

        Log.i("CARAMELLE", "Quantità di caramelle della " + cCaramella.getString(nome)+ " = " + (int)(cCaramella.getInt(quantCaramelle)+num) );

    }

    public boolean catturare(Apparizione apparizione){
        try {
            Log.i("CAPTURA", "Catturando " + apparizione.getCreatura().getNome());

            //TODO: RISOLTO - cerca nella lista di creature del gestore la creatura catturata.
            //Creatura creaturaAux = GiocoSingleton.getInstance().convertCreaturaSerializableToObject(apparizione.getCreatura());
            Creatura creaturaAux = apparizione.getCreatura();

            //Ottiene il timeStamp della cattura
            Map<String, String> ts = TimeUtil.getHoraMinutoSegundoDiaMesAno();
            String dtCap = ts.get("dia") + "/" + ts.get("mes") + "/" + ts.get("ano") + " " + ts.get("hora") + ":" + ts.get("minuto") + ":" + ts.get("segundo");

            //Prepara i valori per essere persistiti nel database
            ContentValues valores = new ContentValues();
            valores.put("login", this.login);
            valores.put("idCreatura", creaturaAux.getNumero());
            valores.put("dataCattura", dtCap);
            valores.put("latitude", apparizione.getLatitude());
            valores.put("longitude", apparizione.getLongitude());
            valores.put("bloccato",0);

            //Persiste la cattura nel database
            DatabaseSingleton.getInstance().inserisci("creatura_utente", valores);

            //Aggiunge 3 caramelle alla creatura catturata
            sommaCaramelle(creaturaAux,3);

            //crea l'oggetto CreaturaCatturata con le informazioni provenienti dall'oggetto Apparizione parametro
            CreaturaCatturata pc = new CreaturaCatturata();
            pc.setLatitude(apparizione.getLatitude());
            pc.setLongitude(apparizione.getLongitude());
            pc.setDataCattura(dtCap);

            //verifica se la lista di qualche creatura non esiste ancora
            if(creature.get(creaturaAux) == null) {
                creature.put(creaturaAux, new ArrayList<CreaturaCatturata>());
                Log.d("CAPTURA", "Creatura nuova");
            }else{
                Log.d("CAPTURA", "Creatura conosciuta");
            }

            //aggiunge la creatura alla lista della sua specie
            creature.get(creaturaAux).add(pc);

            //TODO: caricare la cattura sul server web

            return true;

        }catch (Exception e){
            Log.e("CAPTURA", "ERRORE: " + e.getMessage());
            return false;
        }
    }

    public int getQuantitaCatture(Creatura creatura, boolean includiScambiati){
        if(includiScambiati) {
            if (creature.containsKey(creatura)) {
                return creature.get(creatura).size();
            }
        } else {
            if(creatura == null) return 0;
            int ans = 0;
            if (creature.containsKey(creatura)) {
                for (CreaturaCatturata capt: creature.get(creatura)) {
                    if (capt.getBloccato() == 0)
                        ans++;
                }
                return ans;
            }
        }
        return 0;
    }

    public int getQuantitaCatture(Creatura creatura){
        return getQuantitaCatture(creatura, true);
    }

    public void schiudi(Location location, int idUovo){
        try {
            Log.i("INCUBARE", "Incubando " + GiocoSingleton.getInstance().getCreaturaUovo(idUovo).getNome());


            Creatura creaturaAux = GiocoSingleton.getInstance().getCreaturaUovo(idUovo);

            //Ottiene il timeStamp della cattura
            Map<String, String> ts = TimeUtil.getHoraMinutoSegundoDiaMesAno();
            String dtCap = ts.get("dia") + "/" + ts.get("mes") + "/" + ts.get("ano") + " " + ts.get("hora") + ":" + ts.get("minuto") + ":" + ts.get("segundo");

            //Prepara i valori per essere persistiti nel database
            ContentValues valores = new ContentValues();
            valores.put("login", this.login);
            valores.put("idCreatura", creaturaAux.getNumero());
            valores.put("dataCattura", dtCap);
            valores.put("latitude", location.getLatitude());
            valores.put("longitude", location.getLongitude());
            valores.put("bloccato",0);

            //Persiste la cattura nel database
            DatabaseSingleton.getInstance().inserisci("creatura_utente", valores);

            //Aggiunge 3 caramelle alla creatura catturata
            sommaCaramelle(creaturaAux,3);

            //crea l'oggetto CreaturaCatturata con le informazioni provenienti dall'oggetto Apparizione parametro
            CreaturaCatturata pc = new CreaturaCatturata();
            pc.setLatitude(location.getLatitude());
            pc.setLongitude(location.getLongitude());
            pc.setDataCattura(dtCap);

            //verifica se la lista di qualche creatura non esiste ancora
            if (creature.get(creaturaAux) == null) {
                creature.put(creaturaAux, new ArrayList<CreaturaCatturata>());
                Log.d("CAPTURA", "Creatura nuova");
            } else {
                Log.d("CAPTURA", "Creatura conosciuta");
            }

            //aggiunge la creatura alla lista della sua specie
            creature.get(creaturaAux).add(pc);

            GiocoSingleton.getInstance().aumentaXp("choca");   //aggiorna XP dell'utente alla schiusa di un uovo

        }catch (Exception e){
            Log.e("CHOCAR", "ERRORE: " + e.getMessage());
        }

    }

}
