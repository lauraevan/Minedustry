package mindustry.ui;

import arc.*;
import arc.Graphics.Cursor.*;
import arc.assets.loaders.*;
import arc.assets.loaders.FontLoader.*;
import arc.graphics.*;
import arc.graphics.Texture.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.Font.*;
import arc.math.geom.*;
import arc.scene.style.*;
import arc.struct.*;
import arc.util.*;
import mindustry.*;
import mindustry.game.*;
import mindustry.gen.*;

import java.io.*;

/**
 * GWT super-source for current Mindustry Fonts.
 *
 * It keeps Mindustry's public font/icon API and icon-atlas integration, but
 * consumes BMFont pages baked by WebFontBaker instead of invoking native
 * FreeType in the browser. Initial browser language coverage is Latin-1.
 */
public class Fonts{
    private static ObjectIntMap<String> unicodeIcons = new ObjectIntMap<>();
    private static IntMap<String> unicodeToName = new IntMap<>();
    private static ObjectMap<String, String> stringIcons = new ObjectMap<>();
    private static ObjectMap<String, TextureRegion> largeIcons = new ObjectMap<>();

    public static Font def, outline, icon, iconLarge, tech, logic, monospace;

    public static int getUnicode(String content){
        return unicodeIcons.get(content, 0);
    }

    public static String getUnicodeStr(String content){
        return stringIcons.get(content, "");
    }

    public static boolean hasUnicodeStr(String content){
        return stringIcons.containsKey(content);
    }

    public static void loadSystemCursors(){
        // Keep Arc system cursors unaliased so the web backend can map them to
        // native CSS cursors. Custom PNG cursor support is not needed to boot.
        Core.graphics.restoreCursor();
    }

    public static int cursorScale(){
        return 1;
    }

    private static FontParameter linear(){
        FontParameter p = new FontParameter();
        p.magFilter = TextureFilter.linear;
        p.minFilter = TextureFilter.linear;
        return p;
    }

    /** Keep Mindustry's logical asset names (default/outline/etc.) while
     * resolving them to the build-time baked BMFont files. FontLoader also
     * resolves page image paths through this resolver, so already-qualified
     * webfonts/*.png and *.fnt paths pass through unchanged. */
    private static arc.files.Fi resolveWebFont(String name){
        String normalized = name.replace('\\', '/');
        if(normalized.indexOf('/') >= 0 || normalized.endsWith(".png") || normalized.endsWith(".fnt")){
            return Core.files.internal(normalized);
        }
        return Core.files.internal("webfonts/" + normalized + ".fnt");
    }

    public static void loadFonts(){
        largeIcons.clear();
        Core.assets.load("default", Font.class, linear()).loaded = f -> Fonts.def = f;
        Core.assets.load("monospace", Font.class, linear()).loaded = f -> Fonts.monospace = f;
        Core.assets.load("icon", Font.class, linear()).loaded = f -> Fonts.icon = f;
        Core.assets.load("iconLarge", Font.class, linear()).loaded = f -> Fonts.iconLarge = f;
        Core.assets.load("logic", Font.class, linear()).loaded = f -> Fonts.logic = f;
    }

    /** Extra CJK fallback baking is a post-boot milestone; the base browser font is Latin-1. */
    public static void loadExtraFonts(){
    }

    public static @Nullable String unicodeToName(int unicode){
        return unicodeToName.get(unicode, () -> Iconc.codeToName.get(unicode));
    }

    public static TextureRegion getLargeIcon(String name){
        return largeIcons.get(name, () -> {
            var region = new TextureRegion();
            int code = Iconc.codes.get(name, '\uF8D4');
            var glyph = iconLarge.getData().getGlyph((char)code);
            if(glyph == null) return Core.atlas.find(name);
            region.set(iconLarge.getRegion(glyph.page).texture);
            region.set(glyph.u, glyph.v2, glyph.u2, glyph.v);
            return region;
        });
    }

    public static void registerIcon(String name, String regionName, int ch, TextureRegion region){
        int size = (int)(Fonts.def.getData().lineHeight / Fonts.def.getData().scaleY);

        unicodeIcons.put(name, ch);
        stringIcons.put(name, ((char)ch) + "");
        unicodeToName.put(ch, regionName);

        Vec2 out = Scaling.fit.apply(region.width, region.height, size, size);
        Glyph glyph = new Glyph();
        glyph.id = ch;
        glyph.srcX = 0;
        glyph.srcY = 0;
        glyph.width = (int)out.x;
        glyph.height = (int)out.y;
        glyph.u = region.u;
        glyph.v = region.v2;
        glyph.u2 = region.u2;
        glyph.v2 = region.v;
        glyph.xoffset = (size - glyph.width) / 2;
        glyph.yoffset = (size - glyph.height) / 2 - size;
        glyph.xadvance = size;
        glyph.kerning = null;
        glyph.fixedWidth = true;
        glyph.page = 0;
        Fonts.def.getData().setGlyph(ch, glyph);
        Fonts.outline.getData().setGlyph(ch, glyph);
    }

    public static void loadContentIcons(){
        Texture uitex = Core.atlas.find("logo").texture;
        try(var reader = Core.files.internal("icons/icons.properties").reader(Vars.bufferSize)){
            String line;
            while((line = reader.readLine()) != null){
                String[] split = line.split("=");
                String[] nametex = split[1].split("\\|");
                String character = split[0], texture = nametex[1];
                int ch = Integer.parseInt(character);
                TextureRegion region = Core.atlas.find(texture);
                if(region.texture != uitex) continue;
                registerIcon(nametex[0], texture, ch, region);
            }
        }catch(IOException e){
            throw new RuntimeException(e);
        }

        stringIcons.put("alphachan", stringIcons.get("alphaaaa"));
        for(Team team : Team.baseTeams){
            team.emoji = stringIcons.get(team.name, "");
        }
    }

    public static void loadContentIconsHeadless(){
        try(var reader = Core.files.internal("icons/icons.properties").reader(Vars.bufferSize)){
            String line;
            while((line = reader.readLine()) != null){
                String[] split = line.split("=");
                String[] nametex = split[1].split("\\|");
                int ch = Integer.parseInt(split[0]);
                unicodeIcons.put(nametex[0], ch);
                stringIcons.put(nametex[0], ((char)ch) + "");
            }
        }catch(IOException e){
            throw new RuntimeException(e);
        }
        stringIcons.put("alphachan", stringIcons.get("alphaaaa"));
        for(Team team : Team.baseTeams){
            team.emoji = stringIcons.get(team.name, "");
        }
    }

    /** Called from static startup code before the rest of the UI assets are queued. */
    public static void loadDefaultFont(){
        Core.assets.setLoader(Font.class, new FontLoader(Fonts::resolveWebFont));
        Core.assets.load("outline", Font.class, linear()).loaded = f -> Fonts.outline = f;
        Core.assets.load("tech", Font.class, linear()).loaded = f -> {
            Fonts.tech = f;
            Fonts.tech.getData().down *= 1.5f;
        };
    }

    /** Browser fonts use their own textures, so no native/UI pixmap merge is required. */
    public static void mergeFontAtlas(TextureAtlas atlas){
    }

    public static TextureRegionDrawable getGlyph(Font font, char glyph){
        Glyph found = font.getData().getGlyph(glyph);
        if(found == null){
            Log.warn("No icon found for glyph: @ (@)", glyph, (int)glyph);
            found = font.getData().getGlyph('F');
        }
        Glyph g = found;
        float size = Math.max(g.width, g.height);
        TextureRegionDrawable draw = new TextureRegionDrawable(new TextureRegion(font.getRegion(g.page).texture, g.u, g.v2, g.u2, g.v)){
            @Override
            public void draw(float x, float y, float width, float height){
                Draw.color(Tmp.c1.set(tint).mul(Draw.getColor()).toFloatBits());
                float cx = (int)(x + width / 2f - g.width / 2f);
                float cy = (int)(y + height / 2f - g.height / 2f);
                Draw.rect(region, cx + g.width / 2f, cy + g.height / 2f, g.width, g.height);
            }

            @Override
            public void draw(float x, float y, float originX, float originY, float width, float height, float scaleX, float scaleY, float rotation){
                width *= scaleX;
                height *= scaleY;
                Draw.color(Tmp.c1.set(tint).mul(Draw.getColor()).toFloatBits());
                float cx = (int)(x + width / 2f - g.width / 2f);
                float cy = (int)(y + height / 2f - g.height / 2f);
                Draw.rect(region, cx + g.width / 2f, cy + g.height / 2f, g.width * scaleX, g.height * scaleY, g.width / 2f, g.height / 2f, rotation);
            }

            @Override
            public float imageSize(){
                return size;
            }
        };
        draw.setMinWidth(size);
        draw.setMinHeight(size);
        return draw;
    }
}
