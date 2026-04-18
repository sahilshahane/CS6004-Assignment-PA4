import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.toolkits.callgraph.ReachableMethods;

public class UnreachableMethodRemover extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        ReachableMethods reachableMethods = Scene.v().getReachableMethods();

        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            List<SootMethod> methodsToRemove = new ArrayList<>();

            for (SootMethod method : sootClass.getMethods()) {
                if (!reachableMethods.contains(method) && !Scene.v().getEntryPoints().contains(method)) {
                    methodsToRemove.add(method);
                }
            }
            for (SootMethod method : methodsToRemove) {
                String sig = method.getSignature();
                sootClass.removeMethod(method);
                System.out.println("Removed unreachable method: " + sig);
            }
        }
    }
}
