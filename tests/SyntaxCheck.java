import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.util.*;
public final class SyntaxCheck {
    public static void main(String[] args)throws Exception{
        JavaCompiler compiler=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics=new DiagnosticCollector<>();
        try(StandardJavaFileManager files=compiler.getStandardFileManager(diagnostics,null,java.nio.charset.StandardCharsets.UTF_8)){
            JavacTask task=(JavacTask)compiler.getTask(null,files,diagnostics,Arrays.asList("-proc:none","--release","8"),null,files.getJavaFileObjectsFromStrings(Arrays.asList(args)));
            int count=0;for(Object unit:task.parse())count++;
            boolean failed=false;
            for(Diagnostic<?> d:diagnostics.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){System.err.println(d);failed=true;}
            if(failed)System.exit(1);
            System.out.println("PASS: Java syntax parsed for "+count+" source files. Android API symbol resolution NOT tested.");
        }
    }
}
