import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import java.util.List;

/**
 * Visione temporale (tasto Q): mostra in trasparenza i muri, le trappole e l'uscita
 * dell'ALTRA epoca, così si capisce prima dove si atterra viaggiando nel tempo.
 */
public class VisioneTemporale extends Actor
{
    private List<Oggetto> solidi, trappole;
    private Oggetto uscita;
    private float r, g, b;
    private float intensita = 0f;
    private boolean attiva = false;
    private float tempo = 0f;

    public VisioneTemporale(Stage stage)
    {
        setTouchable(Touchable.disabled);
        stage.addActor(this);
    }

    /** Imposta cosa mostrare (gli oggetti dell'epoca in cui NON si è). */
    public void imposta(List<Oggetto> solidi, List<Oggetto> trappole, Oggetto uscita, boolean altraEPassato)
    {
        this.solidi = solidi;
        this.trappole = trappole;
        this.uscita = uscita;
        if (altraEPassato) { r = 0.45f; g = 0.85f; b = 1f; }
        else               { r = 1f;    g = 0.8f;  b = 0.4f; }
    }

    public void setAttiva(boolean a)
    {
        attiva = a;
    }

    public boolean isVisibileOra()
    {
        return intensita > 0.05f;
    }

    @Override
    public void act(float dt)
    {
        super.act(dt);
        tempo += dt;
        float obiettivo = attiva ? 1f : 0f;
        float passo = dt * 5f;
        if (Math.abs(obiettivo - intensita) <= passo) intensita = obiettivo;
        else intensita += Math.signum(obiettivo - intensita) * passo;
    }

    @Override
    public void draw(Batch batch, float parentAlpha)
    {
        if (intensita <= 0 || solidi == null) return;
        float pulsa = 0.85f + 0.15f * (float) Math.sin(tempo * 4f);
        float a = intensita * parentAlpha;
        for (Oggetto o : solidi)
            riquadro(batch, o, r, g, b, 0.22f * a, 0.8f * a * pulsa);
        if (trappole != null)
            for (Oggetto o : trappole)
                riquadro(batch, o, 1f, 0.25f, 0.2f, 0.3f * a, 0.9f * a * pulsa);
        if (uscita != null)
            riquadro(batch, uscita, 1f, 0.9f, 0.4f, 0.3f * a, 1f * a * pulsa);
        batch.setColor(1, 1, 1, 1);
    }

    private void riquadro(Batch batch, Oggetto o, float r, float g, float b, float riemp, float bordo)
    {
        float x = o.getX(), y = o.getY(), w = o.getWidth(), h = o.getHeight();
        batch.setColor(r, g, b, riemp);
        batch.draw(Risorse.bianco, x, y, w, h);
        batch.setColor(r, g, b, bordo);
        batch.draw(Risorse.bianco, x, y, w, 2);
        batch.draw(Risorse.bianco, x, y + h - 2, w, 2);
        batch.draw(Risorse.bianco, x, y, 2, h);
        batch.draw(Risorse.bianco, x + w - 2, y, 2, h);
    }
}
