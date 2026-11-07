import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Salvataggio automatico della partita (all'inizio di ogni livello).
 * Oltre al livello e all'artefatto salva anche l'epoca, le morti e il tempo di gioco.
 */
public class Save
{
    private static final String PREF_NAME    = "sands_of_time_save";
    private static final String KEY_LEVEL    = "livello";
    private static final String KEY_ARTIFACT = "artefatto";
    private static final String KEY_PASSATO  = "passato";
    private static final String KEY_MORTI    = "morti";
    private static final String KEY_TEMPO    = "tempo";
    private static final String KEY_SCARABEI = "scarabei";
    private static final String RECORD       = "sands_of_time_record";

    private static Preferences prefs()
    {
        return Gdx.app.getPreferences(PREF_NAME);
    }

    public static void salva(int livello, boolean hasArtifact, boolean passato, int morti, float tempo)
    {
        Preferences p = prefs();
        p.putInteger(KEY_LEVEL, livello);
        p.putBoolean(KEY_ARTIFACT, hasArtifact);
        p.putBoolean(KEY_PASSATO, passato);
        p.putInteger(KEY_MORTI, morti);
        p.putFloat(KEY_TEMPO, tempo);
        p.putString(KEY_SCARABEI, StatoPartita.scarabeiComeTesto());
        p.putBoolean("difficile", StatoPartita.difficile);
        p.flush();
    }

    public static boolean caricaDifficile()
    {
        return prefs().getBoolean("difficile", false);
    }

    // ---------------- livelli sbloccati e miglior tempo per livello ----------------

    /** Il livello più avanzato mai raggiunto (per la selezione dei livelli). */
    public static int livelloMassimo()
    {
        return Math.max(1, Gdx.app.getPreferences(RECORD).getInteger("livelloMassimo", 1));
    }

    public static void registraLivello(int livello)
    {
        if (livello > Risorse.numeroLivelli) return;
        if (livello > livelloMassimo()) {
            Preferences p = Gdx.app.getPreferences(RECORD);
            p.putInteger("livelloMassimo", livello);
            p.flush();
        }
    }

    /** Miglior tempo di un livello in secondi, 0 se mai completato. */
    public static float tempoLivello(int livello)
    {
        return Gdx.app.getPreferences(RECORD).getFloat("tempo_" + livello, 0f);
    }

    public static void aggiornaTempoLivello(int livello, float tempo)
    {
        float vecchio = tempoLivello(livello);
        if (tempo > 0 && (vecchio == 0f || tempo < vecchio)) {
            Preferences p = Gdx.app.getPreferences(RECORD);
            p.putFloat("tempo_" + livello, tempo);
            p.flush();
        }
    }

    public static String caricaScarabei()
    {
        return prefs().getString(KEY_SCARABEI, "");
    }

    // ---------------- record (non vengono cancellati con il salvataggio) ----------------

    /** Miglior tempo (in secondi) o 0 se il gioco non è mai stato finito. */
    public static float recordTempo()
    {
        return Gdx.app.getPreferences(RECORD).getFloat("tempo", 0f);
    }

    /** Minor numero di morti o -1 se il gioco non è mai stato finito. */
    public static int recordMorti()
    {
        return Gdx.app.getPreferences(RECORD).getInteger("morti", -1);
    }

    public static int recordScarabei()
    {
        return Gdx.app.getPreferences(RECORD).getInteger("scarabei", 0);
    }

    /**
     * Confronta la partita appena finita con i record.
     * @return {nuovo record tempo, nuovo record morti, nuovo record scarabei}
     */
    public static boolean[] aggiornaRecord(float tempo, int morti, int scarabei)
    {
        Preferences p = Gdx.app.getPreferences(RECORD);
        boolean t = recordTempo() == 0f || tempo < recordTempo();
        boolean m = recordMorti() < 0 || morti < recordMorti();
        boolean s = scarabei > recordScarabei();
        if (t) p.putFloat("tempo", tempo);
        if (m) p.putInteger("morti", morti);
        if (s) p.putInteger("scarabei", scarabei);
        p.flush();
        return new boolean[] { t, m, s };
    }

    /** true se esiste una partita salvata valida (il livello salvato esiste ancora). */
    public static boolean esiste()
    {
        Preferences p = prefs();
        if (!p.contains(KEY_LEVEL)) return false;
        int l = p.getInteger(KEY_LEVEL, 1);
        return l >= 1 && l <= Risorse.numeroLivelli;
    }

    public static int caricaLivello()
    {
        int l = prefs().getInteger(KEY_LEVEL, 1);
        if (l < 1 || l > Risorse.numeroLivelli) l = 1;
        return l;
    }

    public static boolean caricaArtefatto()
    {
        return prefs().getBoolean(KEY_ARTIFACT, false);
    }

    public static boolean caricaPassato()
    {
        return prefs().getBoolean(KEY_PASSATO, false);
    }

    public static int caricaMorti()
    {
        return prefs().getInteger(KEY_MORTI, 0);
    }

    public static float caricaTempo()
    {
        return prefs().getFloat(KEY_TEMPO, 0f);
    }

    /** Cancella il salvataggio (es. dopo aver finito il gioco). */
    public static void cancella()
    {
        Preferences p = prefs();
        p.clear();
        p.flush();
    }
}
