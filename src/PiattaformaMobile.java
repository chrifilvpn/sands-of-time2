import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.ui.Image;

/**
 * Piattaforma che si muove avanti e indietro. Ci si sale solo da sopra (si può attraversare
 * saltando da sotto) e trasporta il personaggio quando ci sta sopra.
 * Nella mappa Tiled è un oggetto "mobile" con le proprietà dx, dy (spostamento in pixel)
 * e durata (secondi per andare da un capo all'altro).
 */
public class PiattaformaMobile
{
    public final Oggetto area;
    public final Image img;
    public final boolean passato;
    private final float sx, sy, dx, dy, durata;
    private float t = 0f;
    public float deltaX = 0f, deltaY = 0f;

    public PiattaformaMobile(Oggetto area, Image img, boolean passato, float dx, float dy, float durata)
    {
        this.area = area;
        this.img = img;
        this.passato = passato;
        this.sx = area.getX();
        this.sy = area.getY();
        this.dx = dx;
        this.dy = dy;
        this.durata = Math.max(0.5f, durata);
        posiziona();
    }

    public void aggiorna(float dt)
    {
        float vecchiaX = area.getX(), vecchiaY = area.getY();
        t += dt;
        float x = sx + dx * fase(), y = sy + dy * fase();
        area.setPosition(x, y);
        deltaX = x - vecchiaX;
        deltaY = y - vecchiaY;
        posiziona();
    }

    /** 0 -> 1 -> 0 con rallentamento agli estremi. */
    private float fase()
    {
        float p = (t / durata) % 2f;
        if (p > 1f) p = 2f - p;
        return p * p * (3 - 2 * p);
    }

    private void posiziona()
    {
        img.setPosition(Math.round(area.getX()), Math.round(area.getY() + area.getHeight() - img.getHeight()));
    }

    public float getTop()
    {
        return area.getY() + area.getHeight();
    }

    /** Il rettangolo "orizzontale" della piattaforma si sovrappone a quello indicato? */
    public boolean sovrapponeX(Rectangle r)
    {
        return r.x < area.getX() + area.getWidth() && r.x + r.width > area.getX();
    }
}
