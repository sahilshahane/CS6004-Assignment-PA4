import java.util.Map;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;

public class InvokeMetricCollector extends SceneTransformer {

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        SootClass metricsClass = Scene.v().getSootClass("MyRuntimeMetrics");
        SootMethod logStatic = metricsClass.getMethod("void logStatic()");
        SootMethod logInstance = metricsClass.getMethod("void logInstance()");
        SootMethod printReport = metricsClass.getMethod("void printReport()");

        for (SootClass sc : Scene.v().getApplicationClasses()) {
            // Do not instrument the metrics collector itself
            if (sc.getName().equals("MyRuntimeMetrics"))
                continue;

            for (SootMethod sm : sc.getMethods()) {
                if (!sm.hasActiveBody())
                    continue;

                Body body = sm.getActiveBody();
                PatchingChain<Unit> units = body.getUnits();

                // Track where we might need to insert calls to avoid ConcurrentModification
                java.util.List<Unit> unitsToInstrument = new java.util.ArrayList<>();
                for (Unit u : units) {
                    unitsToInstrument.add(u);
                }

                for (Unit u : unitsToInstrument) {
                    Stmt stmt = (Stmt) u;

                    // Instrument Invokes
                    if (stmt.containsInvokeExpr()) {
                        InvokeExpr expr = stmt.getInvokeExpr();
                        if (expr.getMethod().getDeclaringClass().isApplicationClass()) {
                            if (expr instanceof StaticInvokeExpr) {
                                units.insertBefore(Jimple.v().newInvokeStmt(
                                        Jimple.v().newStaticInvokeExpr(logStatic.makeRef())), u);
                            } else if (expr instanceof InstanceInvokeExpr) {
                                units.insertBefore(Jimple.v().newInvokeStmt(
                                        Jimple.v().newStaticInvokeExpr(logInstance.makeRef())), u);
                            }
                        }
                    }

                    // Instrument return statements in main
                    if (sm.getName().equals("main") && (stmt instanceof ReturnStmt || stmt instanceof ReturnVoidStmt)) {
                        units.insertBefore(Jimple.v().newInvokeStmt(
                                Jimple.v().newStaticInvokeExpr(printReport.makeRef())), u);
                    }
                }

                // Add try-catch block for abnormality in main
                if (sm.getName().equals("main")) {
                    Unit firstNonIdentity = null;
                    for (Unit u : units) {
                        if (!(u instanceof IdentityStmt)) {
                            firstNonIdentity = u;
                            break;
                        }
                    }
                    if (firstNonIdentity == null)
                        continue;

                    Unit lastBeforeTrap = unitsToInstrument.get(unitsToInstrument.size() - 1);

                    Local exceptionLocal = Jimple.v().newLocal("metricsException", RefType.v("java.lang.Throwable"));
                    body.getLocals().add(exceptionLocal);

                    Stmt catchIdentity = Jimple.v().newIdentityStmt(exceptionLocal, Jimple.v().newCaughtExceptionRef());
                    Stmt printCall = Jimple.v().newInvokeStmt(Jimple.v().newStaticInvokeExpr(printReport.makeRef()));
                    Stmt throwStmt = Jimple.v().newThrowStmt(exceptionLocal);

                    units.insertAfter(catchIdentity, lastBeforeTrap);
                    units.insertAfter(printCall, catchIdentity);
                    units.insertAfter(throwStmt, printCall);

                    SootClass throwableClass = Scene.v().getSootClass("java.lang.Throwable");
                    Trap trap = Jimple.v().newTrap(throwableClass, firstNonIdentity, catchIdentity, catchIdentity);
                    body.getTraps().add(trap);
                }
            }
        }
    }
}
