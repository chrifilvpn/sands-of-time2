import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;

/**
 * Elenco dei trofei: quelli sbloccati sono dorati, gli altri grigi.
 */
public class TrofeiScreen extends BaseScreen
{
    private TransizioneSabbia sabbia;
    private boolean uscendo = false;

    @Override
    public void initialize()
    {
        Image sfondo = new Image(new TextureRegion(Risorse.sfondoMenu, 0, 70, 1600, 960));
        sfondo.setSize(LARGHEZZA, ALTEZZA);
        sfondo.setColor(0.35f, 0.3f, 0.26f, 1f);
        uiStage.addActor(sfondo);
        sfondo.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) { indietro(); }
        });

        Image pannello = new Image(Risorse.pannello);
        pannello.setBounds(30, 30, 740, 420);
        pannello.setTouchable(Touchable.disabled);
        uiStage.addActor(pannello);

        Color oro = new Color(1f, 0.82f, 0.35f, 1f);
        Label titolo = Risorse.etichetta("TROFEI  " + Trofei.quantiSbloccati() + "/" + Trofei.ELENCO.length,
                Risorse.fontGrande, oro);
        titolo.setPosition(Math.round(LARGHEZZA / 2f - titolo.getWidth() / 2f), 405);
        uiStage.addActor(titolo);

        // due colonne
        int perColonna = (Trofei.ELENCO.length + 1) / 2;
        for (int i = 0; i < Trofei.ELENCO.length; i++) {
            String[] t = Trofei.ELENCO[i];
            boolean ok = Trofei.sbloccato(t[0]);
            float x = (i < perColonna) ? 56 : 412;
            float y = 350 - (i % perColonna) * 44;

            Image icona = new Image(Risorse.scarabeo);
            icona.setBounds(x, y - 4, 30, 30);
            icona.setColor(ok ? Color.WHITE : new Color(0.25f, 0.25f, 0.25f, 0.8f));
            icona.setTouchable(Touchable.disabled);
            uiStage.addActor(icona);

            Label nome = Risorse.etichetta(t[1], Risorse.fontNormale, ok ? oro : Color.GRAY);
            nome.setPosition(x + 40, y + 12);
            uiStage.addActor(nome);
            Label descr = Risorse.etichetta(t[2], Risorse.fontPiccolo,
                    ok ? new Color(0.9f, 0.85f, 0.75f, 1f) : new Color(0.55f, 0.55f, 0.55f, 1f));
            descr.setPosition(x + 40, y - 4);
            uiStage.addActor(descr);
        }

        Label aiuto = Risorse.etichetta("ESC o INVIO per tornare", Risorse.fontPiccolo, new Color(1f, 1f, 1f, 0.6f));
        aiuto.setPosition(Math.round(LARGHEZZA / 2f - aiuto.getWidth() / 2f), 42);
        uiStage.addActor(aiuto);

        sabbia = new TransizioneSabbia(uiStage);
        sabbia.apri(0.7f);
    }

    private void indietro()
    {
        if (uscendo) return;
        uscendo = true;
        Risorse.suona(Risorse.sClick);
        sabbia.chiudi(0.6f, new Runnable() {
            public void run() { BaseGame.setActiveScreen(new MenuScreen()); }
        });
    }

    @Override
    public boolean keyDown(int keycode)
    {
        if (keycode == Input.Keys.ESCAPE || keycode == Input.Keys.ENTER || keycode == Input.Keys.SPACE) {
            indietro();
            return true;
        }
        return false;
    }

    @Override
    public void update(float dt) {}
}
