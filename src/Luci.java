import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import java.util.ArrayList;

/**
 * Luci "additive" (schiariscono quello che c'è sotto): aloni delle torce che tremolano
 * e bagliori (es. il forziere che si apre).
 */
public class Luci extends Actor
{
    public static class Luce {
        public float x, y, dimensione, intensita;
        public float r, g, b;
        public boolean tremola;
        float seme;
    }

    private final ArrayList<Luce> luci = new ArrayList<Luce>();
    private float tempo = 0;

    public Luci(Stage stage)
    {
        setTouchable(Touchable.disabled);
        stage.addActor(this);
    }

    /** Alone caldo di una torcia. */
    public Luce torcia(float x, float y)
    {
        return aggiungi(x, y, 190, 0.42f, 1f, 0.62f, 0.28f, true);
    }

    public Luce aggiungi(float x, float y, float dimensione, float intensita, float r, float g, float b, boolean tremola)
    {
        Luce l = new Luce();
        l.x = x; l.y = y; l.dimensione = dimensione; l.intensita = intensita;
        l.r = r; l.g = g; l.b = b; l.tremola = tremola;
        l.seme = MathUtils.random(100f);
        luci.add(l);
        return l;
    }

    @Override
    public void act(float dt)
    {
        super.act(dt);
        tempo += dt;
    }

    @Override
    public void draw(Batch batch, float parentAlpha)
    {
        if (luci.isEmpty()) return;
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        for (Luce l : luci) {
            float f = 1f;
            if (l.tremola) {
                float t = tempo + l.seme;
                f = 0.86f + 0.08f * MathUtils.sin(t * 11f) + 0.06f * MathUtils.sin(t * 23.7f + 1.3f);
            }
            float a = l.intensita * f * parentAlpha * getColor().a;
            if (a <= 0.003f) continue;
            float d = l.dimensione * (0.94f + 0.06f * f);
            batch.setColor(l.r, l.g, l.b, a);
            batch.draw(Risorse.luce, l.x - d / 2f, l.y - d / 2f, d, d);
            // nucleo più chiaro
            float n = d * 0.3f;
            batch.setColor(1f, 0.9f, 0.7f, a * 0.8f);
            batch.draw(Risorse.luce, l.x - n / 2f, l.y - n / 2f, n, n);
        }
        batch.setColor(1, 1, 1, 1);
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
    }
}
