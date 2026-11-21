import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;

/**
 * Trappole "a sorpresa" (ispirate a Level Devil). Nella mappa Tiled sono oggetti chiamati:
 *  - "scatto": spine nascoste che spuntano dal pavimento quando il personaggio si avvicina;
 *  - "cade":   una zanna appesa al soffitto che si stacca quando il personaggio ci passa sotto.
 * Proprietà facoltative: "distanza" (px in orizzontale da cui scattano, default 48)
 * e "ritardo" (secondi prima di scattare, default 0.12).
 * Il rettangolo dell'oggetto è anche la zona che uccide (una volta scattata la trappola).
 */
public class Insidia
{
    public enum Tipo { SCATTO, CADE }

    public final Tipo tipo;
    public final Oggetto zona;
    public final Group grafica;
    public final boolean passato;
    private final float distanza, ritardo;
    private final float x0, y0;
    private boolean innescata = false, attiva = false;
    private float timer = 0f, velocita = 0f;

    public Insidia(Tipo tipo, Oggetto zona, Group grafica, boolean passato, float distanza, float ritardo)
    {
        this.tipo = tipo;
        this.zona = zona;
        this.grafica = grafica;
        this.passato = passato;
        this.distanza = distanza;
        this.ritardo = ritardo;
        this.x0 = zona.getX();
        this.y0 = zona.getY();
        if (tipo == Tipo.SCATTO) {
            // le spine aspettano nascoste sotto il pavimento
            grafica.setVisible(false);
            grafica.setY(grafica.getY() - 32);
        }
    }

    /** True se in questo momento la trappola può uccidere. */
    public boolean isAttiva()
    {
        return attiva;
    }

    /**
     * @param cx centro X del personaggio, @param piediY i suoi piedi
     * @return true nel momento in cui la trappola scatta (per il suono)
     */
    public boolean aggiorna(float dt, boolean epocaGiusta, float cx, float piediY)
    {
        boolean scattaOra = false;
        if (!innescata && epocaGiusta) {
            float sx = x0 - distanza, dx = x0 + zona.getWidth() + distanza;
            boolean vicino = cx >= sx && cx <= dx;
            // la zanna cade solo se il personaggio è sotto di lei
            if (tipo == Tipo.CADE) vicino &= piediY < y0;
            else vicino &= Math.abs(piediY - y0) < 80;
            if (vicino) {
                innescata = true;
                timer = ritardo;
            }
        }
        if (innescata && !attiva) {
            timer -= dt;
            if (timer <= 0) {
                attiva = true;
                scattaOra = true;
                if (tipo == Tipo.SCATTO) {
                    grafica.setVisible(true);
                    grafica.addAction(Actions.moveBy(0, 32, 0.07f, Interpolation.pow2Out));
                }
            }
        }
        if (attiva && tipo == Tipo.CADE) {
            velocita += 900f * dt;
            float dy = velocita * dt;
            zona.moveBy(0, -dy);
            grafica.moveBy(0, -dy);
            if (zona.getY() < -200) attiva = false;   // finita fuori dallo schermo
        }
        return scattaOra;
    }

    public Rectangle getRettangolo()
    {
        return zona.getRettangolo();
    }
}
