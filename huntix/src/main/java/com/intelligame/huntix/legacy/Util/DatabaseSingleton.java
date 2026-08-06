package com.intelligame.huntix.legacy.Util;

/**
 * Created by Lucas on 12/12/2016.
 */

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import java.lang.reflect.Field;

import com.intelligame.huntix.legacy.R;

public final class DatabaseSingleton {

    protected SQLiteDatabase db;
    private final String NOME_BANCO = "huntix_bd";
    private static DatabaseSingleton INSTANCE = new DatabaseSingleton();

    private final String[] SCRIPT_DATABASE_CREATE = new String[] {
            "CREATE TABLE creatura (" +
                    "  idCreatura INTEGER PRIMARY KEY," +
                    "  nome TEXT NOT NULL," +
                    "  categoria TEXT NOT NULL," +
                    "  foto INTEGER NOT NULL," +
                    "  icona INTEGER NOT NULL," +
                    "  idCaramella INTEGER NOT NULL," +
                    "  idCreaturaBase INTEGER ," +
                    " CONSTRAINT fk_creatura_caramella FOREIGN KEY (idCaramella) REFERENCES caramella (idCaramella)," +    //relazione con Caramella
                    " CONSTRAINT fk_creatura_creatura FOREIGN KEY (idCreaturaBase) REFERENCES creatura (idCreatura)" + //rel. evoluzione
                    ");",
            //ora le creature sono raggruppate per evoluzione
            "INSERT INTO creatura (idCreatura, nome, categoria, foto, icona, idCaramella, idCreaturaBase) VALUES" +
                    "(1, 'Semeletto', 'I', "+ R.drawable.creatura_1+", "+ R.drawable.creatura_1_icona+", 1, null)," +
                    "(2, 'Germoglietto', 'I', "+ R.drawable.creatura_2+", "+ R.drawable.creatura_2_icona+", 1, 1)," +
                    "(3, 'Floridra', 'R', "+ R.drawable.creatura_3+", "+ R.drawable.creatura_3_icona+", 1, 2)," +
                    "(4, 'Salamella', 'I', "+ R.drawable.creatura_4+", "+ R.drawable.creatura_4_icona+", 2, null)," +
                    "(5, 'Salamandra', 'I', "+ R.drawable.creatura_5+", "+ R.drawable.creatura_5_icona+", 2, 4)," +
                    "(6, 'Drago di Fiamma', 'R', "+ R.drawable.creatura_6+", "+ R.drawable.creatura_6_icona+", 2, 5)," +
                    "(7, 'Tartaghetto', 'I', "+ R.drawable.creatura_7+", "+ R.drawable.creatura_7_icona+", 3, null)," +
                    "(8, 'Tartagira', 'I', "+ R.drawable.creatura_8+", "+ R.drawable.creatura_8_icona+", 3, 7)," +
                    "(9, 'Tartagonia', 'R', "+ R.drawable.creatura_9+", "+ R.drawable.creatura_9_icona+", 3, 8)," +
                    "(10, 'Brucolino', 'C', "+ R.drawable.creatura_10+", "+ R.drawable.creatura_10_icona+", 4, null)," +
                    "(11, 'Bozzoleto', 'C', "+ R.drawable.creatura_11+", "+ R.drawable.creatura_11_icona+", 4, 10)," +
                    "(12, 'Farfaluna', 'I', "+ R.drawable.creatura_12+", "+ R.drawable.creatura_12_icona+", 4, 11)," +
                    "(13, 'Apecina', 'C', "+ R.drawable.creatura_13+", "+ R.drawable.creatura_13_icona+", 5, null)," +
                    "(14, 'Bozzolo d''Oro', 'C', "+ R.drawable.creatura_14+", "+ R.drawable.creatura_14_icona+", 5, 13)," +
                    "(15, 'Calabrone', 'I', "+ R.drawable.creatura_15+", "+ R.drawable.creatura_15_icona+", 5, 14)," +
                    "(16, 'Rondinella', 'C', "+ R.drawable.creatura_16+", "+ R.drawable.creatura_16_icona+", 6, null)," +
                    "(17, 'Rondone', 'I', "+ R.drawable.creatura_17+", "+ R.drawable.creatura_17_icona+", 6, 16)," +
                    "(18, 'Falcone', 'R', "+ R.drawable.creatura_18+", "+ R.drawable.creatura_18_icona+", 6, 17)," +
                    "(19, 'Rattino', 'C', "+ R.drawable.creatura_19+", "+ R.drawable.creatura_19_icona+", 7, null)," +
                    "(20, 'Rattone', 'I', "+ R.drawable.creatura_20+", "+ R.drawable.creatura_20_icona+", 7, 19)," +
                    "(21, 'Passerotto', 'C', "+ R.drawable.creatura_21+", "+ R.drawable.creatura_21_icona+", 8, null)," +
                    "(22, 'Sparviero', 'I', "+ R.drawable.creatura_22+", "+ R.drawable.creatura_22_icona+", 8, 21)," +
                    "(23, 'Serpentello', 'C', "+ R.drawable.creatura_23+", "+ R.drawable.creatura_23_icona+", 9, null)," +
                    "(24, 'Cobrione', 'I', "+ R.drawable.creatura_24+", "+ R.drawable.creatura_24_icona+", 9, 23)," +
                    "(25, 'Topofulmine', 'C', "+ R.drawable.creatura_25+", "+ R.drawable.creatura_25_icona+", 10, null)," +
                    "(26, 'Ratofulmine', 'I', "+ R.drawable.creatura_26+", "+ R.drawable.creatura_26_icona+", 10, 25)," +
                    "(27, 'Spinito', 'C', "+ R.drawable.creatura_27+", "+ R.drawable.creatura_27_icona+", 11, null)," +
                    "(28, 'Spinosauro', 'I', "+ R.drawable.creatura_28+", "+ R.drawable.creatura_28_icona+", 11, 27)," +
                    "(29, 'Coniglietta', 'C', "+ R.drawable.creatura_29+", "+ R.drawable.creatura_29_icona+", 12, null)," +
                    "(30, 'Coniglina', 'I', "+ R.drawable.creatura_30+", "+ R.drawable.creatura_30_icona+", 12, 29)," +
                    "(31, 'Coniglire', 'R', "+ R.drawable.creatura_31+", "+ R.drawable.creatura_31_icona+", 12, 30)," +
                    "(32, 'Conigliotto', 'C', "+ R.drawable.creatura_32+", "+ R.drawable.creatura_32_icona+", 13, null)," +
                    "(33, 'Coniglirugo', 'I', "+ R.drawable.creatura_33+", "+ R.drawable.creatura_33_icona+", 13, 32)," +
                    "(34, 'Coniglir', 'R', "+ R.drawable.creatura_34+", "+ R.drawable.creatura_34_icona+", 13, 33)," +
                    "(35, 'Fatina', 'I', "+ R.drawable.creatura_35+", "+ R.drawable.creatura_35_icona+", 14, null)," +
                    "(36, 'Fata delle Stelle', 'R', "+ R.drawable.creatura_36+", "+ R.drawable.creatura_36_icona+", 14, 35)," +
                    "(37, 'Volpina', 'C', "+ R.drawable.creatura_37+", "+ R.drawable.creatura_37_icona+", 15, null)," +
                    "(38, 'Volpe Stellata', 'R', "+ R.drawable.creatura_38+", "+ R.drawable.creatura_38_icona+", 15, 37)," +
                    "(39, 'Palloncino', 'C', "+ R.drawable.creatura_39+", "+ R.drawable.creatura_39_icona+", 16, null)," +
                    "(40, 'Palloregale', 'R', "+ R.drawable.creatura_40+", "+ R.drawable.creatura_40_icona+", 16, 39)," +
                    "(41, 'Pipistrellino', 'C', "+ R.drawable.creatura_41+", "+ R.drawable.creatura_41_icona+", 17, null)," +
                    "(42, 'Pipistrone', 'I', "+ R.drawable.creatura_42+", "+ R.drawable.creatura_42_icona+", 17, 41)," +
                    "(43, 'Erbetta', 'C', "+ R.drawable.creatura_43+", "+ R.drawable.creatura_43_icona+", 18, null)," +
                    "(44, 'Fiore Oscuro', 'C', "+ R.drawable.creatura_44+", "+ R.drawable.creatura_44_icona+", 18,43)," +
                    "(45, 'Floridosso', 'I', "+ R.drawable.creatura_45+", "+ R.drawable.creatura_45_icona+", 18,44)," +
                    "(46, 'Funghetto', 'C', "+ R.drawable.creatura_46+", "+ R.drawable.creatura_46_icona+", 19, null)," +
                    "(47, 'Fungo Veglia', 'I', "+ R.drawable.creatura_47+", "+ R.drawable.creatura_47_icona+", 19, 46)," +
                    "(48, 'Lucciolina', 'C', "+ R.drawable.creatura_48+", "+ R.drawable.creatura_48_icona+", 20, null)," +
                    "(49, 'Falena Reale', 'I', "+ R.drawable.creatura_49+", "+ R.drawable.creatura_49_icona+", 20, 48)," +
                    "(50, 'Talpino', 'C', "+ R.drawable.creatura_50+", "+ R.drawable.creatura_50_icona+", 21, null)," +
                    "(51, 'Talpa Trina', 'I', "+ R.drawable.creatura_51+", "+ R.drawable.creatura_51_icona+", 21, 50)," +
                    "(52, 'Gattino', 'C', "+ R.drawable.creatura_52+", "+ R.drawable.creatura_52_icona+", 22, null)," +
                    "(53, 'Gatto del Sole', 'I', "+ R.drawable.creatura_53+", "+ R.drawable.creatura_53_icona+", 22, 52)," +
                    "(54, 'Anatroletto', 'C', "+ R.drawable.creatura_54+", "+ R.drawable.creatura_54_icona+", 23, null)," +
                    "(55, 'Anatro Reale', 'I', "+ R.drawable.creatura_55+", "+ R.drawable.creatura_55_icona+", 23, 54)," +
                    "(56, 'Scimmietta', 'C', "+ R.drawable.creatura_56+", "+ R.drawable.creatura_56_icona+", 24, null)," +
                    "(57, 'Scimmione', 'I', "+ R.drawable.creatura_57+", "+ R.drawable.creatura_57_icona+", 24, 56)," +
                    "(58, 'Cagnetto', 'C', "+ R.drawable.creatura_58+", "+ R.drawable.creatura_58_icona+", 25, null)," +
                    "(59, 'Cane Ardente', 'R', "+ R.drawable.creatura_59+", "+ R.drawable.creatura_59_icona+", 25, 58)," +
                    "(60, 'Girino', 'C', "+ R.drawable.creatura_60+", "+ R.drawable.creatura_60_icona+", 26, null)," +
                    "(61, 'Girindoro', 'C', "+ R.drawable.creatura_61+", "+ R.drawable.creatura_61_icona+", 26, 60)," +
                    "(62, 'Rana Furia', 'R', "+ R.drawable.creatura_62+", "+ R.drawable.creatura_62_icona+", 26, 61)," +
                    "(63, 'Timidino', 'C', "+ R.drawable.creatura_63+", "+ R.drawable.creatura_63_icona+", 27, null)," +
                    "(64, 'Mago delle Stelle', 'I', "+ R.drawable.creatura_64+", "+ R.drawable.creatura_64_icona+", 27, 63)," +
                    "(65, 'Stregone', 'R', "+ R.drawable.creatura_65+", "+ R.drawable.creatura_65_icona+", 27, 64)," +
                    "(66, 'Fortetto', 'C', "+ R.drawable.creatura_66+", "+ R.drawable.creatura_66_icona+", 28, null)," +
                    "(67, 'Forteone', 'I', "+ R.drawable.creatura_67+", "+ R.drawable.creatura_67_icona+", 28, 66)," +
                    "(68, 'Campione', 'R', "+ R.drawable.creatura_68+", "+ R.drawable.creatura_68_icona+", 28, 67)," +
                    "(69, 'Campanella', 'C', "+ R.drawable.creatura_69+", "+ R.drawable.creatura_69_icona+", 29, null)," +
                    "(70, 'Campanone', 'I', "+ R.drawable.creatura_70+", "+ R.drawable.creatura_70_icona+", 29, 69)," +
                    "(71, 'Fiorbocca', 'R', "+ R.drawable.creatura_71+", "+ R.drawable.creatura_71_icona+", 29, 70)," +
                    "(72, 'Medusetta', 'C', "+ R.drawable.creatura_72+", "+ R.drawable.creatura_72_icona+", 30, null)," +
                    "(73, 'Medusone', 'I', "+ R.drawable.creatura_73+", "+ R.drawable.creatura_73_icona+", 30, 72)," +
                    "(74, 'Sassetto', 'C', "+ R.drawable.creatura_74+", "+ R.drawable.creatura_74_icona+", 31, null)," +
                    "(75, 'Sassoione', 'I', "+ R.drawable.creatura_75+", "+ R.drawable.creatura_75_icona+", 31, 74)," +
                    "(76, 'Colosso di Pietra', 'R', "+ R.drawable.creatura_76+", "+ R.drawable.creatura_76_icona+", 31, 75)," +
                    "(77, 'Puledrino', 'C', "+ R.drawable.creatura_77+", "+ R.drawable.creatura_77_icona+", 32, null)," +
                    "(78, 'Destriero', 'I', "+ R.drawable.creatura_78+", "+ R.drawable.creatura_78_icona+", 32, 77)," +
                    "(79, 'Lumachino', 'C', "+ R.drawable.creatura_79+", "+ R.drawable.creatura_79_icona+", 33, null)," +
                    "(80, 'Lumacotto', 'I', "+ R.drawable.creatura_80+", "+ R.drawable.creatura_80_icona+", 33, 79)," +
                    "(81, 'Magnetino', 'C', "+ R.drawable.creatura_81+", "+ R.drawable.creatura_81_icona+", 34, null)," +
                    "(82, 'Magnetone', 'I', "+ R.drawable.creatura_82+", "+ R.drawable.creatura_82_icona+", 34, 81)," +
                    "(83, 'Anatrina', 'C', "+ R.drawable.creatura_83+", "+ R.drawable.creatura_83_icona+", 35, null)," +
                    "(84, 'Struzzo Duo', 'C', "+ R.drawable.creatura_84+", "+ R.drawable.creatura_84_icona+", 36, null)," +
                    "(85, 'Struzzo Trio', 'I', "+ R.drawable.creatura_85+", "+ R.drawable.creatura_85_icona+", 36, 84)," +
                    "(86, 'Fochina', 'C', "+ R.drawable.creatura_86+", "+ R.drawable.creatura_86_icona+", 37, null)," +
                    "(87, 'Trichecone', 'R', "+ R.drawable.creatura_87+", "+ R.drawable.creatura_87_icona+", 37, 86)," +
                    "(88, 'Fanghetto', 'C', "+ R.drawable.creatura_88+", "+ R.drawable.creatura_88_icona+", 38, null)," +
                    "(89, 'Re del Fango', 'R', "+ R.drawable.creatura_89+", "+ R.drawable.creatura_89_icona+", 38, 88)," +
                    "(90, 'Conchiglietta', 'C', "+ R.drawable.creatura_90+", "+ R.drawable.creatura_90_icona+", 39, null)," +
                    "(91, 'Ostrica Reale', 'R', "+ R.drawable.creatura_91+", "+ R.drawable.creatura_91_icona+", 39, 90)," +
                    "(92, 'Fantasmino', 'C', "+ R.drawable.creatura_92+", "+ R.drawable.creatura_92_icona+", 40, null)," +
                    "(93, 'Fantasmore', 'I', "+ R.drawable.creatura_93+", "+ R.drawable.creatura_93_icona+", 40, 92)," +
                    "(94, 'Spettro', 'R', "+ R.drawable.creatura_94+", "+ R.drawable.creatura_94_icona+", 40, 93)," +
                    "(95, 'Pietraserpe', 'C', "+ R.drawable.creatura_95+", "+ R.drawable.creatura_95_icona+", 41, null)," +
                    "(96, 'Sognino', 'C', "+ R.drawable.creatura_96+", "+ R.drawable.creatura_96_icona+", 42, null)," +
                    "(97, 'Mago dei Sogni', 'I', "+ R.drawable.creatura_97+", "+ R.drawable.creatura_97_icona+", 42, 96)," +
                    "(98, 'Granchietto', 'C', "+ R.drawable.creatura_98+", "+ R.drawable.creatura_98_icona+", 43, null)," +
                    "(99, 'Granchione', 'I', "+ R.drawable.creatura_99+", "+ R.drawable.creatura_99_icona+", 43, 98)," +
                    "(100, 'Pallafulmine', 'C', "+ R.drawable.creatura_100+", "+ R.drawable.creatura_100_icona+", 44, null)," +
                    "(101, 'Sfera del Tuono', 'I', "+ R.drawable.creatura_101+", "+ R.drawable.creatura_101_icona+", 44, 100)," +
                    "(102, 'Uovetto', 'I', "+ R.drawable.creatura_102+", "+ R.drawable.creatura_102_icona+", 45, null)," +
                    "(103, 'Cocco Alto', 'R', "+ R.drawable.creatura_103+", "+ R.drawable.creatura_103_icona+", 45, 102)," +
                    "(104, 'Teschinello', 'C', "+ R.drawable.creatura_104+", "+ R.drawable.creatura_104_icona+", 46, null)," +
                    "(105, 'Guerriero d''Osso', 'I', "+ R.drawable.creatura_105+", "+ R.drawable.creatura_105_icona+", 46, 104)," +
                    "(106, 'Gambalone', 'I', "+ R.drawable.creatura_106+", "+ R.drawable.creatura_106_icona+", 47, null)," +
                    "(107, 'Pugno d'Acciaio', 'I', "+ R.drawable.creatura_107+", "+ R.drawable.creatura_107_icona+", 47, 106)," +
                    "(108, 'Linguetta', 'I', "+ R.drawable.creatura_108+", "+ R.drawable.creatura_108_icona+", 48, null)," +
                    "(109, 'Nuvoletta', 'C', "+ R.drawable.creatura_109+", "+ R.drawable.creatura_109_icona+", 49, null)," +
                    "(110, 'Nembo Regale', 'I', "+ R.drawable.creatura_110+", "+ R.drawable.creatura_110_icona+", 49, 109)," +
                    "(111, 'Rinoceronte', 'C', "+ R.drawable.creatura_111+", "+ R.drawable.creatura_111_icona+", 50, null)," +
                    "(112, 'Rinocorazza', 'I', "+ R.drawable.creatura_112+", "+ R.drawable.creatura_112_icona+", 50, 111)," +
                    "(113, 'Ovetta', 'I', "+ R.drawable.creatura_113+", "+ R.drawable.creatura_113_icona+", 51, null)," +
                    "(114, 'Alga Ritorta', 'I', "+ R.drawable.creatura_114+", "+ R.drawable.creatura_114_icona+", 52, null)," +
                    "(115, 'Cangurino', 'I', "+ R.drawable.creatura_115+", "+ R.drawable.creatura_115_icona+", 53, null)," +
                    "(116, 'Cavalluccio', 'C', "+ R.drawable.creatura_116+", "+ R.drawable.creatura_116_icona+", 54, null)," +
                    "(117, 'Drago Marino', 'I', "+ R.drawable.creatura_117+", "+ R.drawable.creatura_117_icona+", 54, 116)," +
                    "(118, 'Pesciolino', 'C', "+ R.drawable.creatura_118+", "+ R.drawable.creatura_118_icona+", 55, null)," +
                    "(119, 'Pesce Reale', 'I', "+ R.drawable.creatura_119+", "+ R.drawable.creatura_119_icona+", 55, 118)," +
                    "(120, 'Stella Marina', 'C', "+ R.drawable.creatura_120+", "+ R.drawable.creatura_120_icona+", 56, null)," +
                    "(121, 'Stellonia', 'I', "+ R.drawable.creatura_121+", "+ R.drawable.creatura_121_icona+", 56, 120)," +
                    "(122, 'Mimo Magico', 'I', "+ R.drawable.creatura_122+", "+ R.drawable.creatura_122_icona+", 57, null)," +
                    "(123, 'Falce d''Erba', 'I', "+ R.drawable.creatura_123+", "+ R.drawable.creatura_123_icona+", 58, null)," +
                    "(124, 'Strega di Ghiaccio', 'I', "+ R.drawable.creatura_124+", "+ R.drawable.creatura_124_icona+", 59, null)," +
                    "(125, 'Scintillio', 'I', "+ R.drawable.creatura_125+", "+ R.drawable.creatura_125_icona+", 60, null)," +
                    "(126, 'Vampa', 'I', "+ R.drawable.creatura_126+", "+ R.drawable.creatura_126_icona+", 61, null)," +
                    "(127, 'Scarabeone', 'I', "+ R.drawable.creatura_127+", "+ R.drawable.creatura_127_icona+", 62, null)," +
                    "(128, 'Torone', 'I', "+ R.drawable.creatura_128+", "+ R.drawable.creatura_128_icona+", 63, null)," +
                    "(129, 'Carpa Magica', 'C', "+ R.drawable.creatura_129+", "+ R.drawable.creatura_129_icona+", 64, null)," +
                    "(130, 'Drago delle Acque', 'I', "+ R.drawable.creatura_130+", "+ R.drawable.creatura_130_icona+", 64, 129)," +
                    "(131, 'Mostro Marino', 'I', "+ R.drawable.creatura_131+", "+ R.drawable.creatura_131_icona+", 65, null)," +
                    "(132, 'Trasformino', 'I', "+ R.drawable.creatura_132+", "+ R.drawable.creatura_132_icona+", 66, null)," +
                    "(133, 'Multiovetto', 'I', "+ R.drawable.creatura_133+", "+ R.drawable.creatura_133_icona+", 67, null)," +
                    "(134, 'Acquaone', 'R', "+ R.drawable.creatura_134+", "+ R.drawable.creatura_134_icona+", 67, 133)," +
                    "(135, 'Fulmione', 'R', "+ R.drawable.creatura_135+", "+ R.drawable.creatura_135_icona+", 67, 133)," +
                    "(136, 'Fiammone', 'R', "+ R.drawable.creatura_136+", "+ R.drawable.creatura_136_icona+", 67, 133)," +
                    "(137, 'Poligono', 'R', "+ R.drawable.creatura_137+", "+ R.drawable.creatura_137_icona+", 68, null)," +
                    "(138, 'Spirale Antica', 'R', "+ R.drawable.creatura_138+", "+ R.drawable.creatura_138_icona+", 69, null)," +
                    "(139, 'Spirale Maestro', 'R', "+ R.drawable.creatura_139+", "+ R.drawable.creatura_139_icona+", 69, 138)," +
                    "(140, 'Guscio Antico', 'R', "+ R.drawable.creatura_140+", "+ R.drawable.creatura_140_icona+", 70, null)," +
                    "(141, 'Scheletro Antico', 'R', "+ R.drawable.creatura_141+", "+ R.drawable.creatura_141_icona+", 70, 140)," +
                    "(142, 'Drago Volante', 'R', "+ R.drawable.creatura_142+", "+ R.drawable.creatura_142_icona+", 71, null)," +
                    "(143, 'Orsettone', 'I', "+ R.drawable.creatura_143+", "+ R.drawable.creatura_143_icona+", 72, null)," +
                    "(144, 'Uccello di Ghiaccio', 'L', "+ R.drawable.creatura_144+", "+ R.drawable.creatura_144_icona+", 73, null)," +
                    "(145, 'Uccello del Tuono', 'L', "+ R.drawable.creatura_145+", "+ R.drawable.creatura_145_icona+", 74, null)," +
                    "(146, 'Uccello di Fuoco', 'L', "+ R.drawable.creatura_146+", "+ R.drawable.creatura_146_icona+", 75, null)," +
                    "(147, 'Dragoletto', 'I', "+ R.drawable.creatura_147+", "+ R.drawable.creatura_147_icona+", 76, null)," +
                    "(148, 'Dragosfera', 'I', "+ R.drawable.creatura_148+", "+ R.drawable.creatura_148_icona+", 76, 147)," +
                    "(149, 'Dragone', 'R', "+ R.drawable.creatura_149+", "+ R.drawable.creatura_149_icona+", 76, 148)," +
                    "(150, 'Mente Suprema', 'L', "+ R.drawable.creatura_150+", "+ R.drawable.creatura_150_icona+", 77, null)," +
                    "(151, 'Misterioso', 'L', "+ R.drawable.creatura_151+", "+ R.drawable.creatura_151_icona+", 78, null);",

            "CREATE TABLE caramella (" +
                    "  idCaramella INTEGER PRIMARY KEY," +
                    "  nome TEXT NOT NULL," +
                    "  quantita INTEGER NOT NULL" +
                    ");",

            "INSERT INTO caramella (idCaramella, nome, quantita) VALUES" +
                    "(1, 'Semeletto', 0)," +
                    "(2, 'Salamella', 0)," +
                    "(3, 'Tartaghetto', 0)," +
                    "(4, 'Brucolino', 0)," +
                    "(5, 'Apecina', 0)," +
                    "(6, 'Rondinella', 0)," +
                    "(7, 'Rattino', 0)," +
                    "(8, 'Passerotto', 0)," +
                    "(9, 'Serpentello', 0)," +
                    "(10, 'Topofulmine', 0)," +
                    "(11, 'Spinito', 0)," +
                    "(12, 'Coniglietta', 0)," +
                    "(13, 'Conigliotto', 0)," +
                    "(14, 'Fatina', 0)," +
                    "(15, 'Volpina', 0)," +
                    "(16, 'Palloncino', 0)," +
                    "(17, 'Pipistrellino', 0)," +
                    "(18, 'Erbetta', 0)," +
                    "(19, 'Funghetto', 0)," +
                    "(20, 'Lucciolina', 0)," +
                    "(21, 'Talpino', 0)," +
                    "(22, 'Gattino', 0)," +
                    "(23, 'Anatroletto', 0)," +
                    "(24, 'Scimmietta', 0)," +
                    "(25, 'Cagnetto', 0)," +
                    "(26, 'Girino', 0)," +
                    "(27, 'Timidino', 0)," +
                    "(28, 'Fortetto', 0)," +
                    "(29, 'Campanella', 0)," +
                    "(30, 'Medusetta', 0)," +
                    "(31, 'Sassetto', 0)," +
                    "(32, 'Puledrino', 0)," +
                    "(33, 'Lumachino', 0)," +
                    "(34, 'Magnetino', 0)," +
                    "(35, 'Anatrina', 0)," +
                    "(36, 'Struzzo Duo', 0)," +
                    "(37, 'Fochina', 0)," +
                    "(38, 'Fanghetto', 0)," +
                    "(39, 'Conchiglietta', 0)," +
                    "(40, 'Fantasmino', 0)," +
                    "(41, 'Pietraserpe', 0)," +
                    "(42, 'Sognino', 0)," +
                    "(43, 'Granchietto', 0)," +
                    "(44, 'Pallafulmine', 0)," +
                    "(45, 'Uovetto', 0)," +
                    "(46, 'Teschinello', 0)," +
                    "(47, 'Pugilino', 0)," +
                    "(48, 'Linguetta', 0)," +
                    "(49, 'Nuvoletta', 0)," +
                    "(50, 'Rinoceronte', 0)," +
                    "(51, 'Ovetta', 0)," +
                    "(52, 'Alga Ritorta', 0)," +
                    "(53, 'Cangurino', 0)," +
                    "(54, 'Cavalluccio', 0)," +
                    "(55, 'Pesciolino', 0)," +
                    "(56, 'Stella Marina', 0)," +
                    "(57, 'Mimo Magico', 0)," +
                    "(58, 'Falce d''Erba', 0)," +
                    "(59, 'Strega di Ghiaccio', 0)," +
                    "(60, 'Scintillio', 0)," +
                    "(61, 'Vampa', 0)," +
                    "(62, 'Scarabeone', 0)," +
                    "(63, 'Torone', 0)," +
                    "(64, 'Carpa Magica', 0)," +
                    "(65, 'Mostro Marino', 0)," +
                    "(66, 'Trasformino', 0)," +
                    "(67, 'Multiovetto', 0)," +
                    "(68, 'Poligono', 0)," +
                    "(69, 'Spirale Antica', 0)," +
                    "(70, 'Guscio Antico', 0)," +
                    "(71, 'Drago Volante', 0)," +
                    "(72, 'Orsettone', 0)," +
                    "(73, 'Uccello di Ghiaccio', 0)," +
                    "(74, 'Uccello del Tuono', 0)," +
                    "(75, 'Uccello di Fuoco', 0)," +
                    "(76, 'Dragoletto', 0)," +
                    "(77, 'Mente Suprema', 0)," +
                    "(78, 'Misterioso', 0);",

            "CREATE TABLE elemento (" +
                    "  idElemento INTEGER PRIMARY KEY," +
                    "  nome TEXT NOT NULL" +
                    ");",
            "INSERT INTO elemento (idElemento, nome) VALUES" +
                    "(1, 'Normale')," +
                    "(2, 'Fuoco')," +
                    "(3, 'Lotta')," +
                    "(4, 'Acqua')," +
                    "(5, 'Volo')," +
                    "(6, 'Erba')," +
                    "(7, 'Veleno')," +
                    "(8, 'Elettro')," +
                    "(9, 'Terra')," +
                    "(10, 'Psico')," +
                    "(11, 'Roccia')," +
                    "(12, 'Ghiaccio')," +
                    "(13, 'Coleottero')," +
                    "(14, 'Drago')," +
                    "(15, 'Spettro')," +
                    "(16, 'Buio')," +
                    "(17, 'Acciaio')," +
                    "(18, 'Fata');",
            "CREATE TABLE creaturaelemento (" +
                    "  idCreatura INTEGER NOT NULL," +
                    "  idElemento INTEGER NOT NULL," +
                    "  PRIMARY KEY  (idCreatura,idElemento)," +
                    "  CONSTRAINT fk_creaturaelemento_creatura FOREIGN KEY (idCreatura) REFERENCES creatura (idCreatura)," +
                    "  CONSTRAINT fk_creaturaelemento_elemento FOREIGN KEY (idElemento) REFERENCES elemento (idElemento)" +
                    ");",
            "INSERT INTO creaturaelemento (idCreatura, idElemento) VALUES" +
                    "(16, 1)," +
                    "(17, 1)," +
                    "(18, 1)," +
                    "(19, 1)," +
                    "(20, 1)," +
                    "(21, 1)," +
                    "(22, 1)," +
                    "(39, 1)," +
                    "(40, 1)," +
                    "(52, 1)," +
                    "(53, 1)," +
                    "(83, 1)," +
                    "(84, 1)," +
                    "(85, 1)," +
                    "(108, 1)," +
                    "(113, 1)," +
                    "(115, 1)," +
                    "(128, 1)," +
                    "(132, 1)," +
                    "(133, 1)," +
                    "(137, 1)," +
                    "(143, 1)," +
                    "(4, 2)," +
                    "(5, 2)," +
                    "(6, 2)," +
                    "(37, 2)," +
                    "(38, 2)," +
                    "(58, 2)," +
                    "(59, 2)," +
                    "(77, 2)," +
                    "(78, 2)," +
                    "(126, 2)," +
                    "(136, 2)," +
                    "(146, 2)," +
                    "(56, 3)," +
                    "(57, 3)," +
                    "(62, 3)," +
                    "(66, 3)," +
                    "(67, 3)," +
                    "(68, 3)," +
                    "(106, 3)," +
                    "(107, 3)," +
                    "(7, 4)," +
                    "(8, 4)," +
                    "(9, 4)," +
                    "(54, 4)," +
                    "(55, 4)," +
                    "(60, 4)," +
                    "(61, 4)," +
                    "(62, 4)," +
                    "(72, 4)," +
                    "(73, 4)," +
                    "(79, 4)," +
                    "(80, 4)," +
                    "(86, 4)," +
                    "(87, 4)," +
                    "(90, 4)," +
                    "(91, 4)," +
                    "(98, 4)," +
                    "(99, 4)," +
                    "(116, 4)," +
                    "(117, 4)," +
                    "(118, 4)," +
                    "(119, 4)," +
                    "(120, 4)," +
                    "(121, 4)," +
                    "(129, 4)," +
                    "(130, 4)," +
                    "(131, 4)," +
                    "(134, 4)," +
                    "(138, 4)," +
                    "(139, 4)," +
                    "(140, 4)," +
                    "(141, 4)," +
                    "(6, 5)," +
                    "(12, 5)," +
                    "(16, 5)," +
                    "(17, 5)," +
                    "(18, 5)," +
                    "(21, 5)," +
                    "(22, 5)," +
                    "(41, 5)," +
                    "(42, 5)," +
                    "(83, 5)," +
                    "(84, 5)," +
                    "(85, 5)," +
                    "(123, 5)," +
                    "(130, 5)," +
                    "(142, 5)," +
                    "(144, 5)," +
                    "(145, 5)," +
                    "(146, 5)," +
                    "(149, 5)," +
                    "(1, 6)," +
                    "(2, 6)," +
                    "(3, 6)," +
                    "(43, 6)," +
                    "(44, 6)," +
                    "(45, 6)," +
                    "(46, 6)," +
                    "(47, 6)," +
                    "(69, 6)," +
                    "(70, 6)," +
                    "(71, 6)," +
                    "(102, 6)," +
                    "(103, 6)," +
                    "(114, 6)," +
                    "(1, 7)," +
                    "(2, 7)," +
                    "(3, 7)," +
                    "(13, 7)," +
                    "(14, 7)," +
                    "(15, 7)," +
                    "(23, 7)," +
                    "(24, 7)," +
                    "(29, 7)," +
                    "(30, 7)," +
                    "(31, 7)," +
                    "(32, 7)," +
                    "(33, 7)," +
                    "(34, 7)," +
                    "(41, 7)," +
                    "(42, 7)," +
                    "(43, 7)," +
                    "(44, 7)," +
                    "(45, 7)," +
                    "(48, 7)," +
                    "(49, 7)," +
                    "(69, 7)," +
                    "(70, 7)," +
                    "(71, 7)," +
                    "(72, 7)," +
                    "(73, 7)," +
                    "(88, 7)," +
                    "(89, 7)," +
                    "(92, 7)," +
                    "(93, 7)," +
                    "(94, 7)," +
                    "(109, 7)," +
                    "(110, 7)," +
                    "(25, 8)," +
                    "(26, 8)," +
                    "(81, 8)," +
                    "(82, 8)," +
                    "(100, 8)," +
                    "(101, 8)," +
                    "(125, 8)," +
                    "(135, 8)," +
                    "(145, 8)," +
                    "(27, 9)," +
                    "(28, 9)," +
                    "(31, 9)," +
                    "(34, 9)," +
                    "(50, 9)," +
                    "(51, 9)," +
                    "(74, 9)," +
                    "(75, 9)," +
                    "(76, 9)," +
                    "(95, 9)," +
                    "(104, 9)," +
                    "(105, 9)," +
                    "(111, 9)," +
                    "(112, 9)," +
                    "(63, 10)," +
                    "(64, 10)," +
                    "(65, 10)," +
                    "(79, 10)," +
                    "(80, 10)," +
                    "(96, 10)," +
                    "(97, 10)," +
                    "(102, 10)," +
                    "(103, 10)," +
                    "(121, 10)," +
                    "(122, 10)," +
                    "(124, 10)," +
                    "(150, 10)," +
                    "(151, 10)," +
                    "(74, 11)," +
                    "(75, 11)," +
                    "(76, 11)," +
                    "(95, 11)," +
                    "(111, 11)," +
                    "(112, 11)," +
                    "(138, 11)," +
                    "(139, 11)," +
                    "(140, 11)," +
                    "(141, 11)," +
                    "(142, 11)," +
                    "(87, 12)," +
                    "(91, 12)," +
                    "(124, 12)," +
                    "(131, 12)," +
                    "(144, 12)," +
                    "(10, 13)," +
                    "(11, 13)," +
                    "(12, 13)," +
                    "(13, 13)," +
                    "(14, 13)," +
                    "(15, 13)," +
                    "(46, 13)," +
                    "(47, 13)," +
                    "(48, 13)," +
                    "(49, 13)," +
                    "(123, 13)," +
                    "(127, 13)," +
                    "(147, 14)," +
                    "(148, 14)," +
                    "(149, 14)," +
                    "(92, 15)," +
                    "(93, 15)," +
                    "(94, 15)," +
                    "(81, 17)," +
                    "(82, 17)," +
                    "(35, 18)," +
                    "(36, 18)," +
                    "(39, 18)," +
                    "(40, 18)," +
                    "(122, 18);",
            "CREATE TABLE utente (" +
                    "  login TEXT PRIMARY KEY," +
                    "  password TEXT NOT NULL," +
                    "  nome TEXT NOT NULL," +
                    "  sesso TEXT NOT NULL," +
                    "  foto TEXT," +
                    "  dataRegistrazione TEXT NOT NULL," +
                    "  sessione TEXT NOT NULL," +
                    "  livello INTEGER DEFAULT 1 NOT NULL," +
                    "  xp INTEGER DEFAULT 0 NOT NULL" +
                    ");",
            "CREATE TABLE creatura_utente (" +
                    "  login TEXT NOT NULL," +
                    "  idCreatura INTEGER NOT NULL," +
                    "  latitude REAL NOT NULL," +
                    "  longitude REAL NOT NULL," +
                    "  dataCattura TEXT NOT NULL," +
                    "  bloccato INTEGER NOT NULL DEFAULT 0," +
                    "  PRIMARY KEY  (login,idCreatura,dataCattura)," +
                    "  CONSTRAINT fk_utente_creatura_login FOREIGN KEY (login) REFERENCES utente (login)," +
                    "  CONSTRAINT fk_utente_creatura_creatura FOREIGN KEY (idCreatura) REFERENCES creatura (idCreatura)" +
                    ");",
            "CREATE TABLE elemento_uovo (" +
                    "  idElementoUovo TEXT PRIMARY KEY," +
                    "  foto INTEGER NOT NULL," +
                    "  fotoTermoculla INTEGER NOT NULL," +
                    "  chilometraggio DOUBLE NOT NULL," +
                    "  cor TEXT NOT NULL" +
                    ");",
            "INSERT INTO elemento_uovo (idElementoUovo, foto, fotoTermoculla,chilometraggio,cor) VALUES" +
                    "('C', "+R.drawable.uovo_verde+", "+R.drawable.termoculla_verde+",2,'Verde')," +
                    "('I', "+R.drawable.uovo_arancione+", "+R.drawable.termoculla_arancione+",5,'Arancione')," +
                    "('R', "+R.drawable.uovo_blu+", "+R.drawable.termoculla_blu+",7,'Blu')," +
                    "('L', "+R.drawable.uovo_rosso+", "+R.drawable.termoculla_rossa+",10,'Rosso');",
            "CREATE TABLE uovo (" +
                    "  idUovo INTEGER PRIMARY KEY AUTOINCREMENT ," +
                    "  idCreatura INTEGER NOT NULL," +
                    "  idElementoUovo TEXT NOT NULL," +
                    "  inCulla INTEGER NOT NULL," +
                    "  schiuso INTEGER NOT NULL," +
                    "  mostrato INTEGER NOT NULL," +
                    "  KmPercorso DOUBLE NOT NULL," +
                    "  CONSTRAINT fk_utente_creatura_creatura FOREIGN KEY (idCreatura) REFERENCES creatura (idCreatura)," +
                    "  CONSTRAINT fk_elemento_uovo FOREIGN KEY (idElementoUovo) REFERENCES elemento_uovo (idElementoUovo)" +
                    ");",
            "CREATE TABLE poi (" +
                    "  idPoi TEXT NOT NULL," +
                    "  latitude REAL NOT NULL," +
                    "  longitude REAL NOT NULL," +
                    "  disponibile BOOLEAN NOT NULL," +
                    "  PRIMARY KEY  (idPoi)" +
                    ");",
            "CREATE TABLE interazionepoi ("+
                    " idPoi TEXT NOT NULL,"+
                    " loginUtente TEXT NOT NULL,"+
                    " ultimoAccesso TEXT NOT NULL,"+
                    " PRIMARY KEY(idPoi, loginUtente),"+
                    " CONSTRAINT fk_interazionepoi_poi FOREIGN KEY (idPoi) REFERENCES poi (idPoi),"+
                    " CONSTRAINT fk_interazionepoi_utente FOREIGN KEY (loginUtente) REFERENCES utente (login)"+
                    ");",
            "CREATE TABLE traduzione (" +
                    "  chiave TEXT NOT NULL," +
                    "  italiano TEXT NOT NULL," +
                    "  PRIMARY KEY  (chiave)" +
                    ");",
            "INSERT INTO traduzione (chiave, italiano) VALUES" +
                    "('accounting','Ufficio Contabilità'),"+
                    "('airport','Aeroporto'),"+
                    "('amusement_park','Parco Divertimenti'),"+
                    "('aquarium','Acquario'),"+
                    "('art_gallery','Galleria d'Arte'),"+
                    "('atm','Bancamat'),"+
                    "('bakery','Panetteria'),"+
                    "('bank','Banca'),"+
                    "('bar','Bar'),"+
                    "('beauty_salon','Salone di Bellezza'),"+
                    "('bicycle_store','Negozio di Biciclette'),"+
                    "('book_store','Libreria'),"+
                    "('bowling_alley','Bowling'),"+
                    "('bus_station','Fermata Autobus'),"+
                    "('cafe','Caffè'),"+
                    "('campground','Area Campeggio'),"+
                    "('car_dealer','Concessionaria Auto'),"+
                    "('car_rental','Noleggio Auto'),"+
                    "('car_repair','Meccanico'),"+
                    "('car_wash','Autolavaggio'),"+
                    "('casino','Casinò'),"+
                    "('cemetery','Cimitero'),"+
                    "('church','Chiesa'),"+
                    "('city_hall','Municipio'),"+
                    "('clothing_store','Negozio di Abbigliamento'),"+
                    "('convenience_store','Minimarket'),"+
                    "('courthouse','Tribunale'),"+
                    "('dentist','Dentista'),"+
                    "('department_store','Grandi Magazzini'),"+
                    "('doctor','Medico'),"+
                    "('drugstore','Farmacia'),"+
                    "('electrician','Elettricista'),"+
                    "('embassy','Ambasciata'),"+
                    "('fire_station','Caserma dei Pompieri'),"+
                    "('florist','Fioraio'),"+
                    "('funeral_home','Agenzia Funebre'),"+
                    "('furniture_store','Negozio di Mobili'),"+
                    "('gas_station','Distributore di Benzina'),"+
                    "('gym','Palestra'),"+
                    "('hair_care','Parrucchiere'),"+
                    "('hardware_store','Negozio di Ferramenta'),"+
                    "('hindu_temple','Tempio Indù'),"+
                    "('home_goods_store','Articoli per la Casa'),"+
                    "('hospital','Ospedale'),"+
                    "('insurance_agency','Agenzia Assicurativa'),"+
                    "('jewelry_store','Gioielleria'),"+
                    "('laundry','Lavanderia'),"+
                    "('lawyer','Studio Legale'),"+
                    "('library','Biblioteca'),"+
                    "('light_rail_station','Stazione Tranviaria'),"+
                    "('liquor_store','Enoteca'),"+
                    "('local_government_office','Ufficio Governo Locale'),"+
                    "('locksmith','Fabbro'),"+
                    "('lodging','Alloggio'),"+
                    "('meal_delivery','Consegna Pasti'),"+
                    "('meal_takeaway','Cibo da Asporto'),"+
                    "('mosque','Moschea'),"+
                    "('movie_rental','Noleggio Film'),"+
                    "('movie_theater','Cinema'),"+
                    "('moving_company','Traslochi'),"+
                    "('museum','Museo'),"+
                    "('night_club','Discoteca'),"+
                    "('painter','Pittore'),"+
                    "('park','Parco'),"+
                    "('parking','Parcheggio'),"+
                    "('pet_store','Negozio di Animali'),"+
                    "('pharmacy','Farmacia'),"+
                    "('physiotherapist','Fisioterapista'),"+
                    "('plumber','Idraulico'),"+
                    "('police','Stazione di Polizia'),"+
                    "('post_office','Ufficio Postale'),"+
                    "('primary_school','Scuola'),"+
                    "('real_estate_agency','Agenzia Immobiliare'),"+
                    "('restaurant','Ristorante'),"+
                    "('roofing_contractor','Impresa di Tetti'),"+
                    "('rv_park','Parco de Trailers'),"+
                    "('school','Scuola'),"+
                    "('secondary_school','Scuola Secondaria'),"+
                    "('shoe_store','Negozio di Scarpe'),"+
                    "('shopping_mall','Centro Commerciale'),"+
                    "('spa','Spa'),"+
                    "('stadium','Stadio'),"+
                    "('storage','Deposito'),"+
                    "('store','Negozio'),"+
                    "('subway_station','Stazione della Metropolitana'),"+
                    "('supermarket','Supermercato'),"+
                    "('synagogue','Sinagoga'),"+
                    "('taxi_stand','Posto Taxi'),"+
                    "('tourist_attraction','Attrazione Turistica'),"+
                    "('train_station','Stazione Ferroviaria'),"+
                    "('transit_station','Stazione di Transito'),"+
                    "('travel_agency','Agenzia di Viaggi'),"+
                    "('university','Università'),"+
                    "('veterinary_care','Veterinario'),"+
                    "('zoo','Zoo');"
    };

    private DatabaseSingleton() {
        Context ctx = MyApp.getAppContext();
        //Apre il database già esistente oppure crea un database nuovo
        db = ctx.openOrCreateDatabase(NOME_BANCO, Context.MODE_PRIVATE, null);

        //cerca le tabelle esistenti nel database = "show tables" di MySQL
        //SELECT * FROM sqlite_master WHERE type = "table"
        Cursor c = cerca("sqlite_master", null, "type = 'table'", "");

        //Crea le tabelle del database se è vuoto.
        //Tutti i database creati dal metodo openOrCreateDatabase() possiedono una tabella predefinita "android_metadata"
        if(c.getCount() == 1){
            for(int i = 0; i < SCRIPT_DATABASE_CREATE.length; i++){
                db.execSQL(SCRIPT_DATABASE_CREATE[i]);
            }
            Log.i("DATABASE", "Create le tabelle del database e le ha popolate.");
        }
        else{
            //Database già creato
            //dobbiamo garantire che gli hash delle risorse siano gli stessi

            //prima cerchiamo nel database i dati delle creature
            c = cerca("creatura", new String[]{"idCreatura,foto,icona"}, "", "");

            Class res = R.drawable.class;
            while (c.moveToNext()){
                int idCreatura = c.getColumnIndex("idCreatura");
                int fotoCol = c.getColumnIndex("foto");
                int iconaCol = c.getColumnIndex("icona");

                int id = c.getInt(idCreatura);
                int foto = c.getInt(fotoCol);
                int icona = c.getInt(iconaCol);
                try {
                    //recuperiamo le risorse di una creatura specifica
                    Field idFoto = res.getDeclaredField("creatura_"+id);
                    Field idIcona = res.getDeclaredField("creatura_"+id+"_icona");

                    //se l'hash dell'icona o della foto è diverso aggiorniamo l'hash del database
                    if(idFoto.getInt(null) != foto || idIcona.getInt(null) != icona ){
                        ContentValues ct = new ContentValues();
                        ct.put("foto", idFoto.getInt(null));
                        ct.put("icona", idIcona.getInt(null));
                        aggiorna("creatura", ct, "idCreatura="+id);
                    }

                } catch (NoSuchFieldException e) {
                    Log.e("DATABASE", "Immagine della creatura non esiste. idCreatura="+id);
                    e.printStackTrace();
                } catch (IllegalAccessException e) {
                    Log.e("DATABASE", "Il sistema non ha permesso l'accesso alla risorsa immagine della creatura. idCreatura="+id);
                    e.printStackTrace();
                }
            }
        }

        c.close();
        Log.i("DATABASE", "Aperta connessione con il database.");
    }

    public static DatabaseSingleton getInstance(){
        return INSTANCE;
    }

    //Inserisce un nuovo record
    public long inserisci(String tabela, ContentValues valores) {
        long id = db.insert(tabela, null, valores);

        Log.i("DATABASE", "Registrato record con id [" + id + "]");
        return id;
    }

    //Aggiorna i record
    public int aggiorna(String tabela, ContentValues valores, String where) {
        int count = db.update(tabela, valores, where, null);

        Log.i("DATABASE", "Aggiornati [" + count + "] registros");
        return count;
    }

    //Elimina i record
    public int cancella(String tabela, String where) {
        int count = db.delete(tabela, where, null);

        Log.i("DATABASE", "Eliminati [" + count + "] registros");
        return count;
    }

    //Cerca i record
    public Cursor cerca(String tabela, String colunas[], String where, String orderBy) {
        Cursor c;
        if(!where.equals(""))
            c = db.query(tabela, colunas, where, null, null, null, orderBy);
        else
            c = db.query(tabela, colunas, null, null, null, null, orderBy);

        Log.i("DATABASE", "Eseguita una ricerca e restituiti [" + c.getCount() + "] registros.");
        return c;
    }

    //Apre la connessione con il database
    public void apri() {
        Context ctx = MyApp.getAppContext();
        //Apre il database già esistente
        db = ctx.openOrCreateDatabase(NOME_BANCO, Context.MODE_PRIVATE, null);
        Log.i("DATABASE", "Aperta connessione con il database.");
    }

    //Chiude il database
    public void chiudi() {
        //chiude il database
        if (db != null) {
            db.close();
            Log.i("DATABASE", "Chiusa connessione con il database.");
        }
    }
}
