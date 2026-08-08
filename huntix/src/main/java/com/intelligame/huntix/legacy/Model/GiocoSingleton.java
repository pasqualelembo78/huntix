package com.intelligame.huntix.legacy.Model;

import android.content.ContentValues;
import android.database.Cursor;
import android.util.Log;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.intelligame.huntix.legacy.Util.DatabaseSingleton;
import com.intelligame.huntix.legacy.Util.MyApp;
import com.intelligame.huntix.legacy.Util.RandomUtil;
import com.intelligame.huntix.legacy.Util.TimeUtil;

/**
 * Created by Lucas on 08/12/2016.
 */
public final class GiocoSingleton {
    private Utente utente;
    private Map<String,List<Creatura>> creature;
    private Apparizione[] apparizioni = new Apparizione[10];
    private List<Elemento> elementiCreatura;
    private static volatile GiocoSingleton INSTANCE;
    private boolean sorteggiatoLeggendario = false;
    private List<Uovo> uova = new ArrayList<>();

    private static final int POI_PER_CHIAMATA = 8;
    private static final String[] POI_NOMI = {
            "Praça Central", "Igreja Matriz", "Museu da Cidade", "Parque Municipal",
            "Biblioteca Pública", "Estação Central", "Teatro Municipal", "Mercado Municipal",
            "Ponte Histórica", "Jardim Botânico", "Café Central", "Torre Panorâmica"
    };


    private List<Caramella> caramelle;

    private void caricaCaramelle(){
        this.caramelle = new ArrayList<Caramella>();

        Cursor c = DatabaseSingleton.getInstance().cerca("caramella",new String[]{"idCaramella","nome","quantita"},"","");

        while(c.moveToNext()){
            int idD = c.getColumnIndex("idCaramella");
            int nome = c.getColumnIndex("nome");
            int quant = c.getColumnIndex("quantita");

            Caramella d = new Caramella();
            d.setIdCaramella(c.getInt(idD));
            d.setNomeCreatura(c.getString(nome));
            d.setQuantita(c.getInt(quant));

            this.caramelle.add(d);
        }

        c.close();

        //TODO: rimuovere i test di stampa caramella

        //STAMPA DI TEST
        for (Caramella d : caramelle){
            Log.d("CARAMELLE",d.getNomeCreatura() + ": " + d.getQuantita());
        }
    }

    private GiocoSingleton() {
        caricaCaramelle();
        caricaElementi();
        caricaCreature(this);
        caricaUova();
    }

    private void caricaUova(){
        this.uova.clear();

        Cursor c = DatabaseSingleton.getInstance().cerca("uovo",new String[]{"idUovo","idCreatura","idElementoUovo","inCulla","schiuso","mostrato","KmPercorso"},"mostrato = 0","");

        while(c.moveToNext()){
            int idO = c.getColumnIndex("idUovo");
            int idP = c.getColumnIndex("idCreatura");
            int idTO = c.getColumnIndex("idElementoUovo");
            int idInc = c.getColumnIndex("inCulla");
            int idCho = c.getColumnIndex ("schiuso");
            int idExi = c.getColumnIndex ("mostrato");
            int idKmAnd = c.getColumnIndex ("KmPercorso");

            uova.add(new Uovo(c.getInt(idO), c.getInt(idP), c.getString(idTO),c.getInt(idInc),c.getInt(idCho),c.getInt(idExi),c.getDouble(idKmAnd)));
        }

        c.close();

    }

    private void caricaElementi(){
        this.elementiCreatura = new ArrayList<>();

        Cursor c = DatabaseSingleton.getInstance().cerca("elemento",new String[]{"idElemento","nome"},"","");

        while(c.moveToNext()){
            int idT = c.getColumnIndex("idElemento");
            int name = c.getColumnIndex("nome");

            Elemento t = new Elemento();
            t.setIdElemento(c.getInt(idT));
            t.setNome(c.getString(name));

            this.elementiCreatura.add(t);
        }

        c.close();

        //TODO: rimuovere i test di stampa elemento

        //STAMPA DI TEST
        for (Elemento tp : elementiCreatura){
            Log.d("ELEMENTI",tp.getNome());
        }
    }

    private void caricaCreature(GiocoSingleton gestore){
        creature = new HashMap<String,List<Creatura>>();

        Cursor c = DatabaseSingleton.getInstance().cerca("creatura",new String[]{"idCreatura","nome","categoria","foto","icona","idCaramella","idCreaturaBase"},"","");

        while(c.moveToNext()){
            int idP = c.getColumnIndex("idCreatura");
            int name = c.getColumnIndex("nome");
            int cat = c.getColumnIndex("categoria");
            int foto = c.getColumnIndex("foto");
            int icona = c.getColumnIndex("icona");
            int idCaramella = c.getColumnIndex("idCaramella");
            int idCreaturaBase = c.getColumnIndex("idCreaturaBase");

            Creatura p = new Creatura(c.getInt(idP),c.getString(name),c.getString(cat),c.getInt(foto),c.getInt(icona),c.getInt(idCaramella), c.getInt(idCreaturaBase),gestore);

            //verifica se la lista di qualche categoria non esiste ancora
            if(creature.get(p.getCategoria()) == null)
                creature.put(p.getCategoria(),new ArrayList<Creatura>());

            //aggiunge la creatura alla lista della sua categoria
            creature.get(p.getCategoria()).add(p);
        }

        c.close();

        //TODO: rimuovere i test di stampa creatura

        //STAMPA CREATURE COMUNI
        for(Creatura creatura : creature.get("C")){
            String elementi = "";
            for (Elemento tp :  creatura.getElementi()){
                elementi += tp.getNome();
                elementi += "/";
            }
            Log.d("CREATURAS", creatura.getNumero() + " - " + creatura.getNome() + " - " + creatura.getCategoria() + " - " + creatura.getFoto() + " - " + creatura.getIcona() + " - " + elementi);
        }

        //STAMPA CREATURE INCOMUNI
        for(Creatura creatura : creature.get("I")){
            String elementi = "";
            for (Elemento tp :  creatura.getElementi()){
                elementi += tp.getNome();
                elementi += "/";
            }
            Log.d("CREATURAS", creatura.getNumero() + " - " + creatura.getNome() + " - " + creatura.getCategoria() + " - " + creatura.getFoto() + " - " + creatura.getIcona() + " - " + elementi);
        }

        //STAMPA CREATURE RARE
        for(Creatura creatura : creature.get("R")){
            String elementi = "";
            for (Elemento tp :  creatura.getElementi()){
                elementi += tp.getNome();
                elementi += "/";
            }
            Log.d("CREATURAS", creatura.getNumero() + " - " + creatura.getNome() + " - " + creatura.getCategoria() + " - " + creatura.getFoto() + " - " + creatura.getIcona() + " - " + elementi);
        }

        //STAMPA CREATURE LEGGENDARIE
        for(Creatura creatura : creature.get("L")){
            String elementi = "";
            for (Elemento tp :  creatura.getElementi()){
                elementi += tp.getNome();
                elementi += "/";
            }
            Log.d("CREATURAS", creatura.getNumero() + " - " + creatura.getNome() + " - " + creatura.getCategoria() + " - " + creatura.getFoto() + " - " + creatura.getIcona() + " - " + elementi);
        }
    }

    private void caricaUtente(){

        Cursor c = DatabaseSingleton.getInstance().cerca("utente",new String[]{"login","password","nome","sesso","foto","dataRegistrazione","xp","livello"},"","");

        while(c.moveToNext()){
            int login = c.getColumnIndex("login");
            int pass = c.getColumnIndex("password");
            int name = c.getColumnIndex("nome");
            int sesso = c.getColumnIndex("sesso");
            int foto = c.getColumnIndex("foto");
            int dtCad = c.getColumnIndex("dataRegistrazione");
            int xp = c.getColumnIndex("xp");
            int livello = c.getColumnIndex("livello");

            utente = new Utente(c.getString(login));

            utente.setPassword(c.getString(pass));
            utente.setNome(c.getString(name));
            utente.setSesso(c.getString(sesso));
            utente.setFoto(c.getString(foto)); //IMPLEMENTARE LA RIMOZIONE DELLA FOTO DELL'UTENTE NELLA REGISTRAZIONE
            utente.setDataRegistrazione(c.getString(dtCad));
            utente.setXp(c.getInt(xp));
            utente.setLivello(c.getInt(livello));
        }

        c.close();

    }

    public static synchronized GiocoSingleton getInstance(){
        if (INSTANCE == null) {
            INSTANCE = new GiocoSingleton();
        }
        return INSTANCE;
    }

    public Utente getUtente(){
        return this.utente;
    }

    public InterazionePoi interagisciPoi(Poi p, Date accesso){
        ContentValues valores = new ContentValues();

        valores.put("ultimoAccesso",accesso.getTime());

        Cursor cPoi = DatabaseSingleton.getInstance().cerca("interazionepoi ip",
                new String[]{"ip.idPoi idPoi"},
                "ip.idPoi = '" + p.getID() + "' and ip.loginUtente = '"+utente.getLogin()+"'",
                "");

        if(cPoi.getCount() > 0){
            DatabaseSingleton.getInstance().aggiorna("interazionepoi",
                    valores,
                    "idPoi = '"+p.getID()+"' and "+
                            "loginUtente='"+utente.getLogin()+"'"
            );
        }
        else{
            valores.put("idPoi",p.getID());
            valores.put("loginUtente",utente.getLogin());
            DatabaseSingleton.getInstance().inserisci("interazionepoi",valores);
        }


        InterazionePoi interazionePoi = new InterazionePoi(p, utente, accesso);
        p.setDisponibile(false);
        aumentaXp("poi");
        sorteggiaUovo();
        return interazionePoi;
    }

    public InterazionePoi getUltimaInterazione(Poi p){
        Cursor cPoi = DatabaseSingleton.getInstance().cerca("interazionepoi",
                new String[]{"ultimoAccesso"},
                "idPoi = '" + p.getID() + "' and loginUtente = '"+utente.getLogin()+"'",
                "");

        Date access = null;
        while(cPoi.getCount() > 0 && cPoi.moveToNext()){
            int cAccesso = cPoi.getColumnIndex("ultimoAccesso");

            access = new Date( cPoi.getLong(cAccesso)) ;
        }

        if(access == null)
            p.setDisponibile(true);
        else{
            Date TempoAtual = Calendar.getInstance().getTime();
            double diff = TempoAtual.getTime() - access.getTime();
            int diffSec = (int)diff/1000;
            if(diffSec > 300)
                p.setDisponibile(true);
            else
                p.setDisponibile(false);
        }

        InterazionePoi interac = new InterazionePoi(p, utente, access);
        return interac;
    }

    public List<Creatura> getCreature(){
        //estrae dalla MAP tutti i valori creatura e li unisce in una lista ordinata da restituire.
        List<Creatura> creatura = new ArrayList<>();

        for (Map.Entry<String, List<Creatura>> entry : creature.entrySet()){
            //aggiunge le liste di creatura alla fine della lista da restituire
            creatura.addAll(entry.getValue());
        }

        //ordina la lista da restituire in base al numero della creatura
        Collections.sort(creatura, new Comparator<Creatura>() {
            @Override
            public int compare(Creatura pk2, Creatura pk1) {
                if(pk1.getNumero() > pk2.getNumero())
                    return -1;
                else if(pk1.getNumero() < pk2.getNumero())
                    return +1;
                return 0;
            }
        });

        //TODO: rimuovere i test di stampa lista creatura
        for(Creatura p : creatura){
            Log.d("LISTA_CREATURE", "Creatura: " + p.getNome());
        }

        return creatura;
    }

    public List<Poi> getPoi(double latitude, double longitude){
        List<Poi> list = new ArrayList<Poi>();

        // Geração OFFLINE di poi: deterministiche rispetto alla cella della
        // griglia in cui si trova il giocatore, così restano stabili tra i refresh.
        // (La versione originale usava Google Places Nearby Search con API key.)
        long cellLat = Math.round(latitude * 1000);
        long cellLng = Math.round(longitude * 1000);
        java.util.Random rnd = new java.util.Random(cellLat * 31 + cellLng);
        int offsetBase = rnd.nextInt(1000);
        for (int i = 0; i < POI_PER_CHIAMATA; i++) {
            double lat = latitude + (rnd.nextDouble() - 0.5) * 0.006;
            double lng = longitude + (rnd.nextDouble() - 0.5) * 0.006;

            String id = "pst_" + (offsetBase + i);
            String nome = POI_NOMI[rnd.nextInt(POI_NOMI.length)];

            Poi poi = new Poi(id, nome);
            poi.setLat(lat);
            poi.setLng(lng);
            poi.setDescrizione("local");

            Cursor cPoi = DatabaseSingleton.getInstance().cerca("poi p",
                    new String[]{"p.disponibile disponibile"},
                    "p.idPoi = '" + poi.getID() + "'",
                    "");
            if (cPoi.getCount() > 0) {
                while (cPoi.moveToNext()) {
                    int coluna = cPoi.getColumnIndex("disponibile");
                    if (cPoi.getInt(coluna) == 0) {
                        poi.setDisponibile(false);
                    } else
                        poi.setDisponibile(true);
                }
            }
            else{
                ContentValues valores = new ContentValues();
                valores.put("idPoi",poi.getID());
                valores.put("latitude",poi.getLat());
                valores.put("longitude",poi.getLng());
                valores.put("disponibile",true);

                long idBd = DatabaseSingleton.getInstance().inserisci("Poi",valores);
                Log.d("POI_ACTIVITY","REGISTRATO NEL DB O AGGIORNATO CON ID = "+idBd);
            }

            InterazionePoi it = getUltimaInterazione(poi);
            //aggiorna se è possibile interagire in questione di tempo
            if (it.getUltimoAccesso() != null) {
                Date TempoAtual = Calendar.getInstance().getTime();
                double diff = TempoAtual.getTime() - it.getUltimoAccesso().getTime();
                int diffSec = (int) diff / (1000);
                if (diffSec> 300) {
                    poi.setDisponibile(true);
                }
                else
                    poi.setDisponibile(false);
            }

            list.add(poi);
        }
        return list;
    }

    public Apparizione[] getApparizioni(){
        return this.apparizioni;
    }

    protected List<Elemento> getElementi(){
        return elementiCreatura;
    }

    public List<Caramella> getCaramelle() {
        return caramelle;
    }

    public List<Uovo> getUova(){ return uova; }

    public void removeUovo(int i){
        Uovo o = uova.get(i);
        uova.remove(o);
    }

    public Creatura getCreaturaUovo(int idUovo){
        Creatura p = null;
        Cursor c = DatabaseSingleton.getInstance().cerca("creatura p, uovo o",new String[]{"p.idCreatura idCreatura","p.nome nome","p.categoria categoria","p.foto foto","p.icona icona","p.idCaramella idCaramella", "p.idCreaturaBase idCreaturaBase"},"o.idCreatura = p.idCreatura AND o.idUovo = '"+idUovo+"'","");
        while (c.moveToNext()) {
            int idP = c.getColumnIndex("idCreatura");
            int name = c.getColumnIndex("nome");
            int cat = c.getColumnIndex("categoria");
            int foto = c.getColumnIndex("foto");
            int icona = c.getColumnIndex("icona");
            int idCaramella = c.getColumnIndex("idCaramella");
            int idCreaturaBase = c.getColumnIndex("idCreaturaBase");

            p = new Creatura(c.getInt(idP),c.getString(name),c.getString(cat),c.getInt(foto),c.getInt(icona),c.getInt(idCaramella),c.getInt(idCreaturaBase),this);
            Log.i("GET", "Nome: " + p.getNome());

        }
        c.close();
        return p;
    }

    public void setInCulla(int idUovo,int inCulla){
        ContentValues valores = new ContentValues();
        valores.put("inCulla",inCulla);
        DatabaseSingleton.getInstance().aggiorna("uovo",valores,"idUovo = '"+idUovo+"'");
    }

    public void setMostrato(int idUovo,int mostrato){

        ContentValues valores = new ContentValues();
        valores.put("mostrato",mostrato);
        DatabaseSingleton.getInstance().aggiorna("uovo",valores,"idUovo = '"+idUovo+"'");

    }

    public void setSchiuso(int idUovo,int schiuso){

        ContentValues valores = new ContentValues();
        valores.put("schiuso",schiuso);
        DatabaseSingleton.getInstance().aggiorna("uovo",valores,"idUovo = '"+idUovo+"'");

    }

    public void setKmPercorso(int idUovo, double kmAndado){
        ContentValues valores = new ContentValues();
        valores.put("kmAndado",kmAndado);
        DatabaseSingleton.getInstance().aggiorna("uovo",valores,"idUovo = '"+idUovo+"'");
    }

    public int quantitaUovaInCulla(){
        int quantitaUovaInCulla = 0;
        for(int i = 0; i < uova.size(); i++){
            if(uova.get(i).getInCulla() == 1) quantitaUovaInCulla++;
        }
        Log.i("IN_CULLA:","Quantità di uova in culla: "+ quantitaUovaInCulla);
        return quantitaUovaInCulla;
    }

    public void sorteggiaUovo(){

        int tamComune = creature.get("C").size();
        int tamIncomune = creature.get("I").size();
        int tamRaro = creature.get("R").size();
        int tamLeggendario = creature.get("L").size();

        Log.d("SORTEGGIO_UOVO","C: " + tamComune + " I: "+ tamIncomune + " R: "+ tamRaro + " L: " + tamLeggendario);

        int min = 0;
        int max;

        //ottiene l'ora attuale
        Map<String,String> tempo = TimeUtil.getHoraMinutoSegundoDiaMesAno();

        Log.d("TEMPO",tempo.get("hora") +":"+tempo.get("minuto")+":"+tempo.get("segundo")+" - "+tempo.get("dia")+"/"+tempo.get("mes")+"/"+tempo.get("ano")+" "+tempo.get("timezone"));

        //ottiene i valori da usare nel criterio dei leggendari
        int numIntSorteado = RandomUtil.randomIntInRange(1,101);
        int numIntSorteado2 = RandomUtil.randomIntInRange(1,101);
        int somaMinSegAtual = (Integer.parseInt(tempo.get("minuto")) + Integer.parseInt(tempo.get("segundo")));

        Log.d("SORTEGGIO_UOVO","NumInt: " + numIntSorteado + " NumInt2: " + numIntSorteado2 + " SomaMinSeg: " + somaMinSegAtual);

        //sorteggia UOVO LEGGENDARIO
        if(numIntSorteado % 2 == 0 && numIntSorteado2 % 2 == 0 && somaMinSegAtual % 2 != 0){
            max = tamLeggendario;
            int sorteggio = RandomUtil.randomIntInRange(min,max);
            int idP = creature.get("L").get(sorteggio).getNumero();

            registraUovo(idP, "L", 0, 0, 0, 0);

            Log.d("SORTEGGIO_UOVO","LEGGENDARIO: " + creature.get("L").get(sorteggio).getNome());

            //sorteggia UOVO RARO
        } else if(numIntSorteado % 2 != 0 && numIntSorteado2 % 2 != 0){
            max = tamRaro;
            int sorteggio = RandomUtil.randomIntInRange(min,max);
            int idP = creature.get("R").get(sorteggio).getNumero();

            registraUovo(idP, "R", 0, 0, 0, 0);

            Log.d("SORTEGGIO_UOVO","RARO: " + creature.get("R").get(sorteggio).getNome());

            //sorteggia UOVO INCOMUNE
        } else if(numIntSorteado <= 35){
            max = tamIncomune;
            int sorteggio = RandomUtil.randomIntInRange(min,max);
            int idP = creature.get("I").get(sorteggio).getNumero();

            registraUovo(idP, "I", 0, 0, 0, 0);

            Log.d("SORTEGGIO_UOVO","INCOMUNE: " + creature.get("I").get(sorteggio).getNome());

            //Sorteggia UOVO COMUNE
        } else {
            max = tamComune;
            int sorteggio = RandomUtil.randomIntInRange(min,max);
            int idP = creature.get("C").get(sorteggio).getNumero();

            registraUovo(idP, "C", 0, 0, 0, 0);

            Log.d("SORTEGGIO_UOVO","COMUNE: " + creature.get("C").get(sorteggio).getNome());
        }
    }


    public void sorteggiaApparizioni(double LatMin, double LatMax, double LongMin, double LongMax){

        int tamComune = creature.get("C").size();
        int tamIncomune = creature.get("I").size();
        int tamRaro = creature.get("R").size();
        int tamLeggendario = creature.get("L").size();

        Log.d("SORTEGGIO","C: " + tamComune + " I: "+ tamIncomune + " R: "+ tamRaro + " L: " + tamLeggendario);

        int contAppariziones = 0;
        int totalComuns = 0; //valore da definire nel sorteggio del leggendario

        int min = 0;
        int max;

        //ottiene l'ora attuale
        Map<String,String> tempo = TimeUtil.getHoraMinutoSegundoDiaMesAno();
        Log.d("TEMPO",tempo.get("hora") +":"+tempo.get("minuto")+":"+tempo.get("segundo")+" - "+tempo.get("dia")+"/"+tempo.get("mes")+"/"+tempo.get("ano")+" "+tempo.get("timezone"));

        //ottiene i valori da usare nel criterio dei leggendari
        int numIntSorteado = RandomUtil.randomIntInRange(1,101);
        int numIntSorteado2 = RandomUtil.randomIntInRange(1,101);
        int somaMinSegAtual = (Integer.parseInt(tempo.get("minuto")) + Integer.parseInt(tempo.get("segundo")));

        Log.d("SORTEGGIO","NumInt: " + numIntSorteado + " NumInt2: " + numIntSorteado2 + " SomaMinSeg: " + somaMinSegAtual);

        //definisce se sorteggiare il leggendario
        if(!sorteggiatoLeggendario && numIntSorteado % 2 == 0 && numIntSorteado2 % 2 == 0 && somaMinSegAtual % 2 != 0){
            sorteggiatoLeggendario = true;
            totalComuns = 5;

            //sorteggia creature leggendarie
            for(int i = 0; i < 1; i++){
                max = tamLeggendario;
                int sorteggio = RandomUtil.randomIntInRange(min,max);

                Apparizione ap = new Apparizione();
                ap.setLatitude(RandomUtil.randomDoubleInRange(LatMin, LatMax));
                ap.setLongitude(RandomUtil.randomDoubleInRange(LongMin, LongMax));
                ap.setCreatura(creature.get("L").get(sorteggio));

                this.apparizioni[contAppariziones] = ap;
                Log.d("SORTEGGIO","LEGGENDARIO: " + apparizioni[contAppariziones].getCreatura().getNome());

                contAppariziones++;
            }
        }
        else{
            sorteggiatoLeggendario = false;
            totalComuns = 6;
        }

        //sorteggia creature comuni
        for(int i = 0; i < totalComuns; i++){
            max = tamComune;
            int sorteggio = RandomUtil.randomIntInRange(min,max);

            Apparizione ap = new Apparizione();
            ap.setLatitude(RandomUtil.randomDoubleInRange(LatMin, LatMax));
            ap.setLongitude(RandomUtil.randomDoubleInRange(LongMin, LongMax));
            ap.setCreatura(creature.get("C").get(sorteggio));

            this.apparizioni[contAppariziones] = ap;
            Log.d("SORTEGGIO","COMUNE: " + apparizioni[contAppariziones].getCreatura().getNome());

            contAppariziones++;
        }

        //sorteggia creature incomuni
        for(int i = 0; i < 3; i++){
            max = tamIncomune;
            int sorteggio = RandomUtil.randomIntInRange(min,max);

            Apparizione ap = new Apparizione();
            ap.setLatitude(RandomUtil.randomDoubleInRange(LatMin, LatMax));
            ap.setLongitude(RandomUtil.randomDoubleInRange(LongMin, LongMax));
            ap.setCreatura(creature.get("I").get(sorteggio));

            this.apparizioni[contAppariziones] = ap;
            Log.d("SORTEGGIO","INCOMUNE: " + apparizioni[contAppariziones].getCreatura().getNome());

            contAppariziones++;
        }

        //sorteggia creature rare
        for(int i = 0; i < 1; i++){
            max = tamRaro;
            int sorteggio = RandomUtil.randomIntInRange(min,max);

            Apparizione ap = new Apparizione();
            ap.setLatitude(RandomUtil.randomDoubleInRange(LatMin, LatMax));
            ap.setLongitude(RandomUtil.randomDoubleInRange(LongMin, LongMax));
            ap.setCreatura(creature.get("R").get(sorteggio));

            this.apparizioni[contAppariziones] = ap;
            Log.d("SORTEGGIO","RARO: " + apparizioni[contAppariziones].getCreatura().getNome());

            contAppariziones++;
        }
    }

    public boolean accedi(String login, String password){

        Cursor c = DatabaseSingleton.getInstance().cerca("utente",
                new String[]{"login","password","sessione"},
                "login = '"+login+"' AND password = '"+password+"'",
                "");

        //significa che l'utente che sta accedendo è lo stesso che ha effettuato l'accesso in precedenza
        if(c.getCount() == 1){
            //apre la sessione dell'utente
            ContentValues valores = new ContentValues();
            valores.put("sessione","SI");
            DatabaseSingleton.getInstance().aggiorna("utente",valores,"login = '"+login+"'");

            //chiama solo se l'utente esiste
            caricaUtente();

            c.close();
            return true;
        }else{

            //TODO: implementare le regole di business di sincronizzazione con il server web
            c.close();
            return false;
        }
    }

    public boolean esci(){

        //chiude la sessione dell'utente
        ContentValues valores = new ContentValues();
        valores.put("sessione","NO");

        DatabaseSingleton.getInstance().aggiorna("utente",valores,"login = '"+this.utente.getLogin()+"'");
        return true;
    }

    public boolean registraUtente(String login, String password, String nome, String sesso, String foto){

        Map<String,String> timeStamp = TimeUtil.getHoraMinutoSegundoDiaMesAno();

        ContentValues valores = new ContentValues();
        valores.put("login",login);
        valores.put("password",password);
        valores.put("nome",nome);
        valores.put("sesso",sesso);
        valores.put("foto",foto);
        valores.put("dataRegistrazione",timeStamp.get("dia")+"/"+timeStamp.get("mes")+"/"+timeStamp.get("ano")+" "+timeStamp.get("hora")+":"+timeStamp.get("minuto")+":"+timeStamp.get("segundo"));
        valores.put("sessione","SI");

        //pulisce le tabelle dei luoghi delle creature catturate e dell'utente
        DatabaseSingleton.getInstance().cancella("creatura_utente", "");
        DatabaseSingleton.getInstance().cancella("utente","");

        DatabaseSingleton.getInstance().inserisci("utente",valores);
        //TODO: inviare la registrazione anche al server web

        //chiama solo dopo aver registrato l'utente
        caricaUtente();
        return true;
    }

    public void registraUovo(int idCreatura, String idElementoUovo, int inCulla, int schiuso, int mostrato, int KmPercorso){
        if(this.uova.size() < 9){

            ContentValues valores = new ContentValues();
            valores.put("idCreatura", idCreatura);
            valores.put("idElementoUovo", idElementoUovo);
            valores.put("inCulla", inCulla);
            valores.put("schiuso", schiuso);
            valores.put("mostrato", mostrato);
            valores.put("KmPercorso", KmPercorso);

            DatabaseSingleton.getInstance().inserisci("uovo", valores);

            //aggiunge alla lista di uova
            caricaUova();
        }
    }

    public boolean sessione(){

        Cursor sessione = DatabaseSingleton.getInstance().cerca("utente",new String[]{"login","sessione"},"sessione = 'SI'","");

        if(sessione.getCount() == 1){
            //chiama solo se esiste una sessione dell'utente
            caricaUtente();

            sessione.close();
            return true;
        }else{
            sessione.close();
            return false;
        }
    }

    public Creatura convertCreaturaSerializableToObject(Creatura creatura) {
        //ottiene la lista di creature dal gestore generale
        List<Creatura> listCreatura = this.getCreature();
        Creatura creaturaAux = null;

        //cerca nella lista di creature del gestore la creatura parametro.
        for (Creatura creaturaLista : listCreatura) {
            if (creaturaLista.getNumero() == creatura.getNumero()) {
                creaturaAux = creaturaLista;
                break;
            }
        }

        return creaturaAux;
    }

    public boolean aumentaXp(String evento) {
        final int xpRecebido = getXpEvento(evento);
        final int livelloAtual = getUtente().getLivello();
        final int xpAtual = getUtente().getXp();
        final int xpMax = xpMassimo(livelloAtual);
        int xpFinal = xpAtual, livelloFinal = livelloAtual;

        if((xpAtual + xpRecebido) >= xpMax) {
            xpFinal = (xpAtual + xpRecebido) - xpMax;
            livelloFinal++;

            if(livelloFinal > 40) {
                livelloFinal = 40;
                xpFinal = xpMassimo(livelloFinal);
            }

            getUtente().setLivello(livelloFinal);
        } else {
            xpFinal = xpAtual + xpRecebido;
        }

        getUtente().setXp(xpFinal);

        ContentValues valores = new ContentValues();

        valores.put("login", getUtente().getLogin());
        valores.put("password", getUtente().getPassword());
        valores.put("nome", getUtente().getNome());
        valores.put("sesso", getUtente().getSesso());
        valores.put("foto", getUtente().getFoto());
        valores.put("dataRegistrazione", getUtente().getDataRegistrazione());
        valores.put("sessione", "SI");
        valores.put("livello", livelloFinal);
        valores.put("xp", xpFinal);

        int count = DatabaseSingleton.getInstance().aggiorna("utente", valores, "login='"+getUtente().getLogin()+"'");

        if(count == 1) {
            Toast.makeText(MyApp.getAppContext(), "Hai guadagnato " + xpRecebido + " XP", Toast.LENGTH_SHORT).show();

            if(livelloFinal > livelloAtual) {
                Toast.makeText(MyApp.getAppContext(), "Complimenti! Sei salito al livello " + livelloFinal, Toast.LENGTH_SHORT).show();
            }
        }

        return count == 1;
    }

    public int xpMassimo(int livelloUtente) {
        return livelloUtente*1000;
    }

    public int getXpEvento(String evento) {
        switch(evento) {
            case "cattura":
                return 20;
            case "evolui":
                return 200;
            case "poi":
                return 50;
            case "choca":
                return 100;
            default:
                return 0;
        }
    }
}
