import java.util.*;

import boomerang.BackwardQuery;
import boomerang.Boomerang;
import boomerang.ForwardQuery;
import boomerang.options.BoomerangOptions;
import boomerang.results.BackwardBoomerangResults;
import boomerang.scope.ControlFlowGraph.Edge;
import boomerang.scope.DataFlowScope;
import boomerang.scope.InvokeExpr;
import boomerang.scope.Statement;
import boomerang.scope.soot.jimple.JimpleMethod;
import boomerang.scope.soot.jimple.JimpleStatement;
import boomerang.scope.soot.SootFrameworkScope;
import soot.*;
import soot.jimple.*;
import soot.util.Chain;
import wpds.impl.NoWeight;
import java.util.concurrent.ThreadLocalRandom;

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

        while (listener.hasNext()) {
            var momc = listener.next();
            SootMethod callerMethod = momc.method();

            if (!callerMethod.hasActiveBody() || callerMethod.isJavaLibraryMethod())
                continue;

            var method = JimpleMethod.of(callerMethod, Scene.v());
            var cfg = method.getControlFlowGraph();

            for (Statement stmt : method.getStatements()) {
                if (!stmt.containsInvokeExpr())
                    continue;

                InvokeExpr invoke = stmt.getInvokeExpr();

                if ((!invoke.isInstanceInvokeExpr()) || invoke.isSpecialInvokeExpr()) {
                    System.out.println("[SKIPPED PROCESSING] " + invoke);
                    continue;
                }

                Stmt sootStmt = (Stmt) ((JimpleStatement) stmt).getDelegate();

                SootMethod expressionMethod = sootStmt.getInvokeExpr().getMethod();

                if (expressionMethod.isJavaLibraryMethod())
                    continue;

                var base = invoke.getBase();

                Collection<Statement> preds = cfg.getPredsOf(stmt);
                if (preds.isEmpty())
                    preds = Collections.singleton(Statement.epsilon());

                Set<SootClass> concreteTypes = new HashSet<>();

                for (Statement predStmt : preds) {
                    Edge edge = new Edge(predStmt, stmt);
                    BackwardQuery query = BackwardQuery.make(edge, base);
                    BackwardBoomerangResults<NoWeight> results = solver.solve(query);

                    for (ForwardQuery fwdQuery : results.getAllocationSites().keySet()) {
                        String className = fwdQuery.getAllocVal().getType().toString();

                        SootClass concreteClass = Scene.v().getSootClass(className);

                        concreteTypes.add(concreteClass);
                    }
                }

                var resolvedMethods = new HashSet<SootMethod>();

                for (var type : concreteTypes) {
                    System.out.println(callerMethod + " -> Possible Concrete Type: " + type);

                    SootMethod resolvedMethod = Scene.v().getOrMakeFastHierarchy()
                            .resolveConcreteDispatch(type, expressionMethod);

                    if (resolvedMethod != null)
                        resolvedMethods.add(resolvedMethod);
                    // System.out.println(" -> Resolved Method: " + resolvedMethod);
                }

                if (resolvedMethods.iterator().hasNext()) {
                    System.out.println(
                            callerMethod + " " + resolvedMethods.iterator().next() + " -> Resolved Method: " +
                                    resolvedMethods.size());
                }

                if (resolvedMethods.size() == 1) {
                    for (var type : concreteTypes) {
                        Helper.replaceToStaticCallSite(type, stmt, resolvedMethods.iterator().next(), callerMethod);
                        System.out.println("Replaced with static : " + stmt);
                    }
                }

            }
        }

        solver.unregisterAllListeners();

    }

}

class Helper {
    public static String getRandomString(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            int index = ThreadLocalRandom.current().nextInt(chars.length());
            sb.append(chars.charAt(index));
        }
        return sb.toString();
    }

    static Body getTransformedStaticMethodBody(SootClass thisClass, SootMethod originalMethod) {
        if (!originalMethod.hasActiveBody()) {
            originalMethod.retrieveActiveBody();
        }

        Body originalBody = originalMethod.getActiveBody();
        Body staticBody = (Body) originalBody.clone();

        PatchingChain<Unit> units = staticBody.getUnits();

        for (Unit u : units) {

            if (u instanceof soot.jimple.NopStmt)
                continue;

            if (u instanceof IdentityStmt) {
                IdentityStmt idStmt = (IdentityStmt) u;
                Value rightOp = idStmt.getRightOp();

                if (rightOp instanceof ThisRef) {
                    // Change "@this: Type" to "@parameter0: Type"
                    ParameterRef newParam0 = Jimple.v()
                            .newParameterRef(thisClass.getType(), 0);

                    System.out.println("Chaning param : " + rightOp + " " + newParam0);
                    idStmt.setRightOp(newParam0);

                } else if (rightOp instanceof ParameterRef) {
                    // Shift existing parameters by +1
                    ParameterRef oldParam = (ParameterRef) rightOp;
                    int shiftedIndex = oldParam.getIndex() + 1;
                    ParameterRef shiftedParam = Jimple.v().newParameterRef(
                            oldParam.getType(), shiftedIndex);
                    idStmt.setRightOp(shiftedParam);
                }
            } else {
                // break;
            }
        }

        // System.out.println("original body : " + originalBody.getUnits());
        // System.out.println("original method : " + originalMethod);

        return staticBody;
    }

    static void replaceToStaticCallSite(SootClass declaringClass, Statement stmt, SootMethod resolvedMethod,
            SootMethod callerMethod) {
        if (!stmt.containsInvokeExpr()) {
            System.out.println(stmt + " is not a invoke expression");
            return;
        }

        if (resolvedMethod.isStatic()) {
            System.out.println("method already static : " + resolvedMethod);
            return;
        }

        if (resolvedMethod.isConstructor()) {
            System.out.println("cannot convert constructor to static : " + resolvedMethod);
            return;
        }

        String newMethodName = resolvedMethod.getName() + "_gen_compile_time";

        // check if static version of the function call already exist
        // if the name contain resolvedMethod.getName() + "_gen_compile_time_" , i.e new
        // method name
        var modifyingClass = false ? declaringClass : resolvedMethod.getDeclaringClass();

        List<Type> staticParamTypes = new ArrayList<>();
        staticParamTypes.add(resolvedMethod.getDeclaringClass().getType()); // The explicit 'this'
        staticParamTypes.addAll(resolvedMethod.getParameterTypes());

        int modifiers = resolvedMethod.getModifiers();
        modifiers |= Modifier.STATIC;

        SootMethod newStaticMethod = new SootMethod(
                newMethodName,
                staticParamTypes,
                resolvedMethod.getReturnType(),
                modifiers,
                resolvedMethod.getExceptions());

        {
            var existingMethod = modifyingClass.getMethodUnsafe(newStaticMethod.getSubSignature());

            if (existingMethod != null) {
                newStaticMethod = existingMethod;
                System.out.println("No need for generating new method, method already exists : " + newStaticMethod);
            } else {

                var staticMethodBody = getTransformedStaticMethodBody(modifyingClass, resolvedMethod);
                staticMethodBody.setMethod(newStaticMethod);
                newStaticMethod.setActiveBody(staticMethodBody);
                modifyingClass.addMethod(newStaticMethod);

            }
        }

        System.out.println("Replacing at : " + stmt);

        // TODO: check if the static method signature does not exists in the adding

        Stmt originalInvokeStmt = (soot.jimple.Stmt) ((JimpleStatement) stmt).getDelegate();

        var originalExpr = (soot.jimple.InstanceInvokeExpr) originalInvokeStmt.getInvokeExpr();

        soot.Body callerBody = callerMethod.getActiveBody();

        Value receiver = originalExpr.getBase();

        List<Value> staticArgs = new ArrayList<>();
        staticArgs.add(receiver);
        staticArgs.addAll(originalExpr.getArgs());

        StaticInvokeExpr staticInvoke = Jimple.v().newStaticInvokeExpr(newStaticMethod.makeRef(), staticArgs);

        Unit newStmt;
        if (originalInvokeStmt instanceof InvokeStmt) {
            newStmt = Jimple.v().newInvokeStmt(staticInvoke);
        } else if (originalInvokeStmt instanceof AssignStmt) {
            AssignStmt assignStmt = (AssignStmt) originalInvokeStmt;
            newStmt = Jimple.v().newAssignStmt(assignStmt.getLeftOp(), staticInvoke);
        } else {
            throw new IllegalArgumentException("Unsupported statement type");
        }

        // Perform the swap safely
        Chain<Unit> units = callerBody.getUnits();
        units.insertAfter(newStmt, originalInvokeStmt);
        units.remove(originalInvokeStmt);
    }
}
