import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Inspect raw Java ZIP entry names: never normalize separators before validation. */
public final class ApkAssetTests {
    private static final long MAX_APK=64L*1024*1024,MAX_ASSET=16L*1024*1024;
    private static final String[] EXPECTED={
        "NOTICES.txt","NOTICES-en.txt","fonts/gothic.ttf","fonts/rounded.ttf","fonts/serif.ttf","fonts/mono.ttf",
        "fonts/gothic-OFL.txt","fonts/rounded-OFL.txt","fonts/serif-OFL.txt","fonts/mono-OFL.txt"
    };
    private static int checks;
    private static void check(boolean okay,String message){if(!okay)throw new AssertionError(message);checks++;}
    public static void main(String[] args)throws Exception{
        if(args.length!=2)throw new IllegalArgumentException("Usage: ApkAssetTests <apk-file> <source-assets-directory>");
        Path apk=Paths.get(args[0]),assets=Paths.get(args[1]).toAbsolutePath().normalize();
        check(Files.isRegularFile(apk,LinkOption.NOFOLLOW_LINKS)&&Files.size(apk)>0&&Files.size(apk)<=MAX_APK,"APK absent or over bounded 64 MiB limit");
        check(Files.isDirectory(assets,LinkOption.NOFOLLOW_LINKS),"Source assets directory absent or symbolic link");
        Set<String> expected=new LinkedHashSet<>();for(String name:EXPECTED)expected.add("assets/"+name);
        try(ZipFile zip=new ZipFile(apk.toFile(),StandardCharsets.UTF_8)){
            Set<String> names=new HashSet<>(),actualAssets=new LinkedHashSet<>();List<String> backwards=new ArrayList<>(),duplicates=new ArrayList<>();int count=0;
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while(entries.hasMoreElements()){
                if(++count>4096)throw new AssertionError("ZIP entry count exceeds bounded limit");
                ZipEntry entry=entries.nextElement();String raw=entry.getName();
                // Java preserves the literal backslash found in Windows aapt2 -A output.
                // Python ZipInfo.filename may normalize it, hiding this Android load failure.
                if(raw.indexOf('\\')>=0)backwards.add(raw);
                if(!names.add(raw))duplicates.add(raw);
                if(raw.startsWith("assets/")&&!entry.isDirectory())actualAssets.add(raw);
            }
            check(count>0&&count<=4096,"APK entry count");
            check(backwards.isEmpty(),"Raw APK entry names contain backslashes: "+backwards);
            check(duplicates.isEmpty(),"Duplicate raw APK entry names: "+duplicates);
            check(actualAssets.equals(expected),"APK assets differ from the exact expected ten paths; actual="+actualAssets);
            for(String relative:EXPECTED){
                Path source=assets.resolve(relative).normalize();
                check(source.startsWith(assets)&&Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)&&Files.size(source)>0&&Files.size(source)<=MAX_ASSET,"Source asset absent, linked or oversized: "+relative);
                String exact="assets/"+relative;ZipEntry entry=zip.getEntry(exact);
                check(entry!=null&&exact.equals(entry.getName())&&!entry.isDirectory()&&entry.getSize()==Files.size(source)&&entry.getSize()<=MAX_ASSET,"Exact Android asset lookup/size mismatch: "+exact);
                byte[] expectedHash,actualHash;
                try(InputStream in=Files.newInputStream(source)){expectedHash=digest(in);}
                try(InputStream in=zip.getInputStream(entry)){actualHash=digest(in);}
                check(MessageDigest.isEqual(expectedHash,actualHash),"APK asset bytes differ from source: "+exact);
            }
        }
        System.out.println("PASS: "+checks+" APK asset checks (raw paths, duplicates, exact ten files, bounded SHA-256 comparison; no Android runtime test).");
    }
    private static byte[] digest(InputStream in)throws Exception{
        MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];long total=0;int n;
        while((n=in.read(buffer))!=-1){total+=n;if(total>MAX_ASSET)throw new AssertionError("Inflated asset exceeds bounded 16 MiB limit");digest.update(buffer,0,n);}
        return digest.digest();
    }
}
