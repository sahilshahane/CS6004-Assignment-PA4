import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import boomerang.scope.soot.BoomerangPretransformer;
import soot.*;
import soot.jimple.spark.SparkTransformer;
import soot.options.Options;

public class PA4 {
    public static void main(String[] args) {
        String jsonInput = args.length > 0 ? args[0] : "{}";

        String classPath = "";
        String outputDir = "sootOutput";
        boolean enableAnalysis = false;
        boolean enableCheckInliner = false;
        boolean enableUnreachable = false;
        boolean enableInvokeMetrics = false;

        try {
            JSONParser parser = new JSONParser();
            JSONObject config = (JSONObject) parser.parse(jsonInput);
            if (config.containsKey("class_path")) {
                classPath = (String) config.get("class_path");
            }
            if (config.containsKey("output_dir")) {
                outputDir = (String) config.get("output_dir");
            }

            enableAnalysis = getBoolParam(config, "enable_transform_analysis");
            enableCheckInliner = getBoolParam(config, "enable_transform_check_inliner");
            enableUnreachable = getBoolParam(config, "enable_transform_unreachable");
            enableInvokeMetrics = getBoolParam(config, "enable_transform_invoke_metrics");

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Failed to read configuration. Aborting.");
            return;
        }

        G.reset();

        Options.v().set_output_dir(outputDir);

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

        Options.v().set_no_writeout_body_releasing(true);

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
        Scene.v().addBasicClass("java.lang.Throwable", SootClass.SIGNATURES);
        Scene.v().addBasicClass("MyRuntimeMetrics", SootClass.SIGNATURES);
        Scene.v().loadNecessaryClasses();

        // 4. Force your test classes to be Application Classes
        // This ensures Spark doesn't skip them
        // BUG: package name conflict
        for (SootClass sc : Scene.v().getClasses()) {
            if (sc.getName().contains("testcases")) {
                sc.setApplicationClass();
            }
        }

        BoomerangPretransformer.v().reset();
        PackManager.v().getPack("cg").apply();
        BoomerangPretransformer.v().apply();

        if (enableAnalysis) {
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.analysis", new AnalysisTransformer()));
        }
        if (enableCheckInliner) {
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.check_inliner", new CheckInliner()));
        }
        if (enableUnreachable) {
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.unreachable", new UnreachableMethodRemover()));
        }
        if (enableInvokeMetrics) {
            PackManager.v().getPack("wjtp").add(new Transform("wjtp.invoke_metrics", new InvokeMetricCollector()));
        }

        // PackManager.v().getPack("wjtp").apply();
        PackManager.v().runPacks();

        // // Optional: Write out Jimple files to sootOutput/
        Options.v().set_output_format(Options.output_format_jimple);
        PackManager.v().writeOutput();

        Options.v().set_output_format(Options.output_format_class);
        PackManager.v().writeOutput();
    }

    private static boolean getBoolParam(JSONObject config, String key) {
        if (!config.containsKey(key))
            return false;
        Object val = config.get(key);
        if (val instanceof Boolean) {
            return ((Boolean) val).booleanValue();
        }
        return false;
    }
}
