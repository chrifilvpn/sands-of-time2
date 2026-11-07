import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

/**
 * Trofei (obiettivi) del gioco: restano sbloccati anche tra una partita e l'altra.
 * Si vedono dal menu principale (TROFEI).
 */
public class Trofei
{
    public static final String[][] ELENCO = {
        // id, nome, descrizione
        {"primo_passo",   "Primo passo",       "Entra nella piramide"},
        {"artefatto",     "Il dono di Thot",   "Trova l'artefatto del tempo"},
        {"viaggiatore",   "Viaggiatore",       "Viaggia nel tempo 50 volte"},
        {"scarabeo",      "Primo scarabeo",    "Raccogli uno scarabeo d'oro"},
        {"collezionista", "Collezionista",     "Ogni scarabeo in una partita"},
        {"archeologo",    "Archeologo",        "Leggi tutte le tavolette"},
        {"intoccabile",   "Intoccabile",       "Un livello (dal 3) senza morire"},
        {"testardo",      "Testardo",          "Muori 25 volte"},
        {"fine",          "Il tesoro di Thot", "Finisci il gioco"},
        {"velocista",     "Velocista",         "Finisci in meno di 15 minuti"},
        {"immortale",     "Quasi immortale",   "Finisci con 5 morti o meno"},
    };

    private static final String NOME = "sands_of_time_trofei";
    private static int attive = 0;

    private static Preferences prefs()
    {
        return Gdx.app.getPreferences(NOME);
    }

    public static boolean sbloccato(String id)
    {
        return prefs().getBoolean("t_" + id, false);
    }

    public static int quantiSbloccati()
    {
        int n = 0;
        for (String[] t : ELENCO)
            if (sbloccato(t[0])) n++;
        return n;
    }

    /** Sblocca un trofeo; se è nuovo mostra la notifica sullo stage indicato. */
    public static void sblocca(String id, Stage ui)
    {
        if (sbloccato(id)) return;
        Preferences p = prefs();
        p.putBoolean("t_" + id, true);
        p.flush();
        Risorse.suona(Risorse.sTrofeo);
        if (ui != null)
            notifica(ui, nome(id));
    }

    /** Aumenta un contatore permanente (es. viaggi nel tempo totali) e restituisce il nuovo valore. */
    public static int incrementa(String contatore)
    {
        Preferences p = prefs();
        int v = p.getInteger("c_" + contatore, 0) + 1;
        p.putInteger("c_" + contatore, v);
        p.flush();
        return v;
    }

    /** Segna una tavoletta come letta; restituisce quante ne sono state lette in totale. */
    public static int segnaTavoletta(String id)
    {
        Preferences p = prefs();
        String lette = p.getString("tavolette", "");
        if (!("," + lette + ",").contains("," + id + ",")) {
            lette = lette.isEmpty() ? id : lette + "," + id;
            p.putString("tavolette", lette);
            p.flush();
        }
        return lette.isEmpty() ? 0 : lette.split(",").length;
    }

    public static String nome(String id)
    {
        for (String[] t : ELENCO)
            if (t[0].equals(id)) return t[1];
        return id;
    }

    /** Riquadro "TROFEO SBLOCCATO" che entra da sinistra in basso e poi sparisce. */
    public static void notifica(Stage ui, String nome)
    {
        Group g = new Group();
        g.setTouchable(Touchable.disabled);
        Label titolo = Risorse.etichetta("TROFEO SBLOCCATO", Risorse.fontPiccolo, new Color(1f, 0.82f, 0.35f, 1f));
        Label testo = Risorse.etichetta(nome, Risorse.fontNormale, Color.WHITE);
        float w = Math.max(titolo.getWidth(), testo.getWidth()) + 64;
        Image sfondo = new Image(Risorse.pannello);
        sfondo.setSize(w, 52);
        Image icona = new Image(Risorse.scarabeo);
        icona.setBounds(10, 12, 28, 28);
        titolo.setPosition(48, 28);
        testo.setPosition(48, 9);
        g.addActor(sfondo);
        g.addActor(icona);
        g.addActor(titolo);
        g.addActor(testo);
        g.setSize(w, 52);
        // le notifiche in contemporanea si impilano verso l'alto
        float y = 12 + (attive % 3) * 58;
        attive++;
        g.setPosition(-w - 10, y);
        ui.addActor(g);
        g.addAction(Actions.sequence(
                Actions.moveTo(12, y, 0.4f, Interpolation.swingOut),
                Actions.delay(3f),
                Actions.moveTo(-w - 10, y, 0.35f, Interpolation.pow2In),
                Actions.run(new Runnable() { public void run() { attive = Math.max(0, attive - 1); } }),
                Actions.removeActor()));
    }
}
