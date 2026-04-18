import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import soot.Scene;
import soot.SceneTransformer;
import soot.SootClass;
import soot.SootMethod;
import soot.jimple.Stmt;

public class UnreachableMethodRemover extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        Set<SootMethod> dynamicallyReachable = new HashSet<>();
        Queue<SootMethod> worklist = new LinkedList<>();

        // Initialize worklist with entry points
        for (SootMethod ep : Scene.v().getEntryPoints()) {
            dynamicallyReachable.add(ep);
            worklist.add(ep);
        }

        // Simple BFS to find all methods reachable from the current Jimple bodies
        while (!worklist.isEmpty()) {
            SootMethod m = worklist.poll();
            if (m.hasActiveBody()) {
                for (soot.Unit u : m.getActiveBody().getUnits()) {
                    Stmt stmt = (Stmt) u;
                    if (stmt.containsInvokeExpr()) {
                        SootMethod target = stmt.getInvokeExpr().getMethod();
                        if (dynamicallyReachable.add(target)) {
                            worklist.add(target);
                        }
                    }
                }
            }
        }

        for (SootClass sootClass : Scene.v().getApplicationClasses()) {
            List<SootMethod> methodsToRemove = new ArrayList<>();

            for (SootMethod method : sootClass.getMethods()) {
                if (!dynamicallyReachable.contains(method)) {
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
