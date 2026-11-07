import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;

/**
 * Menu principale.
 * - Il titolo luccica e ci cadono sopra granelli di sabbia.
 * - "CONTINUA" è disattivato se non c'è un salvataggio, e mostra il livello salvato.
 * - Si usa con mouse o tastiera (FRECCE + INVIO).
 */
public class MenuScreen extends BaseScreen
{
    /** Area del titolo dentro lo sfondo (in coordinate dello schermo). */
    private static final Rectangle AREA_TITOLO = new Rectangle(8, 318, 170, 160);

    private ListaBottoni bottoni;
    private TransizioneSabbia sabbia;
    private Particelle granelli;
    private float timerGranelli = 0;
    private boolean uscendo = false;

    @Override
    public void initialize()
    {
        // ---- sfondo ritagliato in proporzione 5:3 (senza deformarlo) ----
        TextureRegion ritaglio = new TextureRegion(Risorse.sfondoMenu, 0, 70, 1600, 960);
        Image sfondo = new Image(ritaglio);
        sfondo.setSize(LARGHEZZA, ALTEZZA);
        sfondo.setOrigin(LARGHEZZA / 2f, ALTEZZA * 0.4f);
        sfondo.setTouchable(Touchable.disabled);
        uiStage.addActor(sfondo);
        sfondo.addAction(Actions.forever(Actions.sequence(
                Actions.scaleTo(1.04f, 1.04f, 9f, Interpolation.sine),
                Actions.scaleTo(1f, 1f, 9f, Interpolation.sine))));

        // riflesso di luce sul titolo + granelli che cadono dalle lettere
        new Luccichio(uiStage, AREA_TITOLO);
        granelli = new Particelle(uiStage);

        // sabbia nel vento
        Particelle sabbiaVento = new Particelle(uiStage);
        sabbiaVento.avviaAmbiente(70, LARGHEZZA, ALTEZZA, new Color(1f, 0.85f, 0.55f, 1f));

        boolean web = Risorse.isWeb();
        int quanti = web ? 4 : 5;
        float altezzaBottone = 36, spazio = 8;
        float altezzaPannello = quanti * (altezzaBottone + spazio) + 22;

        Image pannello = new Image(Risorse.pannello);
        pannello.setBounds(462, 26, 296, altezzaPannello);
        pannello.setColor(1, 1, 1, 0.9f);
        pannello.setTouchable(Touchable.disabled);
        uiStage.addActor(pannello);

        boolean salvataggio = Save.esiste();
        String testoContinua = salvataggio ? "CONTINUA  (LIV. " + Save.caricaLivello() + ")" : "CONTINUA";

        bottoni = new ListaBottoni(uiStage);
        float y = 26 + altezzaPannello - 11 - altezzaBottone;
        bottoni.aggiungi(testoContinua, 480, y, 260, altezzaBottone, new Runnable() {
            public void run() { continua(); }
        });
        y -= altezzaBottone + spazio;
        bottoni.aggiungi("NUOVA PARTITA", 480, y, 260, altezzaBottone, new Runnable() {
            public void run() { nuovaPartita(); }
        });
        y -= altezzaBottone + spazio;
        bottoni.aggiungi("TROFEI  " + Trofei.quantiSbloccati() + "/" + Trofei.ELENCO.length, 480, y, 260, altezzaBottone,
                new Runnable() { public void run() { vai(new TrofeiScreen()); } });
        y -= altezzaBottone + spazio;
        bottoni.aggiungi("OPZIONI", 480, y, 260, altezzaBottone, new Runnable() {
            public void run() { vai(new OpzioniScreen()); }
        });
        if (!web) {   // nel browser non si può "chiudere" il gioco
            y -= altezzaBottone + spazio;
            bottoni.aggiungi("CHIUDI GIOCO", 480, y, 260, altezzaBottone, new Runnable() {
                public void run() { Gdx.app.exit(); }
            });
        }
        if (!salvataggio) {
            bottoni.setAttivo(0, false);
            bottoni.setSelezionato(1);
        }

        Label crediti = Risorse.etichetta("Gruppo Dugongo", Risorse.fontPiccolo, new Color(1f, 0.9f, 0.7f, 0.8f));
        crediti.setPosition(12, 10);
        uiStage.addActor(crediti);

        // record personali (se il gioco è già stato finito)
        if (Save.recordTempo() > 0) {
            Label record = Risorse.etichetta("RECORD  " + StatoPartita.formatta(Save.recordTempo())
                    + "   MORTI " + Save.recordMorti(), Risorse.fontPiccolo, new Color(1f, 0.82f, 0.35f, 0.9f));
            record.setPosition(12, 26);
            uiStage.addActor(record);
        }

        sabbia = new TransizioneSabbia(uiStage);
        sabbia.apri(0.9f);

        Risorse.attutisci(false);
        Risorse.musica(false);
    }

    private void continua()
    {
        StatoPartita.daSalvataggio();
        vai(new LevelScreen(Save.caricaLivello(), Save.caricaArtefatto(), Save.caricaPassato()));
    }

    private void nuovaPartita()
    {
        StatoPartita.nuova();
        Save.cancella();
        vai(new LevelScreen(1, false, false));
    }

    private void vai(final BaseScreen schermata)
    {
        if (uscendo) return;
        uscendo = true;
        sabbia.chiudi(0.7f, new Runnable() {
            public void run() { BaseGame.setActiveScreen(schermata); }
        });
    }

    @Override
    public boolean keyDown(int keycode)
    {
        // nel browser l'audio può partire solo dopo che il giocatore ha premuto qualcosa
        Risorse.musica(false);
        if (uscendo) return false;
        return bottoni.gestisciTasto(keycode);
    }

    @Override
    public void update(float dt)
    {
        if (Gdx.input.justTouched())
            Risorse.musica(false);

        // granelli che si staccano dal bordo inferiore del titolo
        timerGranelli -= dt;
        if (timerGranelli <= 0) {
            timerGranelli = MathUtils.random(0.04f, 0.12f);
            granelli.granello(MathUtils.random(AREA_TITOLO.x + 30, AREA_TITOLO.x + AREA_TITOLO.width - 30),
                    AREA_TITOLO.y + MathUtils.random(10f, 40f), -10,
                    new Color(1f, 0.85f, 0.5f, 0.9f));
        }
    }
}
