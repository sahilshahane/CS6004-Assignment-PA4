import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import soot.*;
import soot.jimple.spark.SparkTransformer;
import soot.options.Options;

public class PA4 {
    public static void main(String[] args) {
        String classPath = "./testcases/" + args[0];

        G.reset();

        // 2. Setting Global Options (The Options.v() way)
        Options.v().set_keep_line_number(true);

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

        // 3. Crucial: Don't load bodies for excluded classes
        Options.v().set_no_bodies_for_excluded(true);

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

        // 4. Force Call Graph Generation (Spark)
        // This is the manual trigger that populates the Scene's CallGraph
        Map<String, String> opt = new HashMap<>();
        opt.put("enabled", "true");
        opt.put("verbose", "true");
        opt.put("on-fly-cg", "true");
        opt.put("t", "0");
        SparkTransformer.v().transform("cg.spark", opt);

        Options.v().set_num_threads(1);

        // 6. Verify Call Graph exists before starting Analysis
        if (!Scene.v().hasCallGraph()) {
            throw new RuntimeException("Spark failed to build a Call Graph!");
        }

        // 5. Register your Analysis
        // We add it to 'wjtp' (Whole Jimple Transformation Pack)
        AnalysisTransformer analysis = new AnalysisTransformer();
        PackManager.v().getPack("wjtp").add(new Transform("wjtp.analysis", analysis));

        PackManager.v().getPack("wjtp").add(
            new Transform("wjtp.check_inliner", new CheckInliner())
        );
        // 6. Execute
        // DO NOT call soot.Main.main(args) here.
        PackManager.v().runPacks();

        // Optional: Write out Jimple files to sootOutput/
        PackManager.v().writeOutput();
    }
}
