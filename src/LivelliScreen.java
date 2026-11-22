import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

/**
 * Selezione dei livelli: si può giocare QUALSIASI livello, anche se non è ancora stato raggiunto,
 * e con la difficoltà che si vuole (normale, difficile, impossibile).
 * Sotto ogni livello c'è il miglior tempo.
 * FRECCE per scegliere (SU/GIÙ scorrono, SINISTRA/DESTRA anche; sul bottone della difficoltà la cambiano),
 * INVIO per giocare, ESC per tornare.
 */
public class LivelliScreen extends BaseScreen
{
    private ListaBottoni bottoni;
    private TransizioneSabbia sabbia;
    private boolean uscendo = false;
    private TextButton bottoneDifficolta;
    /** 0 = normale, 1 = difficile, 2 = impossibile (resta scelta finché il gioco è aperto). */
    private static int difficolta = 0;
    private static final String[] NOMI = { "NORMALE", "DIFFICILE", "IMPOSSIBILE" };

    @Override
    public void initialize()
    {
        Image sfondo = new Image(new TextureRegion(Risorse.sfondoMenu, 0, 70, 1600, 960));
        sfondo.setSize(LARGHEZZA, ALTEZZA);
        sfondo.setColor(0.4f, 0.35f, 0.3f, 1f);
        sfondo.setTouchable(Touchable.disabled);
        uiStage.addActor(sfondo);

        Image pannello = new Image(Risorse.pannello);
        pannello.setBounds(60, 40, 680, 400);
        pannello.setTouchable(Touchable.disabled);
        uiStage.addActor(pannello);

        Color oro = new Color(1f, 0.82f, 0.35f, 1f);
        Label titolo = Risorse.etichetta("LIVELLI", Risorse.fontGrande, oro);
        titolo.setPosition(Math.round(LARGHEZZA / 2f - titolo.getWidth() / 2f), 400);
        uiStage.addActor(titolo);

        int n = Risorse.numeroLivelli;
        int colonne = 5;
        float w = 100, h = 38, gapX = 22, gapY = 28;
        float x0 = LARGHEZZA / 2f - (colonne * w + (colonne - 1) * gapX) / 2f;
        float yTop = 292;

        bottoni = new ListaBottoni(uiStage);
        // bottone 0: la difficoltà (INVIO o SINISTRA/DESTRA per cambiarla)
        bottoneDifficolta = bottoni.aggiungi(testoDifficolta(), LARGHEZZA / 2f - 160, 350, 320, 34, new Runnable() {
            public void run() { cambiaDifficolta(1); }
        });
        bottoni.setLaterali(0,
                new Runnable() { public void run() { cambiaDifficolta(-1); } },
                new Runnable() { public void run() { cambiaDifficolta(1); } });
        for (int i = 1; i <= n; i++) {
            final int livello = i;
            int col = (i - 1) % colonne, riga = (i - 1) / colonne;
            float x = x0 + col * (w + gapX);
            float y = yTop - riga * (h + gapY);
            bottoni.aggiungi(String.valueOf(i), x, y, w, h, new Runnable() {
                public void run() { gioca(livello); }
            });
            float t = Save.tempoLivello(i);
            String sotto = t > 0 ? StatoPartita.formatta(t) : "--:--";
            Label l = Risorse.etichetta(sotto, Risorse.fontPiccolo, new Color(0.9f, 0.85f, 0.75f, 1f));
            l.setPosition(Math.round(x + w / 2f - l.getWidth() / 2f), y - 15);
            uiStage.addActor(l);

            // sinistra/destra spostano di uno, come su/giù
            final int indice = i;
            bottoni.setLaterali(indice,
                    new Runnable() { public void run() { sposta(indice, -1); } },
                    new Runnable() { public void run() { sposta(indice, 1); } });
        }
        bottoni.setSelezionato(Math.max(1, Math.min(Save.livelloMassimo(), n)));

        Label aiuto = Risorse.etichetta("Sotto ogni livello il tuo miglior tempo    ESC per tornare",
                Risorse.fontPiccolo, new Color(1f, 1f, 1f, 0.6f));
        aiuto.setPosition(Math.round(LARGHEZZA / 2f - aiuto.getWidth() / 2f), 50);
        uiStage.addActor(aiuto);

        sabbia = new TransizioneSabbia(uiStage);
        sabbia.apri(0.7f);
    }

    private void sposta(int indice, int direzione)
    {
        int nuovo = indice + direzione;
        if (nuovo >= 1 && nuovo <= Risorse.numeroLivelli)
            bottoni.setSelezionato(nuovo);
    }

    private static String testoDifficolta()
    {
        return "<  DIFFICOLTA': " + NOMI[difficolta] + "  >";
    }

    private void cambiaDifficolta(int verso)
    {
        difficolta = (difficolta + verso + NOMI.length) % NOMI.length;
        bottoneDifficolta.setText(testoDifficolta());
    }

    private void gioca(int livello)
    {
        if (uscendo) return;
        uscendo = true;
        StatoPartita.nuova();
        StatoPartita.difficile = difficolta == 1;
        StatoPartita.impossibile = difficolta == 2;
        StatoPartita.livelloIniziale = livello;   // in impossibile, morendo si ricomincia da qui
        // dal livello 3 in poi l'artefatto è già stato trovato
        final LevelScreen schermata = new LevelScreen(livello, livello >= 3, false);
        sabbia.chiudi(0.6f, new Runnable() {
            public void run() { BaseGame.setActiveScreen(schermata); }
        });
    }

    private void indietro()
    {
        if (uscendo) return;
        uscendo = true;
        sabbia.chiudi(0.6f, new Runnable() {
            public void run() { BaseGame.setActiveScreen(new MenuScreen()); }
        });
    }

    @Override
    public boolean keyDown(int keycode)
    {
        if (uscendo) return false;
        if (keycode == Input.Keys.ESCAPE) {
            indietro();
            return true;
        }
        return bottoni.gestisciTasto(keycode);
    }

    @Override
    public void update(float dt) {}
}
