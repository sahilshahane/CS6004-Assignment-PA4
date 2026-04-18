import java.util.*;

import boomerang.BackwardQuery;
import boomerang.Boomerang;
import boomerang.ForwardQuery;
import boomerang.options.BoomerangOptions;
import boomerang.results.BackwardBoomerangResults;
import boomerang.scope.ControlFlowGraph.Edge;
import boomerang.scope.DataFlowScope;
import boomerang.scope.InvokeExpr;
import boomerang.scope.Method;
import boomerang.scope.Statement;
import boomerang.scope.Val;
import boomerang.scope.soot.jimple.JimpleMethod;
import boomerang.scope.soot.SootFrameworkScope;
import soot.*;
import soot.jimple.*;
import wpds.impl.NoWeight;

public class AnalysisTransformer extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {

        SootFrameworkScope scope = new SootFrameworkScope(
                Scene.v(),
                Scene.v().getCallGraph(),
                Scene.v().getEntryPoints(),
                DataFlowScope.EXCLUDE_PHANTOM_CLASSES);

        BoomerangOptions boomerangOptions = BoomerangOptions.builder()
                .enableAllowMultipleQueries(true)
                .build();
        Boomerang solver = new Boomerang(scope, boomerangOptions);

        var listener = Scene.v().getReachableMethods().listener();

        List<Stmt> staticFyCallSites = new ArrayList<>();

        while (listener.hasNext()) {
            var momc = listener.next();
            SootMethod sootMethod = momc.method();

            if (!sootMethod.hasActiveBody() || sootMethod.isJavaLibraryMethod())
                continue;

            var method = JimpleMethod.of(sootMethod, Scene.v());
            var cfg = method.getControlFlowGraph();

            for (Statement stmt : method.getStatements()) {
                if (!stmt.containsInvokeExpr())
                    continue;

                InvokeExpr invoke = stmt.getInvokeExpr();

                if (invoke.isInstanceInvokeExpr() && !invoke.isSpecialInvokeExpr()) {

                    var base = invoke.getBase();

                    Collection<Statement> preds = cfg.getPredsOf(stmt);
                    if (preds.isEmpty())
                        preds = Collections.singleton(Statement.epsilon());

                    Set<boomerang.scope.Type> concreteTypes = new HashSet<>();

                    for (Statement predStmt : preds) {
                        Edge edge = new Edge(predStmt, stmt);
                        BackwardQuery query = BackwardQuery.make(edge, base);
                        BackwardBoomerangResults<NoWeight> results = solver.solve(query);

                        for (ForwardQuery fwdQuery : results.getAllocationSites().keySet()) {
                            concreteTypes.add(fwdQuery.getAllocVal().getType());
                        }
                    }

                    for (boomerang.scope.Type type : concreteTypes) {
                        System.out.println(" -> Possible Concrete Type: " + type);
                    }

                    if (concreteTypes.size() == 1) {
                        // Scene.v().getOrMakeFastHierarchy().resolveConcreteDispatch();
                        // directly replace the call
                    }

                }

            }
        }

        solver.unregisterAllListeners();
    }
}
