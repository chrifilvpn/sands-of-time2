import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.maps.MapObject;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.Align;
import java.util.ArrayList;
import java.util.List;

/**
 * Schermata di un livello.
 *
 * Ogni livello è formato da mappaN_presente.tmx e (facoltativo) mappaN_passato.tmx.
 * Oggetti letti dalle mappe (per nome):
 *   solid        muri e pavimenti
 *   kill         trappole (morte al contatto)
 *   finish       uscita del livello
 *   start        punto di partenza
 *   artefatto    (o "object") oggetto da ispezionare per ottenere l'artefatto del tempo
 *   scarabeo     scarabeo d'oro da raccogliere
 *   geroglifico  tavoletta da leggere con E (proprietà "testo")
 *   torcia       punto in cui disegnare l'alone di luce di una torcia
 *
 * Proprietà della mappa (Mappa > Proprietà mappa in Tiled):
 *   dimensionePersonaggio  dimensione in pixel del personaggio (default 57.6)
 *   uscita                 "tocco" (default), "porta", "passato", "tesoro" (vedi README)
 *   titolo                 nome del livello mostrato all'inizio
 *   parallasse             es. 0.75: lo sfondo scorre più piano delle piattaforme
 *   ambiente               "deserto" = vento e passi sulla sabbia; altrimenti piramide (gocce, pietra)
 */
public class LevelScreen extends BaseScreen
{
    // ------------------------------------------------ parametri
    private final int livello;
    private boolean artefattoAlRiavvio;
    private final boolean passatoIniziale;

    // ------------------------------------------------ stato
    private boolean hasArtifact;
    private boolean isInPassato;
    private boolean pausa, gameOver, transizione, cutscene, cutsceneChiudibile, lettura;
    private boolean finaleInCorso = false;
    private float finaleTempo = 0f;
    private float cooldownTempo = 0f;
    private float tremolio = 0f;
    private float tempoAnimazione = 0f;
    private float camX, camY;
    private String tipoUscita, ambiente;

    // ------------------------------------------------ mondo
    private TilemapActor mappaPresente, mappaPassato;
    private Luci lucePresente, lucePassato, luciEffetti;
    private Luci.Luce bagliore;
    private Esploratore esploratore;
    private final ArrayList<Oggetto> solidiPresente   = new ArrayList<Oggetto>();
    private final ArrayList<Oggetto> solidiPassato    = new ArrayList<Oggetto>();
    private final ArrayList<Oggetto> trappolePresente = new ArrayList<Oggetto>();
    private final ArrayList<Oggetto> trappolePassato  = new ArrayList<Oggetto>();
    private Oggetto uscitaPresente, uscitaPassato, oggettoArtefatto;
    private Label etichettaInterazione;
    private Particelle particelleMondo, polvere;
    private float timerScintille = 0f, timerPasso = 0f, timerGocce = 3f, timerSabbia = 2f;
    private float sabbiaRestante = 0f, sabbiaX, sabbiaY, sabbiaMinY, timerGranello = 0f;

    /** Collezionabili (scarabei) e tavolette con i geroglifici. */
    private static class Scarabeo { Oggetto area; Image img; String id; boolean passato; }
    private static class Tavoletta { Oggetto area; Image img; String id; String testo; }
    private final ArrayList<Scarabeo> scarabei = new ArrayList<Scarabeo>();
    private final ArrayList<Tavoletta> tavolette = new ArrayList<Tavoletta>();
    private Tavoletta tavolettaVicina;

    /** Piattaforme che crollano, piattaforme mobili, leve e gabbie. */
    private static class Leva { Oggetto area; Image img; boolean passato; boolean tirata; }
    private static class Gabbia { Oggetto area; Image img; boolean passato; boolean aperta; }
    private final ArrayList<Crollo> crolli = new ArrayList<Crollo>();
    private final ArrayList<PiattaformaMobile> mobili = new ArrayList<PiattaformaMobile>();
    private final ArrayList<Leva> leve = new ArrayList<Leva>();
    private final ArrayList<Gabbia> gabbie = new ArrayList<Gabbia>();
    private final ArrayList<Oggetto> appoggi = new ArrayList<Oggetto>();
    private PiattaformaMobile suMobile = null;
    private Leva levaVicina;

    /** Modalità difficile: viaggi nel tempo limitati per ogni tentativo. */
    public static final int VIAGGI_DIFFICILE = 10;
    private int viaggiRimasti = -1;

    /** Ultime posizioni del personaggio (per la scia di fantasmi del viaggio nel tempo). */
    private static final int STORIA = 24;
    private final float[] storiaX = new float[STORIA], storiaY = new float[STORIA];
    private final TextureRegion[] storiaFrame = new TextureRegion[STORIA];
    private final boolean[] storiaSinistra = new boolean[STORIA];
    private int storiaIndice = 0, storiaQuante = 0;

    private static final Color SABBIA = new Color(0.95f, 0.8f, 0.55f, 0.9f);
    private static final Color POLVERE_PRESENTE = new Color(1f, 0.85f, 0.6f, 1f);
    private static final Color POLVERE_PASSATO = new Color(0.6f, 0.9f, 1f, 1f);
    private static final Color ORO = new Color(1f, 0.85f, 0.35f, 0.9f);
    private static final Color AZZURRO = new Color(0.55f, 0.9f, 1f, 0.9f);
    private static final Color GRANELLO = new Color(0.85f, 0.72f, 0.5f, 0.85f);

    // ------------------------------------------------ interfaccia
    private Image vignetta, flash;
    private TransizioneSabbia sabbia;
    private Image iconaArtefatto, iconaScarabeo;
    private Label tastoT, hudLivello, hudEpoca, hudScarabei, cronometro;
    private Label messaggio, titoloLivello, sottotitoloLivello;
    private Image ingranaggio;

    private Image cutOverlay, cutImmagine;
    private Label cutTitolo, cutSotto;

    private Image letOverlay, letPannello, letIcona;
    private Label letTitolo, letTesto, letSotto;

    private Image pausaOverlay, pausaPannello;
    private Label pausaTitolo;
    private final ArrayList<Label> pausaAiuto = new ArrayList<Label>();
    private Image messaggioSfondo;
    private ListaBottoni pausaBottoni;

    private Image goOverlay;
    private Label goTitolo, goSotto;
    private ListaBottoni goBottoni;

    public LevelScreen(int livello, boolean hasArtifact, boolean passato)
    {
        this.livello = livello;
        this.artefattoAlRiavvio = hasArtifact;
        this.passatoIniziale = passato;
    }

    // =====================================================================
    // Creazione del livello
    // =====================================================================

    @Override
    public void initialize()
    {
        hasArtifact = artefattoAlRiavvio;
        if (StatoPartita.livelloCorrente != livello) {
            StatoPartita.livelloCorrente = livello;
            StatoPartita.mortiNelLivello = 0;
            StatoPartita.tempoLivello = 0f;
        }
        viaggiRimasti = StatoPartita.difficile ? VIAGGI_DIFFICILE : -1;
        Save.registraLivello(livello);

        // ---------- mappe e luci delle torce ----------
        mappaPresente = new TilemapActor("assets/mappa" + livello + "_presente.tmx", mainStage);
        lucePresente = new Luci(mainStage);
        String filePassato = "assets/mappa" + livello + "_passato.tmx";
        if (Gdx.files.internal(filePassato).exists()) {
            mappaPassato = new TilemapActor(filePassato, mainStage);
            lucePassato = new Luci(mainStage);
        }
        caricaTorce(mappaPresente, lucePresente);
        if (mappaPassato != null) caricaTorce(mappaPassato, lucePassato);

        tipoUscita = mappaPresente.getProprietaTesto("uscita", "tocco");
        ambiente = mappaPresente.getProprietaTesto("ambiente", "piramide");
        float dimensione = mappaPresente.getProprietaNumero("dimensionePersonaggio", 57.6f);

        // ---------- oggetti ----------
        uscitaPresente = caricaOggetti(mappaPresente, solidiPresente, trappolePresente, false);
        if (mappaPassato != null)
            uscitaPassato = caricaOggetti(mappaPassato, solidiPassato, trappolePassato, true);

        // nel livello con uscita "passato" la porta del presente è chiusa: diventa un muro
        if (tipoUscita.equals("passato") && uscitaPresente != null) {
            Rectangle r = uscitaPresente.getRettangolo();
            solidiPresente.add(new Oggetto(r.x, r.y, r.width, r.height, Oggetto.Tipo.SOLIDO, mainStage));
            uscitaPresente.remove();
            uscitaPresente = null;
        }

        MapObject art = primo(mappaPresente, "artefatto");
        if (art == null) art = primo(mappaPresente, "object");
        if (art != null)
            oggettoArtefatto = Oggetto.daMappa(art, Oggetto.Tipo.INTERAZIONE, mainStage);

        caricaScarabei(mappaPresente, false);
        if (mappaPassato != null) caricaScarabei(mappaPassato, true);
        caricaTavolette(mappaPresente);
        caricaMeccaniche(mappaPresente, false);
        if (mappaPassato != null) caricaMeccaniche(mappaPassato, true);

        // ---------- epoca iniziale ----------
        boolean passato = passatoIniziale && mappaPassato != null;

        // ---------- personaggio ----------
        esploratore = new Esploratore(0, 0, mainStage, dimensione);
        MapObject start = primo(passato ? mappaPassato : mappaPresente, "start");
        if (start == null && mappaPassato != null)
            start = primo(passato ? mappaPresente : mappaPassato, "start");
        if (start != null) {
            Rectangle r = rettangolo(start);
            esploratore.posizionaPiedi(r.x + r.width / 2f, r.y);
        } else {
            esploratore.posizionaPiedi(100, 100);
        }

        particelleMondo = new Particelle(mainStage);
        luciEffetti = new Luci(mainStage);

        etichettaInterazione = Risorse.etichetta("", Risorse.fontPiccolo, new Color(1f, 0.9f, 0.3f, 1f));
        etichettaInterazione.setVisible(false);
        mainStage.addActor(etichettaInterazione);

        creaInterfaccia();

        // ---------- avvio ----------
        setTempo(passato);
        aggiornaCamera(0, true);
        mostraTitoloLivello();
        if (livello == 1)
            mostraMessaggio("FRECCE o A/D per muoverti  -  SPAZIO per saltare", 6f);

        Risorse.attutisci(false);
        Risorse.avviaAmbiente(ambiente);
        sabbia.apri(0.9f);

        Save.salva(livello, hasArtifact, passato, StatoPartita.morti, StatoPartita.tempo);
    }

    /** Legge solidi, trappole e uscita di una mappa. Restituisce l'uscita (o null). */
    private Oggetto caricaOggetti(TilemapActor mappa, List<Oggetto> solidi, List<Oggetto> trappole, boolean passato)
    {
        for (MapObject obj : mappa.getObjectList("solid"))
            solidi.add(Oggetto.daMappa(obj, Oggetto.Tipo.SOLIDO, mainStage));
        // piattaforme che crollano: sono solidi, ma i loro tile vengono "staccati" dalla mappa per poterli far cadere
        for (MapObject obj : mappa.getObjectList("crollo")) {
            Oggetto o = Oggetto.daMappa(obj, Oggetto.Tipo.SOLIDO, mainStage);
            solidi.add(o);
            com.badlogic.gdx.scenes.scene2d.Group g = mappa.ritagliaTile(o.getRettangolo());
            g.setTouchable(Touchable.disabled);
            mainStage.addActor(g);
            crolli.add(new Crollo(o, g, passato));
        }
        for (MapObject obj : mappa.getObjectList("kill"))
            trappole.add(Oggetto.daMappa(obj, Oggetto.Tipo.TRAPPOLA, mainStage));
        MapObject fine = primo(mappa, "finish");
        return (fine == null) ? null : Oggetto.daMappa(fine, Oggetto.Tipo.USCITA, mainStage);
    }

    private void caricaTorce(TilemapActor mappa, Luci luci)
    {
        for (MapObject obj : mappa.getObjectList("torcia")) {
            Rectangle r = rettangolo(obj);
            luci.torcia(r.x + r.width / 2f, r.y + r.height / 2f);
        }
    }

    private void caricaScarabei(TilemapActor mappa, boolean passato)
    {
        int n = 0;
        for (MapObject obj : mappa.getObjectList("scarabeo")) {
            String id = livello + "-" + (passato ? "pa" : "pr") + "-" + (n++);
            if (StatoPartita.scarabei.contains(id)) continue;   // già raccolto
            Scarabeo s = new Scarabeo();
            s.id = id;
            s.passato = passato;
            s.area = Oggetto.daMappa(obj, Oggetto.Tipo.INTERAZIONE, mainStage);
            s.img = new Image(Risorse.scarabeo);
            s.img.setBounds(s.area.getX(), s.area.getY(), s.area.getWidth(), s.area.getHeight());
            s.img.setTouchable(Touchable.disabled);
            s.img.addAction(Actions.forever(Actions.sequence(
                    Actions.moveBy(0, 4, 0.6f, Interpolation.sine),
                    Actions.moveBy(0, -4, 0.6f, Interpolation.sine))));
            mainStage.addActor(s.img);
            scarabei.add(s);
        }
    }

    private void caricaTavolette(TilemapActor mappa)
    {
        int n = 0;
        for (MapObject obj : mappa.getObjectList("geroglifico")) {
            Tavoletta t = new Tavoletta();
            t.id = livello + "-" + (n++);
            Object testo = obj.getProperties().get("testo");
            t.testo = (testo == null) ? "..." : testo.toString();
            t.area = Oggetto.daMappa(obj, Oggetto.Tipo.INTERAZIONE, mainStage);
            t.img = new Image(Risorse.tavoletta);
            t.img.setBounds(t.area.getX(), t.area.getY(), t.area.getWidth(), t.area.getHeight());
            t.img.setTouchable(Touchable.disabled);
            mainStage.addActor(t.img);
            tavolette.add(t);
        }
    }

    /** Piattaforme mobili, leve e gabbie di una mappa. */
    private void caricaMeccaniche(TilemapActor mappa, boolean passato)
    {
        for (MapObject obj : mappa.getObjectList("mobile")) {
            Oggetto area = Oggetto.daMappa(obj, Oggetto.Tipo.SOLIDO, mainStage);
            Image img = new Image(Risorse.piattaforma);
            img.setSize(area.getWidth(), 20);
            img.setTouchable(Touchable.disabled);
            mainStage.addActor(img);
            mobili.add(new PiattaformaMobile(area, img, passato,
                    proprieta(obj, "dx", 120), proprieta(obj, "dy", 0), proprieta(obj, "durata", 2.5f)));
        }
        for (MapObject obj : mappa.getObjectList("gabbia")) {
            Gabbia g = new Gabbia();
            g.passato = passato;
            g.area = Oggetto.daMappa(obj, Oggetto.Tipo.INTERAZIONE, mainStage);
            g.img = new Image(Risorse.gabbia);
            g.img.setBounds(g.area.getX(), g.area.getY(), g.area.getWidth(), g.area.getHeight());
            g.img.setTouchable(Touchable.disabled);
            mainStage.addActor(g.img);
            gabbie.add(g);
        }
        for (MapObject obj : mappa.getObjectList("leva")) {
            Leva l = new Leva();
            l.passato = passato;
            l.area = Oggetto.daMappa(obj, Oggetto.Tipo.INTERAZIONE, mainStage);
            l.img = new Image(Risorse.leva1);
            l.img.setBounds(l.area.getX(), l.area.getY(), l.area.getWidth(), l.area.getHeight());
            l.img.setTouchable(Touchable.disabled);
            mainStage.addActor(l.img);
            leve.add(l);
        }
    }

    private static float proprieta(MapObject obj, String nome, float predefinito)
    {
        Object v = obj.getProperties().get(nome);
        if (v == null) return predefinito;
        if (v instanceof Number) return ((Number) v).floatValue();
        try { return Float.parseFloat(v.toString()); } catch (NumberFormatException e) { return predefinito; }
    }

    /** Rettangolo di un oggetto della mappa (senza lasciare attori sullo stage). */
    private Rectangle rettangolo(MapObject obj)
    {
        Oggetto o = Oggetto.daMappa(obj, Oggetto.Tipo.INTERAZIONE, mainStage);
        Rectangle r = o.getRettangolo();
        o.remove();
        return r;
    }

    private static MapObject primo(TilemapActor mappa, String nome)
    {
        ArrayList<MapObject> l = mappa.getObjectList(nome);
        return l.isEmpty() ? null : l.get(0);
    }

    // =====================================================================
    // Interfaccia
    // =====================================================================

    private Image rettangolo(Color colore, float alpha)
    {
        Image img = new Image(Risorse.bianco);
        img.setSize(LARGHEZZA, ALTEZZA);
        img.setColor(colore.r, colore.g, colore.b, alpha);
        img.setTouchable(Touchable.disabled);
        uiStage.addActor(img);
        return img;
    }

    private Label etichettaUi(String testo, com.badlogic.gdx.graphics.g2d.BitmapFont font, Color colore)
    {
        Label l = Risorse.etichetta(testo, font, colore);
        l.setTouchable(Touchable.disabled);
        uiStage.addActor(l);
        return l;
    }

    private static void centra(Label l, float y)
    {
        l.pack();
        l.setPosition(Math.round(LARGHEZZA / 2f - l.getWidth() / 2f), Math.round(y));
    }

    private void creaInterfaccia()
    {
        Color oro = new Color(1f, 0.82f, 0.35f, 1f);

        // effetti a schermo intero
        vignetta = new Image(Risorse.vignetta);
        vignetta.setSize(LARGHEZZA, ALTEZZA);
        vignetta.setTouchable(Touchable.disabled);
        vignetta.getColor().a = 0;
        vignetta.setVisible(false);
        uiStage.addActor(vignetta);
        polvere = new Particelle(uiStage);
        polvere.avviaAmbiente(ambiente.equals("deserto") ? 70 : 40, LARGHEZZA, ALTEZZA, POLVERE_PRESENTE);
        flash = rettangolo(Color.WHITE, 0f);

        // HUD in alto a sinistra
        hudLivello = etichettaUi("LIVELLO " + livello + "/" + Risorse.numeroLivelli, Risorse.fontPiccolo, oro);
        hudLivello.setPosition(12, ALTEZZA - 24);
        hudEpoca = etichettaUi("", Risorse.fontPiccolo, Color.WHITE);
        hudEpoca.setPosition(12, ALTEZZA - 42);
        hudEpoca.setVisible(mappaPassato != null);
        iconaScarabeo = new Image(Risorse.scarabeo);
        iconaScarabeo.setBounds(10, ALTEZZA - (mappaPassato != null ? 68 : 50), 18, 18);
        iconaScarabeo.setOrigin(9, 9);
        iconaScarabeo.setTouchable(Touchable.disabled);
        uiStage.addActor(iconaScarabeo);
        hudScarabei = etichettaUi("", Risorse.fontPiccolo, oro);
        hudScarabei.setPosition(32, iconaScarabeo.getY() + 4);
        aggiornaHudScarabei();

        // cronometro (si attiva dalle opzioni)
        cronometro = etichettaUi("00:00", Risorse.fontNormale, Color.WHITE);
        cronometro.setVisible(Opzioni.cronometro || StatoPartita.difficile);

        // artefatto in basso a destra (luminoso nel passato)
        iconaArtefatto = new Image(Risorse.artefattoSpento);
        iconaArtefatto.setSize(56, 56);
        iconaArtefatto.setPosition(LARGHEZZA - 56 - 12, 12);
        iconaArtefatto.setOrigin(28, 28);
        iconaArtefatto.setTouchable(Touchable.disabled);
        iconaArtefatto.setVisible(hasArtifact);
        uiStage.addActor(iconaArtefatto);
        tastoT = etichettaUi(testoTastoT(), Risorse.fontPiccolo, oro);
        tastoT.setPosition(LARGHEZZA - 56 - 18 - tastoT.getWidth(), 28);
        tastoT.setVisible(hasArtifact && mappaPassato != null);

        // ingranaggio (pausa) in alto a destra
        ingranaggio = new Image(Risorse.ingranaggio);
        ingranaggio.setSize(36, 36);
        ingranaggio.setOrigin(18, 18);
        ingranaggio.setPosition(LARGHEZZA - 36 - 10, ALTEZZA - 36 - 10);
        ingranaggio.setColor(1, 1, 1, 0.7f);
        uiStage.addActor(ingranaggio);
        ingranaggio.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (puoMettereInPausa()) alternaPausa();
            }
        });

        // titolo del livello e messaggi
        titoloLivello = etichettaUi("LIVELLO " + livello, Risorse.fontTitolo, oro);
        centra(titoloLivello, ALTEZZA * 0.62f);
        titoloLivello.getColor().a = 0;
        sottotitoloLivello = etichettaUi(mappaPresente.getProprietaTesto("titolo", "").toUpperCase(),
                Risorse.fontMedio, Color.WHITE);
        centra(sottotitoloLivello, ALTEZZA * 0.62f - 34);
        sottotitoloLivello.getColor().a = 0;

        messaggioSfondo = rettangolo(Color.BLACK, 0f);
        messaggio = etichettaUi("", Risorse.fontNormale, Color.WHITE);
        messaggio.getColor().a = 0;

        // ---------- cutscene artefatto ----------
        cutOverlay = rettangolo(Color.BLACK, 0f);
        cutOverlay.setVisible(false);
        cutTitolo = etichettaUi("Hai trovato un artefatto misterioso", Risorse.fontMedio, oro);
        centra(cutTitolo, ALTEZZA * 0.74f);
        cutTitolo.setVisible(false);
        cutImmagine = new Image(Risorse.artefattoAcceso);
        cutImmagine.setSize(150, 150);
        cutImmagine.setOrigin(75, 75);
        cutImmagine.setPosition(LARGHEZZA / 2f - 75, ALTEZZA * 0.30f);
        cutImmagine.setTouchable(Touchable.disabled);
        cutImmagine.setVisible(false);
        uiStage.addActor(cutImmagine);
        cutSotto = etichettaUi("Premi E per continuare", Risorse.fontPiccolo, Color.WHITE);
        centra(cutSotto, ALTEZZA * 0.17f);
        cutSotto.setVisible(false);

        // ---------- lettura tavoletta ----------
        letOverlay = rettangolo(Color.BLACK, 0.6f);
        letPannello = new Image(Risorse.pannello);
        letPannello.setBounds(120, 130, 560, 230);
        letPannello.setTouchable(Touchable.disabled);
        uiStage.addActor(letPannello);
        letIcona = new Image(Risorse.tavoletta);
        letIcona.setBounds(LARGHEZZA / 2f - 20, 300, 40, 50);
        letIcona.setTouchable(Touchable.disabled);
        uiStage.addActor(letIcona);
        letTitolo = etichettaUi("TAVOLETTA ANTICA", Risorse.fontNormale, oro);
        centra(letTitolo, 278);
        letTesto = etichettaUi("", Risorse.fontNormale, new Color(0.95f, 0.9f, 0.8f, 1f));
        letTesto.setAlignment(Align.center);
        letSotto = etichettaUi("Premi E per chiudere", Risorse.fontPiccolo, new Color(1f, 1f, 1f, 0.7f));
        centra(letSotto, 148);
        mostraLettura(false);

        // ---------- pausa ----------
        pausaOverlay = rettangolo(Color.BLACK, 0.6f);
        pausaPannello = new Image(Risorse.pannello);
        pausaPannello.setBounds(200, 30, 400, 420);
        pausaPannello.setTouchable(Touchable.disabled);
        uiStage.addActor(pausaPannello);
        pausaTitolo = etichettaUi("PAUSA", Risorse.fontGrande, oro);
        centra(pausaTitolo, 405);
        pausaBottoni = new ListaBottoni(uiStage);
        pausaBottoni.aggiungi("RIPRENDI",           270, 340, 260, 36, new Runnable() { public void run() { alternaPausa(); } });
        pausaBottoni.aggiungi("RICOMINCIA LIVELLO", 270, 296, 260, 36, new Runnable() { public void run() { riprova(); } });
        pausaBottoni.aggiungi("MENU PRINCIPALE",    270, 252, 260, 36, new Runnable() { public void run() { vaiAlMenu(); } });
        if (!Risorse.isWeb())
            pausaBottoni.aggiungi("CHIUDI GIOCO",   270, 208, 260, 36, new Runnable() { public void run() { Gdx.app.exit(); } });
        String[][] comandi = {
                {"FRECCE / A D", "muoviti"},
                {"SPAZIO / W", "salta"},
                {"E", "interagisci / leggi"},
                {"T", "viaggia nel tempo"},
                {"ESC / P", "pausa"},
                {"M", "audio on/off"},
                {"F11", "schermo intero"}
        };
        Color coloreTasto = new Color(1f, 0.82f, 0.35f, 1f);
        Color coloreDescr = new Color(0.9f, 0.85f, 0.75f, 1f);
        float yRiga = Risorse.isWeb() ? 200 : 178;
        for (String[] c : comandi) {
            Label tasto = etichettaUi(c[0], Risorse.fontPiccolo, coloreTasto);
            tasto.setPosition(222, yRiga);
            Label descr = etichettaUi(c[1], Risorse.fontPiccolo, coloreDescr);
            descr.setPosition(400, yRiga);
            pausaAiuto.add(tasto);
            pausaAiuto.add(descr);
            yRiga -= 17;
        }
        mostraPausa(false);

        // ---------- game over ----------
        goOverlay = rettangolo(new Color(0.15f, 0f, 0f, 1f), 0.8f);
        goTitolo = etichettaUi("GAME OVER", Risorse.fontTitolo, new Color(1f, 0.25f, 0.2f, 1f));
        centra(goTitolo, 300);
        goSotto = etichettaUi("", Risorse.fontPiccolo, new Color(0.9f, 0.8f, 0.8f, 1f));
        goBottoni = new ListaBottoni(uiStage);
        goBottoni.aggiungi("RIPROVA",         280, 200, 240, 38, new Runnable() { public void run() { riprova(); } });
        goBottoni.aggiungi("MENU PRINCIPALE", 280, 152, 240, 38, new Runnable() { public void run() { vaiAlMenu(); } });
        mostraGameOverUi(false);

        // transizione a clessidra (sopra a tutto)
        sabbia = new TransizioneSabbia(uiStage);
    }

    private String testoTastoT()
    {
        return viaggiRimasti >= 0 ? "[T] TEMPO x" + viaggiRimasti : "[T] TEMPO";
    }

    private void aggiornaHudScarabei()
    {
        hudScarabei.setText(StatoPartita.scarabei.size() + "/" + Risorse.totaleScarabei);
        hudScarabei.pack();
    }

    private void mostraTitoloLivello()
    {
        titoloLivello.addAction(Actions.sequence(Actions.delay(0.6f), Actions.fadeIn(0.5f),
                Actions.delay(1.6f), Actions.fadeOut(0.6f)));
        sottotitoloLivello.addAction(Actions.sequence(Actions.delay(0.8f), Actions.fadeIn(0.5f),
                Actions.delay(1.4f), Actions.fadeOut(0.6f)));
    }

    /** Messaggio temporaneo in alto, su una striscia scura. */
    private void mostraMessaggio(String testo, float secondi)
    {
        messaggio.setText(testo);
        centra(messaggio, ALTEZZA - 92);
        messaggioSfondo.setBounds(messaggio.getX() - 14, messaggio.getY() - 9,
                messaggio.getWidth() + 28, messaggio.getHeight() + 18);
        messaggio.clearActions();
        messaggio.getColor().a = 0;
        messaggio.addAction(Actions.sequence(Actions.fadeIn(0.3f), Actions.delay(secondi), Actions.fadeOut(0.6f)));
        messaggioSfondo.clearActions();
        messaggioSfondo.getColor().a = 0;
        messaggioSfondo.addAction(Actions.sequence(Actions.alpha(0.55f, 0.3f), Actions.delay(secondi), Actions.fadeOut(0.6f)));
    }

    private void mostraPausa(boolean v)
    {
        pausaOverlay.setVisible(v);
        pausaPannello.setVisible(v);
        pausaTitolo.setVisible(v);
        for (Label l : pausaAiuto) l.setVisible(v);
        if (v) {
            pausaOverlay.toFront();
            pausaPannello.toFront();
            pausaTitolo.toFront();
            for (Label l : pausaAiuto) l.toFront();
            pausaBottoni.setSelezionato(0);
        }
        pausaBottoni.setVisibile(v);
    }

    private void mostraLettura(boolean v)
    {
        Actor[] parti = { letOverlay, letPannello, letIcona, letTitolo, letTesto, letSotto };
        for (Actor a : parti) {
            a.setVisible(v);
            if (v) {
                a.toFront();
                a.getColor().a = (a == letOverlay) ? 0 : 0;
                a.clearActions();
                a.addAction(Actions.alpha(a == letOverlay ? 0.6f : 1f, 0.3f));
            }
        }
    }

    private void mostraGameOverUi(boolean v)
    {
        goOverlay.setVisible(v);
        goTitolo.setVisible(v);
        goSotto.setVisible(v);
        if (v) {
            goOverlay.toFront();
            goTitolo.toFront();
            goSotto.toFront();
            goBottoni.setSelezionato(0);
        }
        goBottoni.setVisibile(v);
    }

    // =====================================================================
    // Viaggio nel tempo
    // =====================================================================

    private void setTempo(boolean passato)
    {
        isInPassato = passato;

        for (Oggetto o : solidiPresente)   o.setEnable(!passato);
        for (Oggetto o : trappolePresente) o.setEnable(!passato);
        for (Oggetto o : solidiPassato)    o.setEnable(passato);
        for (Oggetto o : trappolePassato)  o.setEnable(passato);
        if (uscitaPresente != null) uscitaPresente.setEnable(!passato);
        if (uscitaPassato != null)  uscitaPassato.setEnable(passato);

        mappaPresente.setVisible(!passato);
        lucePresente.setVisible(!passato);
        if (mappaPassato != null) {
            mappaPassato.setVisible(passato);
            lucePassato.setVisible(passato);
        }
        for (Scarabeo s : scarabei) s.img.setVisible(s.passato == passato);
        for (Crollo c : crolli) {
            c.grafica.setVisible(c.passato == passato);
            c.solido.setEnable(c.passato == passato && !c.isCaduta());
        }
        for (PiattaformaMobile m : mobili) m.img.setVisible(m.passato == passato);
        for (Leva l : leve) l.img.setVisible(l.passato == passato);
        for (Gabbia g : gabbie) g.img.setVisible(g.passato == passato && !g.aperta);
        suMobile = null;
        for (Tavoletta t : tavolette) t.img.setVisible(!passato);

        vignetta.clearActions();
        if (passato) {
            vignetta.setVisible(true);
            vignetta.addAction(Actions.fadeIn(0.3f));
        } else {
            vignetta.addAction(Actions.sequence(Actions.fadeOut(0.3f), Actions.visible(false)));
        }
        polvere.setColoreAmbiente(passato ? POLVERE_PASSATO : POLVERE_PRESENTE);

        Texture icona = passato ? Risorse.artefattoAcceso : Risorse.artefattoSpento;
        iconaArtefatto.setDrawable(new TextureRegionDrawable(new TextureRegion(icona)));

        hudEpoca.setText(passato ? "PASSATO" : "PRESENTE");
        hudEpoca.pack();
        hudEpoca.setColor(passato ? new Color(0.55f, 0.85f, 1f, 1f) : new Color(1f, 0.85f, 0.55f, 1f));

        Risorse.musica(passato);
    }

    private void viaggiaNelTempo()
    {
        if (!hasArtifact || mappaPassato == null || cooldownTempo > 0) return;
        if (viaggiRimasti == 0) {
            Risorse.suona(Risorse.sBloccato);
            cooldownTempo = 0.3f;
            mostraMessaggio("Viaggi finiti! Ricomincia il livello dal menu di pausa", 2.5f);
            return;
        }
        boolean destinazione = !isInPassato;

        // se nell'altra epoca qui c'è un muro, il viaggio è bloccato
        if (bloccato(destinazione)) {
            Risorse.suona(Risorse.sBloccato);
            lampo(new Color(1f, 0.2f, 0.1f, 1f), 0.35f, 0.25f);
            tremolio = 3f;
            cooldownTempo = 0.3f;
            mostraMessaggio("Qualcosa ti blocca nell'altra epoca!", 2f);
            return;
        }

        Color coloreVecchio = isInPassato ? AZZURRO : ORO;
        Color coloreNuovo = destinazione ? AZZURRO : ORO;
        creaScia(coloreVecchio);
        setTempo(destinazione);
        ondaTemporale(coloreNuovo);
        particelleMondo.esplosione(esploratore.getCentroX(), esploratore.getCentroY(), 30,
                coloreNuovo, 140f, 0.7f, 0f);
        cooldownTempo = 0.45f;
        tremolio = 4f;
        Risorse.suona(Risorse.sTempo);
        if (viaggiRimasti > 0) {
            viaggiRimasti--;
            tastoT.setText(testoTastoT());
            tastoT.pack();
            tastoT.setPosition(LARGHEZZA - 56 - 18 - tastoT.getWidth(), 28);
        }
        lampo(destinazione ? new Color(0.6f, 0.85f, 1f, 1f) : new Color(1f, 0.85f, 0.5f, 1f), 0.45f, 0.4f);
        iconaArtefatto.clearActions();
        iconaArtefatto.setScale(1f);
        iconaArtefatto.addAction(Actions.sequence(Actions.scaleTo(1.3f, 1.3f, 0.1f), Actions.scaleTo(1f, 1f, 0.25f)));

        if (Trofei.incrementa("viaggi") >= 50)
            Trofei.sblocca("viaggiatore", uiStage);
    }

    /** Lascia 3 "fantasmi" del personaggio nelle posizioni appena attraversate. */
    private void creaScia(Color colore)
    {
        int[] passi = { 0, 5, 10 };
        float alpha = 0.6f;
        for (int k : passi) {
            if (k >= storiaQuante) break;
            int i = ((storiaIndice - 1 - k) % STORIA + STORIA) % STORIA;
            new Fantasma(mainStage, storiaFrame[i], storiaX[i], storiaY[i], esploratore.getDimensione(),
                    storiaSinistra[i], colore, alpha, 0.6f + k * 0.03f);
            alpha -= 0.15f;
        }
        esploratore.toFront();
        particelleMondo.toFront();
        luciEffetti.toFront();
    }

    /** Anello di luce che si allarga dal personaggio. */
    private void ondaTemporale(Color colore)
    {
        Image onda = new Image(Risorse.anello);
        float d = 80;
        onda.setSize(d, d);
        onda.setOrigin(d / 2f, d / 2f);
        onda.setPosition(esploratore.getCentroX() - d / 2f, esploratore.getCentroY() - d / 2f);
        onda.setColor(colore.r, colore.g, colore.b, 0.9f);
        onda.setScale(0.2f);
        onda.setTouchable(Touchable.disabled);
        mainStage.addActor(onda);
        onda.addAction(Actions.sequence(
                Actions.parallel(Actions.scaleTo(9f, 9f, 0.6f, Interpolation.pow2Out), Actions.fadeOut(0.6f)),
                Actions.removeActor()));
    }

    private boolean bloccato(boolean passato)
    {
        Rectangle hb = esploratore.getHitbox(2f);
        for (Oggetto o : (passato ? solidiPassato : solidiPresente))
            if (!o.crollato && hb.overlaps(o.getRettangolo()))
                return true;
        return false;
    }

    private void lampo(Color c, float alpha, float durata)
    {
        flash.clearActions();
        flash.setColor(c.r, c.g, c.b, alpha);
        flash.addAction(Actions.fadeOut(durata));
    }

    // =====================================================================
    // Ciclo di gioco
    // =====================================================================

    @Override
    protected boolean isMondoFermo()
    {
        return pausa || cutscene || lettura;
    }

    @Override
    public void update(float dt)
    {
        tempoAnimazione += dt;
        if (!pausa && !gameOver && !transizione) {
            StatoPartita.tempo += dt;
            StatoPartita.tempoLivello += dt;
        }
        cooldownTempo -= dt;
        if (cronometro.isVisible()) {
            cronometro.setText(StatoPartita.tempoFormattato());
            centra(cronometro, ALTEZZA - 26);
        }

        if (finaleInCorso) {
            aggiornaFinale(dt);
            aggiornaCamera(dt, false);
            return;
        }

        // morte: aspetta la fine dell'animazione, poi mostra il Game Over
        if (esploratore.isDead()) {
            if (!gameOver && esploratore.isDeathFinished())
                mostraGameOver();
            aggiornaCamera(dt, false);
            return;
        }

        if (pausa || cutscene || lettura || transizione || gameOver) {
            aggiornaCamera(dt, false);
            return;
        }

        List<Oggetto> solidi = isInPassato ? solidiPassato : solidiPresente;

        // le piattaforme mobili si spostano e portano con sé chi ci sta sopra
        for (PiattaformaMobile m : mobili) m.aggiorna(dt);
        if (suMobile != null) esploratore.moveBy(suMobile.deltaX, suMobile.deltaY);

        esploratore.leggiInput(dt);
        esploratore.applyGravity(dt);
        esploratore.moveX(dt);
        collisioni(solidi, true);
        float piediPrima = esploratore.getPiediY();
        esploratore.moveY(dt);
        collisioni(solidi, false);
        atterraggioMobili(piediPrima);
        esploratore.restaNelMondo();

        appoggi.clear();
        appoggi.addAll(solidi);
        for (PiattaformaMobile m : mobili)
            if (m.passato == isInPassato) appoggi.add(m.area);
        if (esploratore.aggiornaStato(appoggi))
            Risorse.suona(Risorse.sSalto, 0.8f, MathUtils.random(0.95f, 1.05f));
        if (esploratore.isAppenaAtterrato()) {
            particelleMondo.sbuffo(esploratore.getCentroX(), esploratore.getPiediY(), SABBIA);
            suonaPasso(0.9f);
        }
        registraPosizione();
        aggiornaCrolli(dt);

        // trappole dell'epoca attuale
        for (Oggetto k : (isInPassato ? trappolePassato : trappolePresente)) {
            if (esploratore.overlaps(k)) {
                muori();
                return;
            }
        }

        raccogliScarabei();
        aggiornaInterazione();
        aggiornaAmbiente(dt);
        emettiScintille(dt);

        // uscita al contatto
        if (tipoUscita.equals("tocco") || tipoUscita.equals("passato")) {
            Oggetto u = uscitaAttiva();
            if (u != null && esploratore.overlaps(u))
                cambiaLivello();
        }

        aggiornaCamera(dt, false);
    }

    /** Ci si appoggia sulle piattaforme mobili solo cadendo da sopra. */
    private void atterraggioMobili(float piediPrima)
    {
        suMobile = null;
        if (esploratore.velocityVec.y > 0) return;
        Rectangle hb = esploratore.getHitbox(0);
        for (PiattaformaMobile m : mobili) {
            if (m.passato != isInPassato || !m.sovrapponeX(hb)) continue;
            float top = m.getTop();
            float topPrima = top - m.deltaY;
            float piedi = esploratore.getPiediY();
            if (piediPrima >= topPrima - 2f && piedi <= top + 0.5f) {
                esploratore.moveBy(0, top - piedi);
                esploratore.velocityVec.y = 0;
                suMobile = m;
                return;
            }
        }
    }

    /** Le piattaforme che crollano tremano quando ci si sale sopra, poi cadono e ricompaiono. */
    private void aggiornaCrolli(float dt)
    {
        Rectangle hb = esploratore.getHitbox(0);
        Rectangle piedi = new Rectangle(hb.x + 2, esploratore.getPiediY() - 3, hb.width - 4, 5);
        for (Crollo c : crolli) {
            boolean qui = c.passato == isInPassato;
            if (qui && esploratore.isATerra() && !c.isCaduta() && piedi.overlaps(c.getRettangolo())) {
                if (c.calpesta()) {
                    Risorse.suona(Risorse.sCrollo, 0.5f, 1.3f);
                    Rectangle r = c.getRettangolo();
                    particelleMondo.sbuffo(r.x + r.width / 2f, r.y + r.height, SABBIA);
                }
            }
            boolean occupato = hb.overlaps(c.getRettangolo());
            if (c.aggiorna(dt, qui, occupato) && qui) {
                Risorse.suona(Risorse.sCrollo);
                Rectangle r = c.getRettangolo();
                for (int i = 0; i < 4; i++)
                    particelleMondo.esplosione(r.x + r.width * (i + 0.5f) / 4f, r.y + r.height / 2f, 5,
                            new Color(0.7f, 0.6f, 0.45f, 0.9f), 60f, 0.7f, 250f);
            }
        }
    }

    private void tiraLeva(Leva l)
    {
        l.tirata = true;
        l.img.setDrawable(new TextureRegionDrawable(new TextureRegion(Risorse.leva2)));
        Risorse.suona(Risorse.sLeva);
        tremolio = 2f;
        particelleMondo.esplosione(l.area.getX() + l.area.getWidth() / 2f, l.area.getY() + l.area.getHeight() * 0.7f,
                10, ORO, 70f, 0.5f, 120f);
        boolean altraEpoca = false;
        for (final Gabbia g : gabbie) {
            if (g.aperta) continue;
            g.aperta = true;
            if (g.passato != l.passato) altraEpoca = true;
            g.img.clearActions();
            g.img.addAction(Actions.sequence(Actions.delay(0.3f), Actions.run(new Runnable() {
                public void run() { Risorse.suona(Risorse.sCancello); }
            }), Actions.parallel(Actions.moveBy(0, 50, 0.8f, Interpolation.pow2In), Actions.fadeOut(0.8f)),
                    Actions.visible(false)));
        }
        mostraMessaggio(altraEpoca ? "Hai sentito un cancello aprirsi... in un'altra epoca!" : "Un cancello si e' aperto!", 3f);
        Trofei.sblocca("ingegnere", uiStage);
    }

    private boolean inGabbia(Scarabeo s)
    {
        for (Gabbia g : gabbie)
            if (!g.aperta && g.passato == s.passato && g.area.overlaps(s.area))
                return true;
        return false;
    }

    private void registraPosizione()
    {
        storiaX[storiaIndice] = esploratore.getX();
        storiaY[storiaIndice] = esploratore.getY();
        storiaFrame[storiaIndice] = esploratore.getFrameCorrente();
        storiaSinistra[storiaIndice] = esploratore.isVersoSinistra();
        storiaIndice = (storiaIndice + 1) % STORIA;
        storiaQuante = Math.min(STORIA, storiaQuante + 1);
    }

    private void suonaPasso(float volume)
    {
        boolean deserto = ambiente.equals("deserto");
        Risorse.suona(deserto ? Risorse.sPassoSabbia : Risorse.sPassoPietra, volume * 0.5f, MathUtils.random(0.88f, 1.12f));
    }

    /** Passi, polvere di corsa, gocce d'acqua e sabbia che cade dal soffitto. */
    private void aggiornaAmbiente(float dt)
    {
        // passi e polvere mentre si corre
        if (esploratore.isATerra() && esploratore.velocityVec.x != 0) {
            timerPasso -= dt;
            if (timerPasso <= 0) {
                timerPasso = 0.27f;
                suonaPasso(0.6f);
                float dir = esploratore.velocityVec.x > 0 ? 1 : -1;
                particelleMondo.polvereCorsa(esploratore.getCentroX() - dir * esploratore.getDimensione() * 0.12f,
                        esploratore.getPiediY(), dir, SABBIA);
            }
        } else {
            timerPasso = 0.05f;
        }

        if (ambiente.equals("deserto")) return;

        // gocce d'acqua lontane
        timerGocce -= dt;
        if (timerGocce <= 0) {
            timerGocce = MathUtils.random(4f, 9f);
            Risorse.suona(Risorse.sGoccia, MathUtils.random(0.2f, 0.4f), MathUtils.random(0.85f, 1.2f));
        }

        // sabbia che cade dal soffitto (solo nel presente: la piramide è antica)
        if (!isInPassato) {
            if (sabbiaRestante > 0) {
                sabbiaRestante -= dt;
                timerGranello -= dt;
                while (timerGranello <= 0) {
                    timerGranello += 0.03f;
                    particelleMondo.granello(sabbiaX, sabbiaY, sabbiaMinY, GRANELLO);
                }
            } else {
                timerSabbia -= dt;
                if (timerSabbia <= 0) {
                    timerSabbia = MathUtils.random(2f, 4.5f);
                    scegliCascataDiSabbia();
                }
            }
        }
    }

    /** Sceglie la parte di sotto di un blocco (soffitto) vicino alla telecamera da cui far cadere la sabbia. */
    private void scegliCascataDiSabbia()
    {
        Rectangle mondo = BaseActor.getWorldBounds();
        ArrayList<Oggetto> possibili = new ArrayList<Oggetto>();
        for (Oggetto o : solidiPresente) {
            float cx = o.getX() + o.getWidth() / 2f;
            if (o.getWidth() >= 30 && o.getY() > 60 && o.getY() < mondo.height - 8
                    && Math.abs(cx - camX) < 420 && Math.abs(o.getY() - camY) < 260)
                possibili.add(o);
        }
        if (possibili.isEmpty()) return;
        Oggetto o = possibili.get(MathUtils.random(possibili.size() - 1));
        sabbiaX = o.getX() + MathUtils.random(10f, o.getWidth() - 10f);
        sabbiaY = o.getY();
        // trova il pavimento sotto la cascata
        sabbiaMinY = 0;
        for (Oggetto p : solidiPresente) {
            float top = p.getY() + p.getHeight();
            if (p != o && sabbiaX >= p.getX() && sabbiaX <= p.getX() + p.getWidth() && top <= sabbiaY && top > sabbiaMinY)
                sabbiaMinY = top;
        }
        sabbiaRestante = MathUtils.random(1.0f, 2.0f);
        timerGranello = 0;
    }

    private void raccogliScarabei()
    {
        for (int i = scarabei.size() - 1; i >= 0; i--) {
            Scarabeo s = scarabei.get(i);
            if (s.passato != isInPassato || !esploratore.overlaps(s.area) || inGabbia(s)) continue;
            StatoPartita.scarabei.add(s.id);
            Risorse.suona(Risorse.sMoneta);
            particelleMondo.esplosione(s.img.getX() + s.img.getWidth() / 2f, s.img.getY() + s.img.getHeight() / 2f,
                    16, ORO, 90f, 0.6f, 80f);
            s.img.clearActions();
            s.img.setOrigin(s.img.getWidth() / 2f, s.img.getHeight() / 2f);
            s.img.addAction(Actions.sequence(
                    Actions.parallel(Actions.moveBy(0, 30, 0.35f, Interpolation.pow2Out),
                            Actions.scaleTo(1.6f, 1.6f, 0.35f), Actions.fadeOut(0.35f)),
                    Actions.removeActor()));
            s.area.remove();
            scarabei.remove(i);
            aggiornaHudScarabei();
            iconaScarabeo.clearActions();
            iconaScarabeo.setScale(1f);
            iconaScarabeo.addAction(Actions.sequence(Actions.scaleTo(1.5f, 1.5f, 0.1f), Actions.scaleTo(1f, 1f, 0.2f)));
            Trofei.sblocca("scarabeo", uiStage);
            if (StatoPartita.scarabei.size() >= Risorse.totaleScarabei)
                Trofei.sblocca("collezionista", uiStage);
        }
    }

    /**
     * Separa il personaggio dai solidi (prima in orizzontale, poi in verticale).
     * Quando si salta contro un soffitto il personaggio viene respinto verso il basso.
     */
    private void collisioni(List<Oggetto> solidi, boolean orizzontale)
    {
        for (Oggetto o : solidi) {
            if (!o.isEnable() || !esploratore.overlaps(o)) continue;
            float yPrima = esploratore.getY();
            Vector2 spinta = esploratore.preventOverlap(o);
            if (spinta == null) continue;

            boolean spintoGiu = esploratore.getY() < yPrima;
            if (orizzontale) {
                if (spintoGiu) esploratore.setY(yPrima);
                else esploratore.velocityVec.x = 0;
            } else {
                if (spintoGiu && esploratore.velocityVec.y <= 0) esploratore.setY(yPrima);
                if (Math.abs(spinta.y) >= Math.abs(spinta.x)) esploratore.velocityVec.y = 0;
            }
        }
    }

    private Oggetto uscitaAttiva()
    {
        return isInPassato ? uscitaPassato : uscitaPresente;
    }

    /** Luccichio sull'uscita e sull'oggetto con l'artefatto: aiuta a capire dove andare. */
    private void emettiScintille(float dt)
    {
        timerScintille -= dt;
        if (timerScintille > 0) return;
        timerScintille = 0.09f;
        Oggetto u = uscitaAttiva();
        if (u != null)
            particelleMondo.scintilla(u.getX(), u.getY(), u.getWidth(), u.getHeight() * 0.8f,
                    isInPassato ? AZZURRO : ORO);
        if (!hasArtifact && !isInPassato && oggettoArtefatto != null)
            particelleMondo.scintilla(oggettoArtefatto.getX(), oggettoArtefatto.getY(),
                    oggettoArtefatto.getWidth(), oggettoArtefatto.getHeight(), AZZURRO);
        if (MathUtils.random() < 0.35f) {
            for (Scarabeo s : scarabei)
                if (s.passato == isInPassato)
                    particelleMondo.scintilla(s.img.getX(), s.img.getY(), s.img.getWidth(), s.img.getHeight(), ORO);
        }
    }

    /** Mostra "[E] ..." sopra la porta / il tesoro / l'artefatto / la tavoletta quando ci sei vicino. */
    private void aggiornaInterazione()
    {
        Actor bersaglio = null;
        String testo = "";
        tavolettaVicina = null;
        levaVicina = null;

        Oggetto u = uscitaAttiva();
        if (u != null && esploratore.overlaps(u)) {
            if (tipoUscita.equals("porta"))  { bersaglio = u; testo = "[E] ENTRA"; }
            if (tipoUscita.equals("tesoro")) { bersaglio = u; testo = "[E] APRI IL TESORO"; }
        }
        if (bersaglio == null && !hasArtifact && !isInPassato && oggettoArtefatto != null
                && esploratore.overlaps(oggettoArtefatto)) {
            bersaglio = oggettoArtefatto;
            testo = "[E] ISPEZIONA";
        }
        if (bersaglio == null && !isInPassato) {
            for (Tavoletta t : tavolette) {
                if (esploratore.overlaps(t.area)) {
                    tavolettaVicina = t;
                    bersaglio = t.area;
                    testo = "[E] LEGGI";
                    break;
                }
            }
        }

        if (bersaglio == null) {
            for (Leva l : leve) {
                if (l.passato == isInPassato && !l.tirata && esploratore.overlaps(l.area)) {
                    levaVicina = l;
                    bersaglio = l.area;
                    testo = "[E] TIRA LA LEVA";
                    break;
                }
            }
        }

        etichettaInterazione.setVisible(bersaglio != null);
        if (bersaglio != null) {
            etichettaInterazione.setText(testo);
            etichettaInterazione.pack();
            float bob = MathUtils.sin(tempoAnimazione * 4f) * 3f;
            etichettaInterazione.setPosition(
                    Math.round(bersaglio.getX() + bersaglio.getWidth() / 2f - etichettaInterazione.getWidth() / 2f),
                    Math.round(bersaglio.getY() + bersaglio.getHeight() + 10 + bob));
            etichettaInterazione.toFront();
        }
    }

    private void interagisci()
    {
        Oggetto u = uscitaAttiva();
        if (u != null && esploratore.overlaps(u)) {
            if (tipoUscita.equals("porta"))  { cambiaLivello(); return; }
            if (tipoUscita.equals("tesoro")) { avviaFinale(); return; }
        }
        if (!hasArtifact && !isInPassato && oggettoArtefatto != null && esploratore.overlaps(oggettoArtefatto)) {
            mostraCutscene();
            return;
        }
        if (tavolettaVicina != null) {
            apriTavoletta(tavolettaVicina);
            return;
        }
        if (levaVicina != null)
            tiraLeva(levaVicina);
    }

    private void apriTavoletta(Tavoletta t)
    {
        lettura = true;
        etichettaInterazione.setVisible(false);
        Risorse.suona(Risorse.sTavoletta);
        // a capo calcolato una volta sola (l'a capo automatico di libGDX faceva tremare il testo)
        letTesto.setText(aCapo("\"" + t.testo + "\"", 40));
        letTesto.pack();
        letTesto.setPosition(Math.round(LARGHEZZA / 2f - letTesto.getWidth() / 2f),
                Math.round(230 - letTesto.getHeight() / 2f));
        mostraLettura(true);
        int lette = Trofei.segnaTavoletta(t.id);
        if (lette >= Risorse.totaleTavolette)
            Trofei.sblocca("archeologo", uiStage);
    }

    /** Divide un testo in righe di al massimo "max" caratteri, senza spezzare le parole. */
    private static String aCapo(String testo, int max)
    {
        StringBuilder risultato = new StringBuilder();
        StringBuilder riga = new StringBuilder();
        for (String parola : testo.split(" ")) {
            if (riga.length() > 0 && riga.length() + 1 + parola.length() > max) {
                risultato.append(riga).append('\n');
                riga.setLength(0);
            }
            if (riga.length() > 0) riga.append(' ');
            riga.append(parola);
        }
        risultato.append(riga);
        return risultato.toString();
    }

    private void chiudiTavoletta()
    {
        lettura = false;
        mostraLettura(false);
    }

    /** Telecamera che segue il personaggio in modo morbido, senza uscire dalla mappa. */
    private void aggiornaCamera(float dt, boolean subito)
    {
        Rectangle m = BaseActor.getWorldBounds();
        float tx = esploratore.getCentroX();
        float ty = esploratore.getCentroY();
        tx = (m.width  <= LARGHEZZA) ? m.width / 2f  : MathUtils.clamp(tx, LARGHEZZA / 2f, m.width - LARGHEZZA / 2f);
        ty = (m.height <= ALTEZZA)   ? m.height / 2f : MathUtils.clamp(ty, ALTEZZA / 2f, m.height - ALTEZZA / 2f);

        if (subito) {
            camX = tx;
            camY = ty;
        } else {
            float k = Math.min(1f, dt * 8f);
            camX += (tx - camX) * k;
            camY += (ty - camY) * k;
        }

        float ox = 0, oy = 0;
        if (tremolio > 0) {
            ox = MathUtils.random(-tremolio, tremolio);
            oy = MathUtils.random(-tremolio, tremolio);
            tremolio = Math.max(0, tremolio - dt * 15f);
        }

        Camera cam = mainStage.getCamera();
        cam.position.set(Math.round(camX + ox), Math.round(camY + oy), 0);
        cam.update();
    }

    @Override
    public void resize(int width, int height)
    {
        super.resize(width, height);
        if (esploratore != null)
            aggiornaCamera(0, true);
    }

    // =====================================================================
    // Eventi: cutscene, morte, uscita, finale
    // =====================================================================

    private void mostraCutscene()
    {
        cutscene = true;
        cutsceneChiudibile = false;
        etichettaInterazione.setVisible(false);
        Risorse.suona(Risorse.sArtefatto);

        cutOverlay.setVisible(true);
        cutOverlay.getColor().a = 0;
        cutOverlay.addAction(Actions.alpha(0.8f, 0.6f));

        cutTitolo.setVisible(true);
        cutTitolo.getColor().a = 0;
        cutTitolo.addAction(Actions.fadeIn(1.0f));

        cutImmagine.setVisible(true);
        cutImmagine.getColor().a = 0;
        cutImmagine.setScale(0.6f);
        cutImmagine.addAction(Actions.sequence(
                Actions.delay(0.4f),
                Actions.parallel(Actions.fadeIn(1.2f), Actions.scaleTo(1f, 1f, 1.2f)),
                Actions.forever(Actions.sequence(Actions.scaleTo(1.06f, 1.06f, 0.8f), Actions.scaleTo(1f, 1f, 0.8f)))));

        cutSotto.setVisible(true);
        cutSotto.getColor().a = 0;
        cutSotto.addAction(Actions.sequence(Actions.delay(1.6f), Actions.run(new Runnable() {
            public void run() { cutsceneChiudibile = true; }
        }), Actions.forever(Actions.sequence(Actions.fadeIn(0.5f), Actions.fadeOut(0.5f)))));
    }

    private void chiudiCutscene()
    {
        cutscene = false;
        hasArtifact = true;
        artefattoAlRiavvio = true;
        Actor[] parti = { cutOverlay, cutTitolo, cutImmagine, cutSotto };
        for (Actor a : parti) {
            a.clearActions();
            a.setVisible(false);
        }

        iconaArtefatto.setVisible(true);
        iconaArtefatto.setScale(0f);
        iconaArtefatto.addAction(Actions.scaleTo(1f, 1f, 0.4f));
        tastoT.setVisible(mappaPassato != null);
        mostraMessaggio("Premi T per viaggiare nel tempo", 5f);
        Trofei.sblocca("artefatto", uiStage);
        Save.salva(livello, true, isInPassato, StatoPartita.morti, StatoPartita.tempo);
    }

    private void muori()
    {
        esploratore.morte();
        etichettaInterazione.setVisible(false);
        StatoPartita.morti++;
        StatoPartita.mortiNelLivello++;
        Risorse.attutisci(true);
        Risorse.suona(Risorse.sMorte);
        tremolio = 6f;
        lampo(new Color(1f, 0.1f, 0.05f, 1f), 0.45f, 0.5f);
        particelleMondo.esplosione(esploratore.getCentroX(), esploratore.getCentroY(), 24,
                new Color(0.9f, 0.3f, 0.2f, 0.9f), 110f, 0.8f, 200f);
        if (Trofei.incrementa("morti") >= 25)
            Trofei.sblocca("testardo", uiStage);
        Save.salva(livello, artefattoAlRiavvio, passatoIniziale && mappaPassato != null,
                StatoPartita.morti, StatoPartita.tempo);
    }

    private void mostraGameOver()
    {
        gameOver = true;
        goSotto.setText("Morti finora: " + StatoPartita.morti + "     (R = riprova)");
        centra(goSotto, 252);
        mostraGameOverUi(true);
        goOverlay.getColor().a = 0;
        goOverlay.addAction(Actions.alpha(0.8f, 0.4f));
        goTitolo.getColor().a = 0;
        goTitolo.addAction(Actions.fadeIn(0.5f));
        sabbia.toFront();
    }

    private void cambiaLivello()
    {
        if (transizione) return;
        etichettaInterazione.setVisible(false);
        Risorse.suona(Risorse.sPorta);
        if (livello == 1)
            Trofei.sblocca("primo_passo", uiStage);
        if (livello >= 3 && StatoPartita.mortiNelLivello == 0)
            Trofei.sblocca("intoccabile", uiStage);
        Save.aggiornaTempoLivello(livello, StatoPartita.tempoLivello);
        Save.registraLivello(livello + 1);
        final int prossimo = livello + 1;
        final boolean artefatto = hasArtifact;
        final boolean passato = isInPassato;
        if (prossimo > Risorse.numeroLivelli) {
            avviaFinale();
            return;
        }
        transizione = true;
        sabbia.chiudi(0.8f, new Runnable() {
            public void run() { BaseGame.setActiveScreen(new LevelScreen(prossimo, artefatto, passato)); }
        });
    }

    /** Il forziere si apre: bagliore che cresce, pioggia di monete e poi la schermata finale. */
    private void avviaFinale()
    {
        if (transizione) return;
        transizione = true;
        finaleInCorso = true;
        finaleTempo = 0f;
        etichettaInterazione.setVisible(false);
        Risorse.suona(Risorse.sForziere);
        tremolio = 5f;
        Save.aggiornaTempoLivello(livello, StatoPartita.tempoLivello);
        Save.cancella();
        Oggetto u = uscitaAttiva();
        float cx = u != null ? u.getX() + u.getWidth() / 2f : esploratore.getCentroX();
        float cy = u != null ? u.getY() + u.getHeight() * 0.6f : esploratore.getCentroY();
        bagliore = luciEffetti.aggiungi(cx, cy, 40, 0f, 1f, 0.8f, 0.4f, true);
    }

    private void aggiornaFinale(float dt)
    {
        float prima = finaleTempo;
        finaleTempo += dt;
        bagliore.dimensione = 60 + finaleTempo * 420;
        bagliore.intensita = Math.min(0.9f, finaleTempo * 0.7f);
        if (finaleTempo < 1.8f && MathUtils.random() < 0.6f)
            particelleMondo.esplosione(bagliore.x, bagliore.y, 5, ORO, 260f, 1.3f, 380f);
        if (prima < 0.9f && finaleTempo >= 0.9f)
            Risorse.suona(Risorse.sVittoria);
        if (prima < 1.4f && finaleTempo >= 1.4f) {
            flash.clearActions();
            flash.setColor(1f, 0.92f, 0.65f, 0f);
            flash.addAction(Actions.alpha(1f, 1.3f));
        }
        if (prima < 2.6f && finaleTempo >= 2.6f) {
            sabbia.chiudi(1.0f, new Runnable() {
                public void run() { BaseGame.setActiveScreen(new FineScreen()); }
            });
        }
    }

    private void riprova()
    {
        if (transizione) return;
        transizione = true;
        sabbia.chiudi(0.5f, new Runnable() {
            public void run() { BaseGame.setActiveScreen(new LevelScreen(livello, artefattoAlRiavvio, passatoIniziale)); }
        });
    }

    private void vaiAlMenu()
    {
        if (transizione) return;
        transizione = true;
        sabbia.chiudi(0.5f, new Runnable() {
            public void run() { BaseGame.setActiveScreen(new MenuScreen()); }
        });
    }

    // =====================================================================
    // Pausa e input
    // =====================================================================

    private boolean puoMettereInPausa()
    {
        return !gameOver && !transizione && !cutscene && !lettura && !esploratore.isDead();
    }

    /** Se la finestra viene ridotta a icona, il gioco va in pausa da solo. */
    @Override
    public void pause()
    {
        if (!pausa && esploratore != null && puoMettereInPausa())
            alternaPausa();
    }

    private void alternaPausa()
    {
        pausa = !pausa;
        mostraPausa(pausa);
        // la musica non si ferma: si abbassa ("attutita") e risale quando si riprende
        Risorse.attutisci(pausa);
    }

    @Override
    public boolean keyDown(int keyCode)
    {
        if (keyCode == Input.Keys.ESCAPE || keyCode == Input.Keys.P) {
            if (lettura) { chiudiTavoletta(); return true; }
            if (puoMettereInPausa()) alternaPausa();
            return true;
        }
        if (transizione) return false;
        if (pausa) return pausaBottoni.gestisciTasto(keyCode);
        if (gameOver) {
            if (keyCode == Input.Keys.R) { riprova(); return true; }
            return goBottoni.gestisciTasto(keyCode);
        }
        if (cutscene) {
            if (cutsceneChiudibile && (keyCode == Input.Keys.E || keyCode == Input.Keys.ENTER || keyCode == Input.Keys.SPACE))
                chiudiCutscene();
            return true;
        }
        if (lettura) {
            if (keyCode == Input.Keys.E || keyCode == Input.Keys.ENTER || keyCode == Input.Keys.SPACE)
                chiudiTavoletta();
            return true;
        }
        if (esploratore.isDead()) return false;

        if (keyCode == Input.Keys.SPACE || keyCode == Input.Keys.W || keyCode == Input.Keys.UP)
            esploratore.premiSalto();
        if (keyCode == Input.Keys.E)
            interagisci();
        if (keyCode == Input.Keys.T)
            viaggiaNelTempo();
        return false;
    }

    @Override
    public boolean keyUp(int keyCode)
    {
        if (keyCode == Input.Keys.SPACE || keyCode == Input.Keys.W || keyCode == Input.Keys.UP)
            esploratore.rilasciaSalto();
        return false;
    }

    @Override
    public void hide()
    {
        super.hide();
        Risorse.fermaAmbiente();
    }

    @Override
    public void dispose()
    {
        super.dispose();
        mappaPresente.dispose();
        if (mappaPassato != null) mappaPassato.dispose();
    }
}
