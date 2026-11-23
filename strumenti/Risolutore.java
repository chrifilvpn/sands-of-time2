import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Calcola il numero MINIMO di viaggi nel tempo per finire un livello di Sands of Time
 * raccogliendo tutti gli scarabei (e l'artefatto, se c'e').
 * Replica la fisica del gioco fotogramma per fotogramma (dt = 1/60) e fa una ricerca 0-1 BFS
 * (ogni viaggio costa 1, tutto il resto 0) sulle scelte del giocatore ogni K fotogrammi.
 *
 * Uso: java Risolutore cartellaAssets livello epocaIniziale(0=presente,1=passato) [K] [qx] [qy] [qvy] [tavolette 0/1] [pianoFile]
 */
public class Risolutore
{
    static float DT = 1f / 60f;
    static int ASSESTA = 0;   // fotogrammi da fermo prima di iniziare (per il confronto con il gioco)
    static float VEL_X = 150, GRAV = 700, VEL_SALTO = 450, VEL_MAX_Y = 400, RIDUCI_KILL = 0;
    static final float COYOTE = 0.10f, BUFFER = 0.12f, TAGLIO = 0.45f;
    static final float PIEDI = 0.13f, TESTA = 0.86f, LATO = 0.31f;

    // ------------------------------------------------------------- geometria
    static final class R {
        float x, y, w, h;
        R(float x, float y, float w, float h) { this.x = x; this.y = y; this.w = w; this.h = h; }
    }

    /** BaseActor.overlaps: bounding box stretto + SAT stretto (per rettangoli e' lo stesso test). */
    static boolean overlaps(float ax, float ay, float aw, float ah, R b)
    {
        return ax < b.x + b.w && ax + aw > b.x && ay < b.y + b.h && ay + ah > b.y;
    }

    static final class Crollo { R r; int ep; float trema; }
    static final class Mobile { R base; int ep; float dx, dy, durata; }

    // ------------------------------------------------------------- livello
    int nEp;                         // 1 o 2 epoche
    float mondoW, mondoH, dim;
    String uscita;
    List<R>[] solidi = new List[2], kill = new List[2];
    R[] usc = new R[2];
    List<R> scR = new ArrayList<>(); List<Integer> scEp = new ArrayList<>();
    List<R> gabR = new ArrayList<>(); List<Integer> gabEp = new ArrayList<>();
    List<R> levR = new ArrayList<>(); List<Integer> levEp = new ArrayList<>();
    List<R> tav = new ArrayList<>();
    List<Crollo> crolli = new ArrayList<>();
    List<Mobile> mobili = new ArrayList<>();
    R artefatto;
    R[] start = new R[2];
    boolean[] scInGabbia;            // lo scarabeo e' dentro una gabbia (stessa epoca)

    static float num(Element e, String a) { String v = e.getAttribute(a); return v.isEmpty() ? 0 : Float.parseFloat(v); }

    static Map<String, String> props(Element e)
    {
        Map<String, String> m = new HashMap<>();
        NodeList pl = e.getChildNodes();
        for (int i = 0; i < pl.getLength(); i++) {
            if (!(pl.item(i) instanceof Element)) continue;
            Element c = (Element) pl.item(i);
            if (!c.getTagName().equals("properties")) continue;
            NodeList ps = c.getElementsByTagName("property");
            for (int j = 0; j < ps.getLength(); j++) {
                Element p = (Element) ps.item(j);
                String v = p.hasAttribute("value") ? p.getAttribute("value") : p.getTextContent();
                m.put(p.getAttribute("name"), v);
            }
        }
        return m;
    }

    Document doc(File f) throws Exception
    {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f);
    }

    /** Rettangolo come lo calcola Oggetto.daMappa dopo il TmxMapLoader (asse y capovolto). */
    R rett(Element o, float mapH)
    {
        float x = num(o, "x"), yT = num(o, "y");
        NodeList pol = o.getElementsByTagName("polygon");
        if (pol.getLength() > 0) {
            String[] pts = ((Element) pol.item(0)).getAttribute("points").trim().split("\\s+");
            float y = mapH - yT;
            float minx = Float.MAX_VALUE, maxx = -Float.MAX_VALUE, miny = Float.MAX_VALUE, maxy = -Float.MAX_VALUE;
            for (String p : pts) {
                String[] xy = p.split(",");
                float vx = x + Float.parseFloat(xy[0]), vy = y - Float.parseFloat(xy[1]);
                minx = Math.min(minx, vx); maxx = Math.max(maxx, vx); miny = Math.min(miny, vy); maxy = Math.max(maxy, vy);
            }
            return new R(minx, miny, maxx - minx, maxy - miny);
        }
        float w = num(o, "width"), h = num(o, "height");
        return new R(x, mapH - yT - h, w, h);
    }

    List<Element> oggetti(Document d, String nome)
    {
        List<Element> l = new ArrayList<>();
        NodeList os = d.getElementsByTagName("object");
        for (int i = 0; i < os.getLength(); i++) {
            Element o = (Element) os.item(i);
            if (nome.equals(o.getAttribute("name"))) l.add(o);
        }
        return l;
    }

    void carica(File cartella, int livello) throws Exception
    {
        File fp = new File(cartella, "mappa" + livello + "_presente.tmx");
        File fx = new File(cartella, "mappa" + livello + "_passato.tmx");
        nEp = fx.exists() ? 2 : 1;
        for (int ep = 0; ep < nEp; ep++) {
            Document d = doc(ep == 0 ? fp : fx);
            Element map = d.getDocumentElement();
            float mapH = num(map, "height") * num(map, "tileheight");
            float mapW = num(map, "width") * num(map, "tilewidth");
            mondoW = mapW; mondoH = mapH;   // l'ultima mappa caricata imposta i confini
            if (ep == 0) {
                Map<String, String> p = props(map);
                dim = Float.parseFloat(p.getOrDefault("dimensionePersonaggio", "57.6"));
                uscita = p.getOrDefault("uscita", "tocco");
            }
            solidi[ep] = new ArrayList<>(); kill[ep] = new ArrayList<>();
            for (Element o : oggetti(d, "solid")) solidi[ep].add(rett(o, mapH));
            for (Element o : oggetti(d, "crollo")) {
                Crollo c = new Crollo(); c.r = rett(o, mapH); c.ep = ep;
                c.trema = Float.parseFloat(props(o).getOrDefault("ritardo", "0.55"));
                crolli.add(c);
            }
            // le spine "a scatto" uccidono come le trappole normali (scattano prima che tu le superi)
            for (Element o : oggetti(d, "scatto")) kill[ep].add(rett(o, mapH));
            for (Element o : oggetti(d, "kill")) kill[ep].add(rett(o, mapH));
            List<Element> f = oggetti(d, "finish");
            usc[ep] = f.isEmpty() ? null : rett(f.get(0), mapH);
            for (Element o : oggetti(d, "scarabeo")) { scR.add(rett(o, mapH)); scEp.add(ep); }
            for (Element o : oggetti(d, "gabbia")) { gabR.add(rett(o, mapH)); gabEp.add(ep); }
            for (Element o : oggetti(d, "leva")) { levR.add(rett(o, mapH)); levEp.add(ep); }
            for (Element o : oggetti(d, "mobile")) {
                Mobile m = new Mobile(); m.base = rett(o, mapH); m.ep = ep;
                Map<String, String> p = props(o);
                m.dx = Float.parseFloat(p.getOrDefault("dx", "120"));
                m.dy = Float.parseFloat(p.getOrDefault("dy", "0"));
                m.durata = Math.max(0.5f, Float.parseFloat(p.getOrDefault("durata", "2.5")));
                mobili.add(m);
            }
            List<Element> s = oggetti(d, "start");
            start[ep] = s.isEmpty() ? null : rett(s.get(0), mapH);
            if (ep == 0) {
                List<Element> a = oggetti(d, "artefatto");
                if (a.isEmpty()) a = oggetti(d, "object");
                artefatto = a.isEmpty() ? null : rett(a.get(0), mapH);
                for (Element o : oggetti(d, "geroglifico")) tav.add(rett(o, mapH));
            }
        }
        if (uscita.equals("passato") && usc[0] != null) { solidi[0].add(usc[0]); usc[0] = null; }
        if (System.getenv("MOBILI_STATICI") != null) {
            // rilassamento: la piattaforma mobile c'e' contemporaneamente in tutti i punti del suo percorso
            List<Mobile> nuove = new ArrayList<>();
            for (Mobile m : mobili) {
                int passi = (int) Math.ceil(Math.max(Math.abs(m.dx), Math.abs(m.dy)) / 4f);
                for (int k = 0; k <= passi; k++) {
                    Mobile c = new Mobile(); c.ep = m.ep; c.dx = 0; c.dy = 0; c.durata = 1000;
                    c.base = new R(m.base.x + m.dx * k / passi, m.base.y + m.dy * k / passi, m.base.w, m.base.h);
                    nuove.add(c);
                }
            }
            mobili.clear(); mobili.addAll(nuove);
        }
        scInGabbia = new boolean[scR.size()];
        for (int i = 0; i < scR.size(); i++)
            for (int g = 0; g < gabR.size(); g++) {
                R a = gabR.get(g), b = scR.get(i);
                if (gabEp.get(g).equals(scEp.get(i)) && overlaps(a.x, a.y, a.w, a.h, b)) scInGabbia[i] = true;
            }
        // il personaggio: vertici e sensore come in Esploratore
        hbL = dim * LATO; hbR = dim * (1 - LATO); hbB = dim * PIEDI; hbT = dim * TESTA;
        sensW = dim * (1 - 2 * LATO) * 0.9f;
    }

    float hbL, hbR, hbB, hbT, sensW;

    // ------------------------------------------------------------- stato
    static final class S implements Cloneable {
        float x, y, vy, coyote, buffer, cooldown, t;
        int ep, suMobile = -1, mask, tavMask;
        boolean aTerra, held, leva, art, morto, finito;
        byte[] cStato; float[] cTimer;
        int indice = -1; int costo;
        S copia() {
            try {
                S n = (S) super.clone();
                n.cStato = cStato.clone(); n.cTimer = cTimer.clone();
                return n;
            } catch (CloneNotSupportedException e) { throw new RuntimeException(e); }
        }
    }

    float mobX(Mobile m, float t) { return m.base.x + m.dx * fase(m, t); }
    float mobY(Mobile m, float t) { return m.base.y + m.dy * fase(m, t); }
    static float fase(Mobile m, float t)
    {
        float p = (t / m.durata) % 2f;
        if (p > 1f) p = 2f - p;
        return p * p * (3 - 2 * p);
    }

    boolean solidoAttivo(S s, int i) { return true; }

    /** Solidi dell'epoca attuale, compresi i crolli non caduti. */
    int cacheEp = -1; long cacheCrolli = -1;
    void solidiCorrenti(S s, List<R> out)
    {
        long cc = 0;
        for (int i = 0; i < crolli.size(); i++) cc = cc * 3 + s.cStato[i];
        if (cacheEp == s.ep && cacheCrolli == cc) return;
        cacheEp = s.ep; cacheCrolli = cc;
        out.clear();
        out.addAll(solidi[s.ep]);
        for (int i = 0; i < crolli.size(); i++)
            if (crolli.get(i).ep == s.ep && s.cStato[i] != 2) out.add(crolli.get(i).r);
    }

    final List<R> tmp = new ArrayList<>();

    /** libGDX Intersector.overlapConvexPolygons con MTV, per due rettangoli (giocatore = poly1). */
    float[] mtv(float ax, float ay, float aw, float ah, R b)
    {
        float[] A = { ax, ay, ax + aw, ay, ax + aw, ay + ah, ax, ay + ah };
        float[] B = { b.x, b.y, b.x + b.w, b.y, b.x + b.w, b.y + b.h, b.x, b.y + b.h };
        float[] res = { Float.MAX_VALUE, 0, 0 };   // depth, nx, ny
        if (!assi(B, A, res, true)) return null;
        if (!assi(A, B, res, false)) return null;
        return res;
    }

    static boolean assi(float[] v1, float[] v2, float[] mtv, boolean shifted)
    {
        for (int i = 0; i < 8; i += 2) {
            float x1 = v1[i], y1 = v1[i + 1], x2 = v1[(i + 2) % 8], y2 = v1[(i + 3) % 8];
            float axX = y1 - y2, axY = -(x1 - x2);
            float len = (float) Math.sqrt(axX * axX + axY * axY);
            axX /= len; axY /= len;
            float minA = Float.MAX_VALUE, maxA = -Float.MAX_VALUE, minB = Float.MAX_VALUE, maxB = -Float.MAX_VALUE;
            for (int v = 0; v < 8; v += 2) { float p = v1[v] * axX + v1[v + 1] * axY; minA = Math.min(minA, p); maxA = Math.max(maxA, p); }
            for (int v = 0; v < 8; v += 2) { float p = v2[v] * axX + v2[v + 1] * axY; minB = Math.min(minB, p); maxB = Math.max(maxB, p); }
            if (maxA <= minB || maxB <= minA) return false;
            float o = Math.min(maxA, maxB) - Math.max(minA, minB);
            boolean aCb = minA < minB && maxA > maxB, bCa = minB < minA && maxB > maxA;
            float mins = 0, maxs = 0;
            if (aCb || bCa) { mins = Math.abs(minA - minB); maxs = Math.abs(maxA - maxB); o += Math.min(mins, maxs); }
            if (mtv[0] > o) {
                mtv[0] = o;
                boolean c = shifted ? (minA < minB) : (minA > minB);
                if (!c) { axX = -axX; axY = -axY; }
                if (aCb || bCa) { if (!(mins > maxs)) { axX = -axX; axY = -axY; } }
                mtv[1] = axX; mtv[2] = axY;
            }
        }
        return true;
    }

    void collisioni(S s, List<R> sol, boolean orizzontale)
    {
        for (R o : sol) {
            float ax = s.x + hbL, ay = s.y + hbB, aw = hbR - hbL, ah = hbT - hbB;
            if (!overlaps(ax, ay, aw, ah, o)) continue;
            float yPrima = s.y;
            float[] m = mtv(ax, ay, aw, ah, o);
            if (m == null) continue;
            if (FRAGILE > 0) {   // urto proprio sullo spigolo: il risultato dipende da arrotondamenti minimi
                float ox = Math.min(ax + aw - o.x, o.x + o.w - ax), oy = Math.min(ay + ah - o.y, o.y + o.h - ay);
                if (Math.abs(ox - oy) < FRAGILE) { s.morto = true; return; }
            }
            s.x += m[1] * m[0];
            s.y += m[2] * m[0];
            boolean spintoGiu = s.y < yPrima;
            if (orizzontale) {
                if (spintoGiu) s.y = yPrima;
                else vxCorrente = 0;
            } else {
                if (spintoGiu && s.vy <= 0) s.y = yPrima;
                if (Math.abs(m[2]) >= Math.abs(m[1])) s.vy = 0;
            }
        }
    }

    float vxCorrente;

    boolean bloccato(S s, int dest)
    {
        float m = 2f;
        float ax = s.x + hbL + m, ay = s.y + hbB + m, aw = (hbR - hbL) - 2 * m, ah = (hbT - hbB) - 2 * m;
        for (R o : solidi[dest]) if (overlaps(ax, ay, aw, ah, o)) return true;
        for (int i = 0; i < crolli.size(); i++)
            if (crolli.get(i).ep == dest && s.cStato[i] != 2 && overlaps(ax, ay, aw, ah, crolli.get(i).r)) return true;
        return false;
    }

    boolean hb(S s, R o) { return overlaps(s.x + hbL, s.y + hbB, hbR - hbL, hbT - hbB, o); }

    int tuttiScarabei;
    boolean serveArtefatto, contaTavolette;

    /** Un fotogramma di LevelScreen.update. */
    void passo(S s, int dir)
    {
        s.cooldown -= DT;
        // piattaforme mobili
        if (!mobili.isEmpty()) {
            float t0 = s.t; s.t += DT;
            if (s.suMobile >= 0) {
                Mobile m = mobili.get(s.suMobile);
                s.x += mobX(m, s.t) - mobX(m, t0);
                s.y += mobY(m, s.t) - mobY(m, t0);
            }
        }
        vxCorrente = dir * VEL_X;
        s.buffer -= DT; s.coyote -= DT;
        s.vy -= GRAV * DT;
        s.vy = Math.max(-VEL_MAX_Y, Math.min(VEL_MAX_Y, s.vy));
        solidiCorrenti(s, tmp);
        s.x += vxCorrente * DT;
        collisioni(s, tmp, true);
        float piediPrima = s.y + hbB;
        s.y += s.vy * DT;
        collisioni(s, tmp, false);
        // atterraggio sulle mobili
        s.suMobile = -1;
        if (s.vy <= 0) {
            float hx = s.x + hbL, hw = hbR - hbL;
            for (int i = 0; i < mobili.size(); i++) {
                Mobile m = mobili.get(i);
                if (m.ep != s.ep) continue;
                float mx = mobX(m, s.t), my = mobY(m, s.t);
                if (!(hx < mx + m.base.w && hx + hw > mx)) continue;
                float top = my + m.base.h;
                float topPrima = top - (my - mobY(m, s.t - DT));
                float piedi = s.y + hbB;
                if (piediPrima >= topPrima - 2f && piedi <= top + 0.5f) {
                    s.y += top - piedi; s.vy = 0; s.suMobile = i; break;
                }
            }
        }
        // restaNelMondo
        float sx = s.x + hbL, dx = s.x + hbR, giu = s.y + hbB, su = s.y + hbT;
        if (sx < 0) s.x -= sx;
        if (dx > mondoW) s.x += mondoW - dx;
        if (giu < 0) { s.y -= giu; if (s.vy < 0) s.vy = 0; }
        if (su > mondoH) { s.y += mondoH - su; if (s.vy > 0) s.vy = 0; }
        // aggiornaStato
        float senX = s.x + dim * 0.5f - sensW / 2f, senY = s.y + hbB - 3;
        boolean tocca = false;
        for (R o : tmp) if (overlaps(senX, senY, sensW, 4, o)) { tocca = true; break; }
        if (!tocca) for (int i = 0; i < mobili.size(); i++) {
            Mobile m = mobili.get(i);
            if (m.ep != s.ep) continue;
            float mx = mobX(m, s.t), my = mobY(m, s.t);
            if (senX < mx + m.base.w && senX + sensW > mx && senY < my + m.base.h && senY + 4 > my) { tocca = true; break; }
        }
        s.aTerra = s.vy <= 0 && tocca;
        if (s.aTerra) s.coyote = COYOTE;
        if (s.buffer > 0 && s.coyote > 0) {
            s.vy = VEL_SALTO; s.buffer = 0; s.coyote = 0; s.aTerra = false; s.held = true;
        }
        // crolli
        float hx = s.x + hbL, hy = s.y + hbB, hw = hbR - hbL, hh = hbT - hbB;
        for (int i = 0; i < crolli.size(); i++) {
            Crollo c = crolli.get(i);
            if (c.ep == s.ep && s.aTerra && s.cStato[i] != 2 && overlaps(hx + 2, s.y + hbB - 3, hw - 4, 5, c.r)) {
                if (s.cStato[i] == 0) { s.cStato[i] = 1; s.cTimer[i] = c.trema; }
            }
            boolean occ = overlaps(hx, hy, hw, hh, c.r);
            if (s.cStato[i] == 1) {
                s.cTimer[i] -= DT;
                if (s.cTimer[i] <= 0) { s.cStato[i] = 2; s.cTimer[i] = 3f; }
            } else if (s.cStato[i] == 2) {
                s.cTimer[i] -= DT;
                if (s.cTimer[i] <= 0 && !occ) { s.cStato[i] = 0; s.cTimer[i] = 0; }
            }
        }
        // trappole
        for (R k : kill[s.ep]) if (RIDUCI_KILL > 0 ? overlaps(s.x + hbL + RIDUCI_KILL, s.y + hbB + RIDUCI_KILL, hbR - hbL - 2 * RIDUCI_KILL, hbT - hbB - 2 * RIDUCI_KILL, k) : hb(s, k)) { s.morto = true; return; }
        // scarabei
        for (int i = 0; i < scR.size(); i++) {
            if ((s.mask & (1 << i)) != 0 || scEp.get(i) != s.ep) continue;
            if (scInGabbia[i] && !s.leva) continue;
            if (hb(s, scR.get(i))) s.mask |= 1 << i;
        }
        // uscita al contatto
        if (uscita.equals("tocco") || uscita.equals("passato")) {
            R u = usc[s.ep];
            if (u != null && hb(s, u)) s.finito = true;
        }
    }

    boolean completo(S s)
    {
        if (s.mask != tuttiScarabei) return false;
        if (serveArtefatto && !s.art) return false;
        if (contaTavolette && s.tavMask != (1 << tav.size()) - 1) return false;
        return true;
    }

    /** Premere E: stessa priorita' di LevelScreen.interagisci. Ritorna false se non fa niente di utile. */
    boolean interagisci(S s)
    {
        R u = usc[s.ep];
        if (u != null && hb(s, u) && (uscita.equals("porta") || uscita.equals("tesoro"))) {
            if (!completo(s)) return false;
            s.finito = true; return true;
        }
        if (!s.art && s.ep == 0 && artefatto != null && hb(s, artefatto)) { s.art = true; return true; }
        if (s.ep == 0) for (int i = 0; i < tav.size(); i++) if (hb(s, tav.get(i))) {
            if (!contaTavolette || (s.tavMask & (1 << i)) != 0) return false;
            s.tavMask |= 1 << i; return true;
        }
        for (int i = 0; i < levR.size(); i++)
            if (levEp.get(i) == s.ep && !s.leva && hb(s, levR.get(i))) { s.leva = true; return true; }
        return false;
    }

    // ------------------------------------------------------------- ricerca
    int K = 4;
    float qCaduta = 0.5f, qxAria = 6, qyAria = 6;
    // albero della ricerca (per ricostruire il piano): genitore e azione di ogni stato
    int[] genitore = new int[1 << 20]; byte[] azioneDi = new byte[1 << 20]; int nStati = 0;
    int registra(int g, int a) {
        if (nStati == genitore.length) { genitore = Arrays.copyOf(genitore, nStati * 2); azioneDi = Arrays.copyOf(azioneDi, nStati * 2); }
        genitore[nStati] = g; azioneDi[nStati] = (byte) a; return nStati++;
    }
    float qx = 2, qy = 2, qvy = 20;

    long chiave(S s)
    {
        long h = 1469598103934665603L;
        if (s.aTerra) {          // a terra: posizione fine
            h = mix(h, Math.round((s.x + OFF * qx) / qx));
            h = mix(h, Math.round((s.y + OFF * qy) / qy));
            h = mix(h, s.suMobile >= 0 ? Math.round(s.vy) : 0);
        } else {                 // in aria: griglia piu' grossa
            h = mix(h, Math.round((s.x + OFF * qxAria) / qxAria) + 100000);
            h = mix(h, Math.round((s.y + OFF * qyAria) / qyAria));
            h = mix(h, Math.round((s.vy + OFF * qvy) / qvy));
        }
        h = mix(h, s.ep);
        h = mix(h, s.aTerra ? 1 : 0);
        h = mix(h, s.coyote > 0 ? Math.round(s.coyote / DT) : 0);
        h = mix(h, s.buffer > 0 ? Math.round(s.buffer / DT) : 0);
        h = mix(h, s.cooldown > 0 ? (int) Math.ceil(s.cooldown / (K * DT)) : 0);
        h = mix(h, s.held ? 1 : 0);
        h = mix(h, s.suMobile);
        h = mix(h, s.mask);
        h = mix(h, s.tavMask);
        h = mix(h, (s.leva ? 1 : 0) | (s.art ? 2 : 0));
        float cx = s.x + dim / 2f, cy = s.y + dim / 2f;
        for (int i = 0; i < s.cStato.length; i++) {
            h = mix(h, s.cStato[i]);
            if (s.cStato[i] == 0) continue;
            boolean vicino = !VICINANZA || vicino(cx, cy, crolli.get(i).r, 0, 0, RAGGIO);
            if (!vicino) continue;
            if (s.cStato[i] == 1) h = mix(h, (int) Math.ceil(s.cTimer[i] / (K * DT)));
            if (s.cStato[i] == 2) h = mix(h, 1000 + (int) Math.ceil(s.cTimer[i] / qCaduta));
        }
        for (Mobile m : mobili)
            if ((m.dx != 0 || m.dy != 0) && (!FASE_SOLO_A_TERRA || s.aTerra || s.suMobile >= 0)
                    && (!VICINANZA || vicino(cx, cy, m.base, m.dx, m.dy, RAGGIO)))
                h = mix(h, 5000 + (int) Math.floor((s.t % (2 * m.durata)) / qFase));
        return h;
    }

    static boolean VICINANZA = true;
    static float qFase = 0.2f, OFF = 0f;
    static float FRAGILE = System.getenv("FRAGILE") != null ? Float.parseFloat(System.getenv("FRAGILE")) : 0f;
    static boolean FASE_SOLO_A_TERRA = System.getenv("FASE_A_TERRA") != null;
    static float RAGGIO = 200;

    /** Il punto e' entro "raggio" px dal rettangolo (allargato dello spostamento dx,dy)? */
    static boolean vicino(float px, float py, R r, float dx, float dy, float raggio)
    {
        float x0 = Math.min(r.x, r.x + dx), x1 = Math.max(r.x + r.w, r.x + r.w + dx);
        float y0 = Math.min(r.y, r.y + dy), y1 = Math.max(r.y + r.h, r.y + r.h + dy);
        float ddx = Math.max(0, Math.max(x0 - px, px - x1)), ddy = Math.max(0, Math.max(y0 - py, py - y1));
        return ddx <= raggio && ddy <= raggio;
    }

    static long mix(long h, long v)
    {
        h ^= v + 0x9E3779B97F4A7C15L + (h << 6) + (h >>> 2);
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ (h >>> 31);
    }

    // azione: bit0-1 dir(0=0,1=-1,2=+1)  bit2 salto  bit3 rilascio  bit4 viaggio  bit5 E
    S risolvi(int epInizio, boolean conArtefatto, long limiteStati)
    {
        S s0 = new S();
        R st = start[epInizio] != null ? start[epInizio] : start[1 - epInizio];
        s0.ep = epInizio;
        s0.x = (st.x + st.w / 2f) - dim / 2f;
        s0.y = st.y - dim * PIEDI;
        s0.cStato = new byte[crolli.size()]; s0.cTimer = new float[crolli.size()];
        s0.art = conArtefatto;
        s0.cooldown = 0;
        for (int f = 0; f < ASSESTA; f++) passo(s0, 0);
        tuttiScarabei = (1 << scR.size()) - 1;
        serveArtefatto = artefatto != null && !conArtefatto;

        LongHashSet visti = new LongHashSet(1 << 22);
        int nc = crolli.size();
        s0.indice = registra(-1, 0);
        visti.add(chiave(s0));
        CodaStati corrente = new CodaStati(nc), prossimo = new CodaStati(nc);
        corrente.push(s0);
        int costo = 0;
        long esplorati = 0;
        S s = new S(); s.cStato = new byte[nc]; s.cTimer = new float[nc];
        while (true) {
            if (corrente.vuota()) {
                if (prossimo.vuota()) return null;
                if (MISTO) prossimo.inverti();   // i semi del nuovo strato in ordine di arrivo, i loro figli in profondita'
                CodaStati t = corrente; corrente = prossimo; prossimo = t;
                costo++;
                continue;
            }
            corrente.pop(s); int idx = s.indice; s.costo = costo;
            if ((esplorati & 0xFFFFF) == 0) System.err.println("  esplorati " + esplorati + " costo " + costo + " coda " + corrente.size() + "+" + prossimo.size());
            if (++esplorati > limiteStati) { System.err.println("limite stati raggiunto"); return null; }
            boolean puoSaltare = s.aTerra || s.coyote > 0 || s.vy < 0;
            // un tasto va lasciato prima di poterlo ripremere (nessuno lo lascia e ripreme nello stesso fotogramma)
            boolean puoTagliare = s.held;
            if (s.held) puoSaltare = false;
            boolean puoViaggiare = nEp == 2 && s.art && s.cooldown <= 0 && !bloccato(s, 1 - s.ep);
            for (int a = 0; a < 64; a++) {
                int dir = a & 3; if (dir == 3) continue;
                boolean salto = (a & 4) != 0, rilascio = (a & 8) != 0, viaggio = (a & 16) != 0, e = (a & 32) != 0;
                if (salto && !puoSaltare) continue;
                if (rilascio && !puoTagliare) continue;
                if (salto && rilascio) continue;
                if (viaggio && !puoViaggiare) continue;
                S n = s.copia();
                n.costo = costo;
                if (viaggio) {
                    n.ep = 1 - n.ep; n.cooldown = 0.45f; n.suMobile = -1; n.costo++;
                }
                boolean artPrima = n.art;
                if (e && !interagisci(n)) continue;
                int codice = a | ((e && !artPrima && n.art) ? 64 : 0);
                if (salto) { n.buffer = BUFFER; n.held = true; }
                if (rilascio) { if (n.vy > 0) n.vy *= TAGLIO; n.held = false; }
                int d = dir == 0 ? 0 : (dir == 1 ? -1 : 1);
                for (int f = 0; f < K && !n.morto && !n.finito; f++) passo(n, d);
                if (n.morto) continue;
                if (n.finito) {
                    if (completo(n)) { n.indice = registra(idx, codice); return n; }
                    continue;
                }
                long k = chiave(n);
                if (!visti.add(k)) continue;
                n.indice = registra(idx, codice);
                if (n.costo == costo) corrente.push(n); else prossimo.push(n);
            }
        }
    }

    String verifica(int ep, boolean conArt, List<Integer> az)
    {
        S s = new S();
        R st = start[ep] != null ? start[ep] : start[1 - ep];
        s.ep = ep; s.x = (st.x + st.w / 2f) - dim / 2f; s.y = st.y - dim * PIEDI;
        s.cStato = new byte[crolli.size()]; s.cTimer = new float[crolli.size()]; s.art = conArt;
        tuttiScarabei = (1 << scR.size()) - 1;
        serveArtefatto = artefatto != null && !conArt;
        for (int f = 0; f < ASSESTA; f++) passo(s, 0);
        int viaggi = 0;
        for (int a : az) {
            int dir = a & 3;
            if ((a & 16) != 0) {
                if (!(nEp == 2 && s.art && s.cooldown <= 0 && !bloccato(s, 1 - s.ep))) return "FALLITO (viaggio non possibile)";
                s.ep = 1 - s.ep; s.cooldown = 0.45f; s.suMobile = -1; viaggi++;
            }
            if ((a & 32) != 0) interagisci(s);
            if ((a & 4) != 0) { s.buffer = BUFFER; s.held = true; }
            if ((a & 8) != 0) { if (s.vy > 0) s.vy *= TAGLIO; s.held = false; }
            for (int f = 0; f < K; f++) {
                passo(s, dir == 0 ? 0 : (dir == 1 ? -1 : 1));
                if (s.morto) return "FALLITO (morto)";
                if (s.finito) return completo(s) ? "OK viaggi=" + viaggi : "FALLITO (uscito senza tutto)";
            }
        }
        return "FALLITO (non arrivato)";
    }

    /** Rigioca il piano e scrive la posizione a ogni fotogramma (per il confronto con il gioco). */
    void traccia(int ep, boolean conArt, List<Integer> az, String file) throws Exception
    {
        S s = new S();
        R st = start[ep] != null ? start[ep] : start[1 - ep];
        s.ep = ep; s.x = (st.x + st.w / 2f) - dim / 2f; s.y = st.y - dim * PIEDI;
        s.cStato = new byte[crolli.size()]; s.cTimer = new float[crolli.size()]; s.art = conArt;
        for (int f = 0; f < ASSESTA; f++) passo(s, 0);
        try (PrintWriter w = new PrintWriter(file)) {
            int fr = 0;
            for (int a : az) {
                int dir = a & 3;
                if ((a & 16) != 0) { s.ep = 1 - s.ep; s.cooldown = 0.45f; s.suMobile = -1; }
                if ((a & 32) != 0) interagisci(s);
                if ((a & 4) != 0) s.buffer = BUFFER;
                if ((a & 8) != 0) { if (s.vy > 0) s.vy *= TAGLIO; s.held = false; }
                for (int f = 0; f < K; f++) {
                    passo(s, dir == 0 ? 0 : (dir == 1 ? -1 : 1));
                    w.println("TR " + (++fr) + " " + s.x + " " + s.y + " " + s.vy + " " + s.ep + " " + (s.aTerra ? 1 : 0) + " m" + s.mask + (s.morto ? " MORTO" : ""));
                    if (s.morto || s.finito) return;
                }
            }
        }
    }

    Archivio arch;

    /** Stati salvati in array di primitivi a blocchi (molto meno memoria degli oggetti). */
    static final class Archivio {
        static final int B = 1 << 20;
        final int nc;
        final ArrayList<float[]> f = new ArrayList<>();   // 7 float per stato + nc timer
        final ArrayList<int[]> p = new ArrayList<>();     // dati impacchettati
        final ArrayList<byte[]> c = new ArrayList<>();    // stati dei crolli
        final int nf;
        Archivio(int nc) { this.nc = nc; this.nf = 7 + nc; }
        void assicura(int i) {
            while (i >= p.size() * B) { f.add(new float[B * nf]); p.add(new int[B]); c.add(new byte[Math.max(1, B * nc)]); }
        }
        void salva(int i, S s) {
            assicura(i);
            float[] F = f.get(i / B); int o = (i % B) * nf;
            F[o] = s.x; F[o + 1] = s.y; F[o + 2] = s.vy; F[o + 3] = s.coyote; F[o + 4] = s.buffer; F[o + 5] = s.cooldown; F[o + 6] = s.t;
            for (int k = 0; k < nc; k++) F[o + 7 + k] = s.cTimer[k];
            p.get(i / B)[i % B] = s.ep | (s.aTerra ? 2 : 0) | (s.held ? 4 : 0) | (s.leva ? 8 : 0) | (s.art ? 16 : 0)
                    | ((s.suMobile + 1) << 5) | (s.mask << 11) | (s.tavMask << 19);
            byte[] C = c.get(i / B); for (int k = 0; k < nc; k++) C[(i % B) * nc + k] = s.cStato[k];
        }
        void carica(int i, S s) {
            float[] F = f.get(i / B); int o = (i % B) * nf;
            s.x = F[o]; s.y = F[o + 1]; s.vy = F[o + 2]; s.coyote = F[o + 3]; s.buffer = F[o + 4]; s.cooldown = F[o + 5]; s.t = F[o + 6];
            for (int k = 0; k < nc; k++) s.cTimer[k] = F[o + 7 + k];
            int v = p.get(i / B)[i % B];
            s.ep = v & 1; s.aTerra = (v & 2) != 0; s.held = (v & 4) != 0; s.leva = (v & 8) != 0; s.art = (v & 16) != 0;
            s.suMobile = ((v >> 5) & 63) - 1; s.mask = (v >> 11) & 255; s.tavMask = (v >> 19) & 255;
            byte[] C = c.get(i / B); for (int k = 0; k < nc; k++) s.cStato[k] = C[(i % B) * nc + k];
            s.morto = false; s.finito = false;
        }
    }

    static boolean FIFO = false, MISTO = false;

    /** Pila (LIFO) o coda (FIFO) di interi. */
    /** Pila/coda di stati salvati in array di primitivi. */
    static final class CodaStati {
        final int nc, nf;
        float[] f; int[] p; byte[] c; int[] ind; int n, testa;
        CodaStati(int nc) { this.nc = nc; nf = 7 + nc; int cap = 1 << 16; f = new float[cap * nf]; p = new int[cap]; c = new byte[Math.max(1, cap * nc)]; ind = new int[cap]; }
        boolean vuota() { return n == testa; }
        int size() { return n - testa; }
        void cresci() {
            if (testa > 0) {   // prima compatta
                int m = n - testa;
                System.arraycopy(f, testa * nf, f, 0, m * nf); System.arraycopy(p, testa, p, 0, m);
                if (nc > 0) System.arraycopy(c, testa * nc, c, 0, m * nc);
                System.arraycopy(ind, testa, ind, 0, m); n = m; testa = 0;
                if (n < p.length * 3 / 4) return;
            }
            int cap = p.length * 2;
            f = Arrays.copyOf(f, cap * nf); p = Arrays.copyOf(p, cap); c = Arrays.copyOf(c, Math.max(1, cap * nc)); ind = Arrays.copyOf(ind, cap);
        }
        void push(S s) {
            if (n == p.length) cresci();
            int o = n * nf;
            f[o] = s.x; f[o + 1] = s.y; f[o + 2] = s.vy; f[o + 3] = s.coyote; f[o + 4] = s.buffer; f[o + 5] = s.cooldown; f[o + 6] = s.t;
            for (int k = 0; k < nc; k++) { f[o + 7 + k] = s.cTimer[k]; c[n * nc + k] = s.cStato[k]; }
            p[n] = s.ep | (s.aTerra ? 2 : 0) | (s.held ? 4 : 0) | (s.leva ? 8 : 0) | (s.art ? 16 : 0)
                    | ((s.suMobile + 1) << 5) | (s.mask << 11) | (s.tavMask << 19);
            ind[n] = s.indice; n++;
        }
        void pop(S s) {
            int i = FIFO ? testa++ : --n;
            int o = i * nf;
            s.x = f[o]; s.y = f[o + 1]; s.vy = f[o + 2]; s.coyote = f[o + 3]; s.buffer = f[o + 4]; s.cooldown = f[o + 5]; s.t = f[o + 6];
            for (int k = 0; k < nc; k++) { s.cTimer[k] = f[o + 7 + k]; s.cStato[k] = c[i * nc + k]; }
            int v = p[i];
            s.ep = v & 1; s.aTerra = (v & 2) != 0; s.held = (v & 4) != 0; s.leva = (v & 8) != 0; s.art = (v & 16) != 0;
            s.suMobile = ((v >> 5) & 63) - 1; s.mask = (v >> 11) & 255; s.tavMask = (v >> 19) & 255;
            s.indice = ind[i]; s.morto = false; s.finito = false;
            if (FIFO && testa == n) { testa = 0; n = 0; }
        }
        /** Inverte l'ordine (per l'ordine "misto": la pila restituira' prima il primo inserito). */
        void inverti() {
            int a = testa, b = n - 1; float[] tf = new float[nf];
            while (a < b) {
                System.arraycopy(f, a * nf, tf, 0, nf); System.arraycopy(f, b * nf, f, a * nf, nf); System.arraycopy(tf, 0, f, b * nf, nf);
                int t = p[a]; p[a] = p[b]; p[b] = t; t = ind[a]; ind[a] = ind[b]; ind[b] = t;
                for (int k = 0; k < nc; k++) { byte x = c[a * nc + k]; c[a * nc + k] = c[b * nc + k]; c[b * nc + k] = x; }
                a++; b--;
            }
        }
    }

    static final class IntPila {
        int[] a = new int[1 << 16]; int n, testa;
        void push(int v) {
            if (n == a.length) {
                if (testa > n / 2) { System.arraycopy(a, testa, a, 0, n - testa); n -= testa; testa = 0; }
                else a = Arrays.copyOf(a, n * 2);
            }
            a[n++] = v;
        }
        int pop() { if (FIFO) { int v = a[testa++]; if (testa == n) { testa = 0; n = 0; } return v; } return a[--n]; }
        boolean vuota() { return n == testa; }
    }

    static final class LongHashSet {
        long[] t; int n; boolean zero;
        LongHashSet(int cap) { t = new long[cap]; }
        boolean add(long k) {
            if (k == 0) { if (zero) return false; zero = true; return true; }
            if (n * 10 >= t.length * 7) rehash();
            int m = t.length - 1, i = (int) (k ^ (k >>> 32)) & m;
            while (t[i] != 0) { if (t[i] == k) return false; i = (i + 1) & m; }
            t[i] = k; n++; return true;
        }
        void rehash() {
            long[] o = t; t = new long[o.length * 2]; n = 0;
            for (long k : o) if (k != 0) add(k);
        }
    }

    public static void main(String[] a) throws Exception
    {
        Risolutore r = new Risolutore();
        int liv = Integer.parseInt(a[1]);
        r.carica(new File(a[0]), liv);
        int ep = Integer.parseInt(a[2]);
        if (a.length > 5 && a[3].equals("migliora")) {   // prova a togliere viaggi inutili da un piano
            if (System.getenv("DT") != null) DT = 1f / Float.parseFloat(System.getenv("DT"));
            if (System.getenv("ASSESTA") != null) ASSESTA = Integer.parseInt(System.getenv("ASSESTA"));
            List<Integer> az = new ArrayList<>();
            Scanner sc = new Scanner(new File(a[4]));
            r.K = sc.nextInt();
            while (sc.hasNextInt()) az.add(sc.nextInt());
            String v0 = r.verifica(ep, liv >= 3, az);
            if (!v0.startsWith("OK")) { System.out.println("L" + liv + " ep=" + ep + " piano non valido: " + v0); return; }
            boolean migliorato = true;
            while (migliorato) {
                migliorato = false;
                List<Integer> t = new ArrayList<>();
                for (int i = 0; i < az.size(); i++) if ((az.get(i) & 16) != 0) t.add(i);
                search:
                for (int x = 0; x < t.size(); x++) for (int y = x; y < t.size(); y++) {
                    List<Integer> b = new ArrayList<>(az);
                    b.set(t.get(x), b.get(t.get(x)) & ~16);
                    if (y != x) b.set(t.get(y), b.get(t.get(y)) & ~16);
                    if (r.verifica(ep, liv >= 3, b).startsWith("OK")) { az = b; migliorato = true; break search; }
                }
            }
            try (PrintWriter w = new PrintWriter(a[5])) { w.println(r.K); for (int x : az) w.println(x); }
            System.out.println("L" + liv + " ep=" + ep + " prima " + v0 + " dopo " + r.verifica(ep, liv >= 3, az));
            return;
        }
        if (a.length > 4 && a[3].equals("verifica")) {   // rigioca un piano: ce la fa? con quanti viaggi?
            if (System.getenv("DT") != null) DT = 1f / Float.parseFloat(System.getenv("DT"));
            if (System.getenv("ASSESTA") != null) ASSESTA = Integer.parseInt(System.getenv("ASSESTA"));
            List<Integer> az = new ArrayList<>();
            Scanner sc = new Scanner(new File(a[4]));
            r.K = sc.nextInt();
            while (sc.hasNextInt()) az.add(sc.nextInt());
            System.out.println("L" + liv + " ep=" + ep + " " + r.verifica(ep, liv >= 3, az));
            return;
        }
        if (a.length > 3) r.K = Integer.parseInt(a[3]);
        if (a.length > 4) r.qx = Float.parseFloat(a[4]);
        if (a.length > 5) r.qy = Float.parseFloat(a[5]);
        if (a.length > 6) r.qvy = Float.parseFloat(a[6]);
        if (a.length > 7) r.contaTavolette = a[7].equals("1");
        if (System.getenv("ESATTO") != null) VICINANZA = false;
        if (System.getenv("RILASSATO") != null) {   // fisica "piu' generosa": serve per il limite inferiore
            GRAV = 690; VEL_X = 152; RIDUCI_KILL = 1;
        }
        if ("fifo".equals(System.getenv("ORDINE"))) FIFO = true;
        if (System.getenv("OFFSET") != null) OFF = Float.parseFloat(System.getenv("OFFSET"));
        if ("misto".equals(System.getenv("ORDINE"))) MISTO = true;
        if (System.getenv("QFASE") != null) qFase = Float.parseFloat(System.getenv("QFASE"));
        if (System.getenv("RAGGIO") != null) RAGGIO = Float.parseFloat(System.getenv("RAGGIO"));
        long limite = System.getenv("LIMITE") != null ? Long.parseLong(System.getenv("LIMITE")) : 90_000_000L;
        if (System.getenv("DT") != null) DT = 1f / Float.parseFloat(System.getenv("DT"));
        if (System.getenv("ASSESTA") != null) ASSESTA = Integer.parseInt(System.getenv("ASSESTA"));
        if (System.getenv("QARIA") != null) { r.qxAria = r.qyAria = Float.parseFloat(System.getenv("QARIA")); }
        if (System.getenv("QCADUTA") != null) r.qCaduta = Float.parseFloat(System.getenv("QCADUTA"));
        if (ep == 1 && r.nEp == 1) { System.out.println("L" + liv + " ep=1 nessun passato"); return; }
        boolean conArt = liv >= 3;
        long t0 = System.currentTimeMillis();
        S fine = r.risolvi(ep, conArt, limite);
        long ms = System.currentTimeMillis() - t0;
        if (fine == null) { System.out.println("L" + liv + " ep=" + ep + " NESSUNA SOLUZIONE (" + ms + "ms, " + r.nStati + " stati)"); return; }
        System.out.println("L" + liv + " ep=" + ep + " viaggi=" + fine.costo + " (" + ms + "ms, " + r.nStati + " stati)");
        if (a.length > 8) {
            // piano: un'azione ogni K fotogrammi
            List<Integer> az = new ArrayList<>();
            for (int i = fine.indice; r.genitore[i] >= 0; i = r.genitore[i]) az.add((int) r.azioneDi[i]);
            Collections.reverse(az);
            try (PrintWriter w = new PrintWriter(a[8])) {
                w.println(r.K);
                for (int x : az) w.println(x);
            }
            if (System.getenv("TRACCIA") != null) r.traccia(ep, conArt, az, System.getenv("TRACCIA"));
        }
    }
}
