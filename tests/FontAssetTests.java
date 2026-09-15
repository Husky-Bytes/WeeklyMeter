import java.awt.Font;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Checks the actual bundled files, not an Android font rendering test. */
public final class FontAssetTests {
    public static void main(String[] args)throws Exception{
        Path directory=Paths.get(args[0]);int count=0;
        for(String name:new String[]{"gothic","rounded","serif","mono"}){
            Path path=directory.resolve(name+".ttf");
            Font font=Font.createFont(Font.TRUETYPE_FONT,path.toFile());count++;
            String sample="ChatGPT 0123456789%:. 초기화 조회 오전 오후 월 화 수 목 금 토 일 년";
            if(font.canDisplayUpTo(sample)!=-1)throw new AssertionError(name+": required glyph missing");count++;
            String license=new String(Files.readAllBytes(directory.resolve(name+"-OFL.txt")),StandardCharsets.UTF_8);
            if(!license.contains("SIL OPEN FONT LICENSE")||!license.contains("Copyright"))throw new AssertionError(name+": license missing");count++;
            System.out.println("Font asset OK: "+name+".ttf; Korean/date/numeric glyphs and license present.");
        }
        System.out.println("PASS: "+count+" bundled font asset checks (JDK parser only; no Android visual test).");
    }
}
