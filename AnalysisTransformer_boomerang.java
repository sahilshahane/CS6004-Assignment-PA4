import java.util.*;

import soot.*;
import soot.jimple.toolkits.callgraph.CallGraph;

public class AnalysisTransformer_boomerang extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        CallGraph cg = Scene.v().getCallGraph();

        if (cg.size() <= 0) {
            throw new RuntimeException("Call graph is empty. Spark might not have run!");
        }

        System.out.println("LOL");

    }
}
