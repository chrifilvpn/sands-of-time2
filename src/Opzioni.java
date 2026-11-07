import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Preferences;

/**
 * Opzioni del giocatore (salvate e ricaricate a ogni avvio).
 */
public class Opzioni
{
    private static final String NOME = "sands_of_time_opzioni";

    public static float volumeMusica = 0.45f;
    public static float volumeEffetti = 0.6f;
    public static boolean cronometro = false;

    public static void carica()
    {
        Preferences p = Gdx.app.getPreferences(NOME);
        volumeMusica  = p.getFloat("volumeMusica", 0.45f);
        volumeEffetti = p.getFloat("volumeEffetti", 0.6f);
        cronometro    = p.getBoolean("cronometro", false);
    }

    public static void salva()
    {
        Preferences p = Gdx.app.getPreferences(NOME);
        p.putFloat("volumeMusica", volumeMusica);
        p.putFloat("volumeEffetti", volumeEffetti);
        p.putBoolean("cronometro", cronometro);
        p.flush();
    }

    /** Percentuale da mostrare (es. "50%"). */
    public static String percento(float v)
    {
        return Math.round(v * 100) + "%";
    }
}
