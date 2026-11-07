import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;

/**
 * Piattaforma che crolla: quando ci sali sopra trema per un attimo, poi cade.
 * Dopo qualche secondo ricompare al suo posto (così il livello resta sempre completabile).
 * Nella mappa Tiled è un oggetto chiamato "crollo" (al posto di "solid").
 */
public class Crollo
{
    private enum Stato { INTEGRA, TREMA, CADUTA }

    private static final float TEMPO_TREMA = 0.55f;
    private static final float TEMPO_RITORNO = 3f;

    public final Oggetto solido;
    public final Group grafica;
    public final boolean passato;
    private final float x0, y0;
    private Stato stato = Stato.INTEGRA;
    private float timer = 0f;

    public Crollo(Oggetto solido, Group grafica, boolean passato)
    {
        this.solido = solido;
        this.grafica = grafica;
        this.passato = passato;
        this.x0 = grafica.getX();
        this.y0 = grafica.getY();
    }

    public boolean isCaduta()
    {
        return stato == Stato.CADUTA;
    }

    /** Il personaggio ci ha appena messo i piedi sopra. */
    public boolean calpesta()
    {
        if (stato != Stato.INTEGRA) return false;
        stato = Stato.TREMA;
        timer = TEMPO_TREMA;
        return true;
    }

    /**
     * Aggiorna il crollo. Restituisce true nel momento in cui la piattaforma cade.
     * @param occupato true se il personaggio è nel punto in cui la piattaforma deve ricomparire
     */
    public boolean aggiorna(float dt, boolean epocaGiusta, boolean occupato)
    {
        boolean cadutaOra = false;
        if (stato == Stato.TREMA) {
            timer -= dt;
            grafica.setPosition(x0 + MathUtils.random(-1.5f, 1.5f), y0 + MathUtils.random(-1f, 1f));
            if (timer <= 0) {
                stato = Stato.CADUTA;
                timer = TEMPO_RITORNO;
                solido.crollato = true;
                solido.setEnable(false);
                grafica.setPosition(x0, y0);
                grafica.clearActions();
                grafica.addAction(Actions.parallel(
                        Actions.moveBy(0, -260, 0.9f, Interpolation.pow2In),
                        Actions.fadeOut(0.9f)));
                cadutaOra = true;
            }
        } else if (stato == Stato.CADUTA) {
            timer -= dt;
            if (timer <= 0 && !occupato) {
                stato = Stato.INTEGRA;
                solido.crollato = false;
                solido.setEnable(epocaGiusta);
                grafica.clearActions();
                grafica.setPosition(x0, y0);
                grafica.getColor().a = 0;
                grafica.addAction(Actions.fadeIn(0.4f));
            }
        }
        return cadutaOra;
    }

    public Rectangle getRettangolo()
    {
        return solido.getRettangolo();
    }
}
