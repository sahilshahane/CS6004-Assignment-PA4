import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import boomerang.scope.soot.BoomerangPretransformer;
import soot.*;
import soot.jimple.spark.SparkTransformer;
import soot.options.Options;

public class PA4 {
    public static void main(String[] args) {
        String classPath = "./testcases/" + args[0];

        G.reset();

        Options.v().setPhaseOption("cg.spark", "on");
        Options.v().setPhaseOption("cg.spark", "vta:true");
        Options.v().setPhaseOption("cg.spark", "fallback-cha:false");

        Options.v().setPhaseOption("jb", "use-original-names:true");

        // 2. Setting Global Options (The Options.v() way)
        Options.v().set_keep_line_number(true);
        Options.v().set_no_bodies_for_excluded(true);

        Options.v().set_prepend_classpath(true); // Equivalent to -pp
        Options.v().set_soot_classpath(classPath);

        Options.v().set_process_dir(Collections.singletonList(classPath));
        Options.v().set_app(true);
        Options.v().set_whole_program(true);

        Options.v().set_src_prec(Options.src_prec_class);

        Options.v().set_allow_phantom_refs(true);
        Options.v().set_main_class("Test");
        Options.v().set_output_format(Options.output_format_jimple);

        // 2. Use the exclude list to mark everything else as Library
        List<String> excluded = new ArrayList<>();
        excluded.add("java.*");
        excluded.add("javax.*");
        excluded.add("sun.*");
        excluded.add("com.sun.*");
        excluded.add("jdk.*");
        Options.v().set_exclude(excluded);

        // 3. Load Classes
        Scene.v().addBasicClass("java.lang.Object", SootClass.SIGNATURES);
        Scene.v().loadNecessaryClasses();

        // 4. Force your test classes to be Application Classes
        // This ensures Spark doesn't skip them
        // BUG: package name conflict
        for (SootClass sc : Scene.v().getClasses()) {
            if (sc.getName().contains("testcases")) {
                sc.setApplicationClass();
            }
        }

        AnalysisTransformer analysis = new AnalysisTransformer();
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.analysis", analysis));

        PackManager.v().getPack("cg").apply();

        BoomerangPretransformer.v().reset();
        BoomerangPretransformer.v().apply();

        PackManager.v().getPack("wjtp").apply();

        // Optional: Write out Jimple files to sootOutput/
        PackManager.v().writeOutput();
    }
}
