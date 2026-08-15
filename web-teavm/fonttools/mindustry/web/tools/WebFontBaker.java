package mindustry.web.tools;

import arc.files.*;
import arc.freetype.*;
import arc.freetype.FreeTypeFontGenerator.*;
import arc.graphics.*;
import arc.graphics.g2d.*;
import arc.graphics.g2d.Font.*;
import arc.graphics.g2d.PixmapPacker.*;
import arc.struct.*;
import mindustry.gen.*;

import java.util.*;

/**
 * Runs on the build JVM, never in GWT. It uses Arc's normal native FreeType
 * implementation to convert Mindustry's current WOFF/TTF files to BMFont pages
 * that Arc's pure-Java FontLoader can read in the browser.
 */
public final class WebFontBaker{
    private static final String logicChars = "\0ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz1234567890\"!`?'.,;:()[]{}<>|/@\\^$€-%+=#_&~*";

    private WebFontBaker(){}

    public static void main(String[] args){
        if(args.length != 2){
            throw new IllegalArgumentException("usage: WebFontBaker <Mindustry assets dir> <output dir>");
        }

        Fi assets = new Fi(args[0]);
        Fi output = new Fi(args[1]);
        output.mkdirs();
        output.emptyDirectory();

        // First boot targets the full Latin-1/default Arc character set. Current
        // Mindustry content icons are injected into def/outline later from the UI atlas.
        FreeTypeFontParameter defaultFont = parameter(18, FreeTypeFontGenerator.DEFAULT_CHARS);
        defaultFont.shadowColor = Color.darkGray;
        defaultFont.shadowOffsetY = 2;
        bake(assets.child("fonts/font.woff"), output, "default", defaultFont, 2048);

        FreeTypeFontParameter outline = parameter(18, FreeTypeFontGenerator.DEFAULT_CHARS);
        outline.borderWidth = 2f;
        outline.borderColor = Color.darkGray;
        bake(assets.child("fonts/font.woff"), output, "outline", outline, 2048);

        bake(assets.child("fonts/monospace.woff"), output, "monospace", parameter(16, FreeTypeFontGenerator.DEFAULT_CHARS), 2048);
        bake(assets.child("fonts/icon.ttf"), output, "icon", parameter(30, "\u0000" + Iconc.all), 2048);

        FreeTypeFontParameter iconLarge = parameter(48, "\u0000" + Iconc.all);
        iconLarge.borderWidth = 5f;
        iconLarge.borderColor = Color.darkGray;
        bake(assets.child("fonts/icon.ttf"), output, "iconLarge", iconLarge, 2048);

        bake(assets.child("fonts/logic.ttf"), output, "logic", parameter(16, logicChars), 2048);
        bake(assets.child("fonts/tech.ttf"), output, "tech", parameter(18, FreeTypeFontGenerator.DEFAULT_CHARS), 2048);

        System.out.println("Baked Mindustry web fonts to " + output.absolutePath());
    }

    private static FreeTypeFontParameter parameter(int size, String chars){
        FreeTypeFontParameter out = new FreeTypeFontParameter();
        out.size = size;
        out.incremental = false;
        out.characters = chars;
        out.magFilter = Texture.TextureFilter.linear;
        out.minFilter = Texture.TextureFilter.linear;
        return out;
    }

    private static void bake(Fi source, Fi output, String name, FreeTypeFontParameter param, int pageSize){
        if(!source.exists()) throw new IllegalArgumentException("Missing source font: " + source);

        PixmapPacker packer = new PixmapPacker(pageSize, pageSize, 2, true);
        param.packer = packer;

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(source);
        FreeTypeFontData data = new FreeTypeFontData();
        try{
            generator.generateData(param, data);
            writeFont(data, packer, output, name, param.size);
        }finally{
            generator.dispose();
            packer.dispose();
        }
    }

    /** Minimal AngelCode BMFont text writer, matching Arc FontData's parser. */
    private static void writeFont(FontData data, PixmapPacker packer, Fi dir, String name, int nominalSize){
        Seq<Page> pages = packer.getPages();
        String[] pageNames = new String[pages.size];
        for(int i = 0; i < pages.size; i++){
            pageNames[i] = pages.size == 1 ? name + ".png" : name + "_" + i + ".png";
            PixmapIO.writePng(dir.child(pageNames[i]), pages.get(i).getPixmap());
        }

        ArrayList<Glyph> glyphs = new ArrayList<>();
        for(Glyph[] page : data.glyphs){
            if(page == null) continue;
            for(Glyph glyph : page){
                if(glyph != null) glyphs.add(glyph);
            }
        }
        if(data.missingGlyph != null && !glyphs.contains(data.missingGlyph)) glyphs.add(data.missingGlyph);
        glyphs.sort(Comparator.comparingInt(g -> g.id));

        int lineHeight = Math.max(1, Math.round(data.lineHeight));
        int base = Math.round(data.capHeight + data.ascent);
        StringBuilder out = new StringBuilder(64 * 1024);
        out.append("info face=\"").append(name).append("\" size=").append(nominalSize)
            .append(" bold=0 italic=0 charset=\"\" unicode=1 stretchH=100 smooth=1 aa=2 padding=0,0,0,0 spacing=0,0\n");
        out.append("common lineHeight=").append(lineHeight).append(" base=").append(base)
            .append(" scaleW=").append(packer.getPageWidth()).append(" scaleH=").append(packer.getPageHeight())
            .append(" pages=").append(pageNames.length).append(" packed=0\n");
        for(int i = 0; i < pageNames.length; i++){
            out.append("page id=").append(i).append(" file=\"").append(pageNames[i]).append("\"\n");
        }
        out.append("chars count=").append(glyphs.size()).append('\n');
        for(Glyph g : glyphs){
            boolean empty = g.width == 0 || g.height == 0;
            int yoff = data.flipped ? g.yoffset : -(g.height + g.yoffset);
            out.append("char id=").append(g.id)
                .append(" x=").append(empty ? 0 : g.srcX)
                .append(" y=").append(empty ? 0 : g.srcY)
                .append(" width=").append(empty ? 0 : g.width)
                .append(" height=").append(empty ? 0 : g.height)
                .append(" xoffset=").append(g.xoffset)
                .append(" yoffset=").append(yoff)
                .append(" xadvance=").append(g.xadvance)
                .append(" page=").append(g.page)
                .append(" chnl=0\n");
        }

        int kerningCount = 0;
        StringBuilder kern = new StringBuilder();
        for(Glyph first : glyphs){
            for(Glyph second : glyphs){
                int amount = first.getKerning((char)second.id);
                if(amount != 0){
                    kerningCount++;
                    kern.append("kerning first=").append(first.id)
                        .append(" second=").append(second.id)
                        .append(" amount=").append(amount).append('\n');
                }
            }
        }
        out.append("kernings count=").append(kerningCount).append('\n').append(kern);
        dir.child(name + ".fnt").writeString(out.toString(), false);
    }
}
