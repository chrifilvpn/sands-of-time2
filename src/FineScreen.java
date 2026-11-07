import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/**
 * Schermata finale: congratulazioni, statistiche, record personali e crediti.
 */
public class FineScreen extends BaseScreen
{
    private TransizioneSabbia sabbia;
    private boolean pronto = false, uscendo = false;
    private Particelle scintille;
    private float timer = 0;

    @Override
    public void initialize()
    {
        Image sfondo = new Image(BaseActor.getTexture("assets/StanzaTesoro.png"));
        sfondo.setSize(LARGHEZZA, ALTEZZA);
        sfondo.setColor(0.35f, 0.3f, 0.25f, 1f);   // scurito per leggere il testo
        uiStage.addActor(sfondo);
        sfondo.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) { torna(); }
        });

        scintille = new Particelle(uiStage);
        scintille.avviaAmbiente(60, LARGHEZZA, ALTEZZA, new Color(1f, 0.85f, 0.4f, 1f));

        // record: vanno calcolati prima di mostrarli
        int presi = StatoPartita.scarabei.size();
        boolean[] nuovi = Save.aggiornaRecord(StatoPartita.tempo, StatoPartita.morti, presi);

        Color oro = new Color(1f, 0.82f, 0.35f, 1f);
        Color verde = new Color(0.55f, 1f, 0.55f, 1f);
        aggiungi("CONGRATULAZIONI!", Risorse.fontTitolo, oro, 385, 0.3f);
        aggiungi("Hai trovato il tesoro di Thot", Risorse.fontNormale, Color.WHITE, 342, 0.9f);

        aggiungi("TEMPO      " + StatoPartita.tempoFormattato() + (nuovi[0] ? "   NUOVO RECORD!" : ""),
                Risorse.fontMedio, nuovi[0] ? verde : oro, 280, 1.5f);
        aggiungi("MORTI      " + StatoPartita.morti + (nuovi[1] ? "   NUOVO RECORD!" : ""),
                Risorse.fontMedio, nuovi[1] ? verde : oro, 250, 1.8f);
        aggiungi("SCARABEI   " + presi + "/" + Risorse.totaleScarabei,
                Risorse.fontMedio, oro, 220, 2.1f);
        aggiungi("Record:  " + StatoPartita.formatta(Save.recordTempo()) + "  -  " + Save.recordMorti() + " morti",
                Risorse.fontPiccolo, new Color(0.9f, 0.85f, 0.75f, 1f), 186, 2.4f);
        aggiungi("Un gioco del Gruppo Dugongo", Risorse.fontPiccolo, new Color(0.9f, 0.85f, 0.75f, 1f), 136, 2.8f);

        Label invio = aggiungi("Premi INVIO per tornare al menu", Risorse.fontPiccolo, Color.WHITE, 60, 3.4f);
        invio.addAction(Actions.sequence(Actions.delay(4f), Actions.forever(
                Actions.sequence(Actions.alpha(0.3f, 0.6f), Actions.alpha(1f, 0.6f)))));

        sabbia = new TransizioneSabbia(uiStage);
        sabbia.apri(1.2f);
        uiStage.addAction(Actions.sequence(Actions.delay(3.4f), Actions.run(new Runnable() {
            public void run() { pronto = true; }
        })));

        // trofei di fine gioco
        Trofei.sblocca("fine", uiStage);
        if (StatoPartita.tempo < 15 * 60) Trofei.sblocca("velocista", uiStage);
        if (StatoPartita.morti <= 5)      Trofei.sblocca("immortale", uiStage);
        if (presi >= Risorse.totaleScarabei) Trofei.sblocca("collezionista", uiStage);
        if (StatoPartita.difficile)       Trofei.sblocca("maestro", uiStage);

        Risorse.attutisci(false);
        Risorse.musica(false);
    }

    private Label aggiungi(String testo, BitmapFont font, Color colore, float y, float ritardo)
    {
        Label l = Risorse.etichetta(testo, font, colore);
        l.setPosition(Math.round(LARGHEZZA / 2f - l.getWidth() / 2f), y);
        l.getColor().a = 0;
        l.setTouchable(Touchable.disabled);
        l.addAction(Actions.sequence(Actions.delay(ritardo), Actions.fadeIn(0.8f)));
        uiStage.addActor(l);
        return l;
    }

    private void torna()
    {
        if (!pronto || uscendo) return;
        uscendo = true;
        Risorse.suona(Risorse.sClick);
        sabbia.chiudi(0.8f, new Runnable() {
            public void run() { BaseGame.setActiveScreen(new MenuScreen()); }
        });
    }

    @Override
    public boolean keyDown(int keycode)
    {
        if (keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE || keycode == Input.Keys.ESCAPE) {
            torna();
            return true;
        }
        return false;
    }

    @Override
    public void update(float dt)
    {
        timer += dt;
        if (timer > 0.7f) {
            timer = 0;
            scintille.esplosione(MathUtils.random(100f, 700f), MathUtils.random(200f, 420f), 18,
                    new Color(1f, 0.85f, 0.4f, 0.9f), 90f, 1.0f, 60f);
        }
    }
}
