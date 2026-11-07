import java.util.HashSet;

/**
 * Statistiche della partita in corso (mostrate nella schermata finale).
 */
public class StatoPartita
{
    public static int morti = 0;
    public static float tempo = 0f;
    /** Scarabei raccolti in questa partita (id "livello-epoca-numero"). */
    public static final HashSet<String> scarabei = new HashSet<String>();

    /** Per il trofeo "Intoccabile": morti nel livello attuale (si azzera cambiando livello). */
    public static int livelloCorrente = 0;
    public static int mortiNelLivello = 0;
    /** Tempo passato nel livello attuale (per il miglior tempo di ogni livello). */
    public static float tempoLivello = 0f;
    /** Modalità difficile: viaggi nel tempo limitati. */
    public static boolean difficile = false;

    public static void nuova()
    {
        difficile = false;
        tempoLivello = 0f;
        morti = 0;
        tempo = 0f;
        scarabei.clear();
        livelloCorrente = 0;
        mortiNelLivello = 0;
    }

    public static void daSalvataggio()
    {
        morti = Save.caricaMorti();
        tempo = Save.caricaTempo();
        scarabei.clear();
        String s = Save.caricaScarabei();
        if (!s.isEmpty())
            for (String id : s.split(","))
                scarabei.add(id);
        livelloCorrente = 0;
        mortiNelLivello = 0;
        tempoLivello = 0f;
        difficile = Save.caricaDifficile();
    }

    public static String scarabeiComeTesto()
    {
        StringBuilder sb = new StringBuilder();
        for (String id : scarabei) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }

    /** Tempo formattato come mm:ss (oppure h:mm:ss). */
    public static String tempoFormattato()
    {
        return formatta(tempo);
    }

    public static String formatta(float secondi)
    {
        int s = (int) secondi;
        int h = s / 3600, m = (s / 60) % 60, sec = s % 60;
        if (h > 0)
            return h + ":" + due(m) + ":" + due(sec);
        return due(m) + ":" + due(sec);
    }

    private static String due(int n)
    {
        return (n < 10 ? "0" : "") + n;
    }
}
