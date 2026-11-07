import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;

/**
 * Transizione "a clessidra" tra le schermate: la sabbia sale e riempie lo schermo,
 * poi nella schermata successiva scende e lo libera.
 */
public class TransizioneSabbia extends Actor
{
    private static final int COLONNE = 100;
    private final float[] ritardo = new float[COLONNE];
    private final float[] tinta = new float[COLONNE];
    private float tempo = 0, durata = 1;
    private boolean chiusura = false, attiva = false;
    private Runnable dopo;

    public TransizioneSabbia(Stage stage)
    {
        setTouchable(Touchable.disabled);
        setBounds(0, 0, BaseScreen.LARGHEZZA, BaseScreen.ALTEZZA);
        for (int i = 0; i < COLONNE; i++) {
            // le colonne partono in tempi diversi: il bordo della sabbia è irregolare
            float onda = 0.5f + 0.5f * MathUtils.sin(i * 0.23f);
            ritardo[i] = 0.25f * onda + MathUtils.random(0.12f);
            tinta[i] = MathUtils.random(-0.05f, 0.05f);
        }
        stage.addActor(this);
    }

    /** Schermo pieno di sabbia che scende e scopre la schermata. */
    public void apri(float durata)
    {
        this.durata = durata;
        tempo = 0;
        chiusura = false;
        attiva = true;
        dopo = null;
    }

    /** La sabbia sale fino a coprire lo schermo, poi esegue "dopo". */
    public void chiudi(float durata, Runnable dopo)
    {
        this.durata = durata;
        tempo = 0;
        chiusura = true;
        attiva = true;
        this.dopo = dopo;
        toFront();
        Risorse.suona(Risorse.sClessidra, 0.8f, 1f);
    }

    public boolean isInCorso()
    {
        return attiva && tempo < durata;
    }

    @Override
    public void act(float dt)
    {
        super.act(dt);
        if (!attiva) return;
        tempo += dt;
        if (tempo >= durata) {
            if (chiusura && dopo != null) {
                Runnable r = dopo;
                dopo = null;
                r.run();
            } else if (!chiusura) {
                attiva = false;
            }
        }
    }

    /** 0 = colonna vuota, 1 = colonna piena. */
    private float riempimento(int i)
    {
        float p = (tempo / durata - ritardo[i]) / (1f - 0.37f);
        p = MathUtils.clamp(p, 0, 1);
        p = p * p * (3 - 2 * p);   // movimento morbido
        return chiusura ? p : 1 - p;
    }

    @Override
    public void draw(Batch batch, float parentAlpha)
    {
        if (!attiva && !chiusura) return;
        float w = BaseScreen.LARGHEZZA / COLONNE;
        float H = BaseScreen.ALTEZZA;
        for (int i = 0; i < COLONNE; i++) {
            float f = riempimento(i);
            if (f <= 0) continue;
            float h = f * (H + 30);
            float x = i * w;
            float t = tinta[i];
            // la sabbia parte sempre dal basso: in chiusura sale, in apertura scende
            float base = 0;
            float altezza = h;
            // corpo della sabbia
            batch.setColor(0.78f + t, 0.6f + t, 0.36f + t, 1f);
            batch.draw(Risorse.bianco, x, base, w + 1, altezza);
            // venature più scure
            batch.setColor(0.68f + t, 0.5f + t, 0.3f + t, 1f);
            for (float y = base + 20 + (i % 3) * 7; y < base + altezza - 6; y += 38)
                batch.draw(Risorse.bianco, x, y, w + 1, 3);
            // cresta chiara
            batch.setColor(0.95f, 0.82f, 0.56f, 1f);
            batch.draw(Risorse.bianco, x, base + altezza - 3, w + 1, 3);
        }
        batch.setColor(1, 1, 1, 1);
    }
}
