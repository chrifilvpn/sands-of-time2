import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;

/**
 * Riflesso di luce che attraversa il titolo del menu ogni pochi secondi.
 * Il disegno è "ritagliato" (scissor) all'area del titolo.
 */
public class Luccichio extends Actor
{
    private final Rectangle area;
    private final Rectangle ritaglio = new Rectangle();
    private final TextureRegion luce;
    private float tempo = 0;
    private static final float PERIODO = 5f, PASSAGGIO = 1.4f;

    public Luccichio(Stage stage, Rectangle area)
    {
        this.area = area;
        this.luce = new TextureRegion(Risorse.luce);
        setTouchable(Touchable.disabled);
        stage.addActor(this);
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
        float t = tempo % PERIODO;
        if (t > PASSAGGIO) return;
        float p = t / PASSAGGIO;
        float x = area.x - 120 + (area.width + 240) * p;

        batch.flush();
        getStage().calculateScissors(area, ritaglio);
        if (ScissorStack.pushScissors(ritaglio)) {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            float a = 0.55f * (float) Math.sin(Math.PI * p) * parentAlpha;
            batch.setColor(1f, 0.92f, 0.7f, a);
            batch.draw(luce, x - 45, area.y - 40, 45, (area.height + 80) / 2f, 90, area.height + 80, 1, 1, -22);
            batch.setColor(1f, 1f, 1f, a * 0.8f);
            batch.draw(luce, x - 12, area.y - 40, 12, (area.height + 80) / 2f, 24, area.height + 80, 1, 1, -22);
            batch.flush();
            ScissorStack.popScissors();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            batch.setColor(1, 1, 1, 1);
        }
    }
}
