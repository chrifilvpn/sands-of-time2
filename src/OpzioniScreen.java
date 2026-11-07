import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;

/**
 * Opzioni: volume di musica ed effetti, cronometro, schermo intero, cancellazione dei dati.
 * FRECCE SU/GIÙ per scegliere, SINISTRA/DESTRA (o INVIO/click) per cambiare il valore.
 */
public class OpzioniScreen extends BaseScreen
{
    private ListaBottoni bottoni;
    private TransizioneSabbia sabbia;
    private TextButton bMusica, bEffetti, bCronometro, bCancella;
    private boolean confermaCancella = false;
    private boolean uscendo = false;

    @Override
    public void initialize()
    {
        Image sfondo = new Image(new TextureRegion(Risorse.sfondoMenu, 0, 70, 1600, 960));
        sfondo.setSize(LARGHEZZA, ALTEZZA);
        sfondo.setColor(0.4f, 0.35f, 0.3f, 1f);
        sfondo.setTouchable(Touchable.disabled);
        uiStage.addActor(sfondo);

        Image pannello = new Image(Risorse.pannello);
        pannello.setBounds(190, 40, 420, 400);
        pannello.setTouchable(Touchable.disabled);
        uiStage.addActor(pannello);

        Label titolo = Risorse.etichetta("OPZIONI", Risorse.fontGrande, new Color(1f, 0.82f, 0.35f, 1f));
        titolo.setPosition(Math.round(LARGHEZZA / 2f - titolo.getWidth() / 2f), 390);
        uiStage.addActor(titolo);

        bottoni = new ListaBottoni(uiStage);
        float x = 230, w = 340, h = 38;
        bMusica = bottoni.aggiungi("", x, 324, w, h, new Runnable() { public void run() { cambiaMusica(0.1f, true); } });
        bottoni.setLaterali(0, new Runnable() { public void run() { cambiaMusica(-0.1f, false); } },
                               new Runnable() { public void run() { cambiaMusica(0.1f, false); } });
        bEffetti = bottoni.aggiungi("", x, 278, w, h, new Runnable() { public void run() { cambiaEffetti(0.1f, true); } });
        bottoni.setLaterali(1, new Runnable() { public void run() { cambiaEffetti(-0.1f, false); } },
                               new Runnable() { public void run() { cambiaEffetti(0.1f, false); } });
        bCronometro = bottoni.aggiungi("", x, 232, w, h, new Runnable() { public void run() { alternaCronometro(); } });
        Runnable alterna = new Runnable() { public void run() { alternaCronometro(); } };
        bottoni.setLaterali(2, alterna, alterna);
        bottoni.aggiungi("SCHERMO INTERO  (F11)", x, 186, w, h, new Runnable() { public void run() { schermoIntero(); } });
        bCancella = bottoni.aggiungi("CANCELLA SALVATAGGIO", x, 140, w, h, new Runnable() { public void run() { cancella(); } });
        bottoni.aggiungi("INDIETRO", x, 74, w, h, new Runnable() { public void run() { indietro(); } });
        aggiornaTesti();

        Label aiuto = Risorse.etichetta("SINISTRA / DESTRA per regolare   ESC per tornare",
                Risorse.fontPiccolo, new Color(1f, 1f, 1f, 0.6f));
        aiuto.setPosition(Math.round(LARGHEZZA / 2f - aiuto.getWidth() / 2f), 50);
        uiStage.addActor(aiuto);

        sabbia = new TransizioneSabbia(uiStage);
        sabbia.apri(0.7f);
    }

    private void aggiornaTesti()
    {
        bMusica.setText("MUSICA   < " + Opzioni.percento(Opzioni.volumeMusica) + " >");
        bEffetti.setText("EFFETTI  < " + Opzioni.percento(Opzioni.volumeEffetti) + " >");
        bCronometro.setText("CRONOMETRO   " + (Opzioni.cronometro ? "SI" : "NO"));
        bCancella.setText(confermaCancella ? "SICURO? PREMI DI NUOVO" : "CANCELLA SALVATAGGIO");
    }

    /** Cambia di 0.1; con "giro" dopo il 100% riparte da 0 (per il click del mouse). */
    private static float passo(float v, float delta, boolean giro)
    {
        float n = Math.round((v + delta) * 10) / 10f;
        if (n > 1.001f) n = giro ? 0f : 1f;
        if (n < 0) n = 0;
        return n;
    }

    private void cambiaMusica(float delta, boolean giro)
    {
        Opzioni.volumeMusica = passo(Opzioni.volumeMusica, delta, giro);
        Opzioni.salva();
        Risorse.applicaVolumi();
        aggiornaTesti();
    }

    private void cambiaEffetti(float delta, boolean giro)
    {
        Opzioni.volumeEffetti = passo(Opzioni.volumeEffetti, delta, giro);
        Opzioni.salva();
        aggiornaTesti();
        Risorse.suona(Risorse.sMoneta);   // per sentire subito il nuovo volume
    }

    private void alternaCronometro()
    {
        Opzioni.cronometro = !Opzioni.cronometro;
        Opzioni.salva();
        aggiornaTesti();
    }

    private void schermoIntero()
    {
        if (Gdx.graphics.isFullscreen()) {
            if (!Risorse.isWeb()) Gdx.graphics.setWindowedMode(800, 480);
        } else {
            Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
    }

    private void cancella()
    {
        if (!confermaCancella) {
            confermaCancella = true;
        } else {
            confermaCancella = false;
            Save.cancella();
            bCancella.setText("SALVATAGGIO CANCELLATO");
            return;
        }
        aggiornaTesti();
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
