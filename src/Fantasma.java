import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;

/**
 * "Fantasma" trasparente del personaggio che svanisce: forma la scia del viaggio nel tempo.
 */
public class Fantasma extends Actor
{
    private final TextureRegion frame;
    private final boolean sinistra;
    private final float durata;
    private float vita = 0;
    private final float alphaIniziale;

    public Fantasma(Stage stage, TextureRegion frame, float x, float y, float dimensione,
                    boolean sinistra, Color colore, float alpha, float durata)
    {
        this.frame = frame;
        this.sinistra = sinistra;
        this.durata = durata;
        this.alphaIniziale = alpha;
        setBounds(x, y, dimensione, dimensione);
        setColor(colore.r, colore.g, colore.b, alpha);
        setTouchable(Touchable.disabled);
        stage.addActor(this);
    }

    @Override
    public void act(float dt)
    {
        super.act(dt);
        vita += dt;
        getColor().a = alphaIniziale * Math.max(0, 1 - vita / durata);
        if (vita >= durata) remove();
    }

    @Override
    public void draw(Batch batch, float parentAlpha)
    {
        if (frame == null) return;
        Color c = getColor();
        batch.setColor(c.r, c.g, c.b, c.a * parentAlpha);
        float espansione = 1f + 0.15f * (vita / durata);
        float w = getWidth() * espansione, h = getHeight() * espansione;
        float x = getX() - (w - getWidth()) / 2f;
        if (sinistra)
            batch.draw(frame, x + w, getY(), -w, h);
        else
            batch.draw(frame, x, getY(), w, h);
        batch.setColor(1, 1, 1, 1);
    }
}
