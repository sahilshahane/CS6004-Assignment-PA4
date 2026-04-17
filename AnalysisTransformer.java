import java.util.*;

import heros.FlowFunction;
import heros.FlowFunctions;
import heros.InterproceduralCFG;
import heros.solver.IFDSSolver;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.ide.DefaultJimpleIFDSTabulationProblem;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;

class Node {
    private String id;

    private Node(String id) {
        this.id = id;
    }

    public String get_id() {
        return this.id;
    }

    static Node make_obj(Type objType, int lineNumber) {
        return new Node("O_" + objType.toString() + "_" + lineNumber);
    }

    static Node make_var(String id) {
        return new Node("V_" + id);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Node))
            return false;
        return this.id.equals(((Node) obj).id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.id);
    }

    @Override
    public String toString() {
        return id;
    }
}

class Fact {
    final Local local;
    final SootFieldRef fields[];
    final Node target;
    private final List<Unit> contexts;
    private final int hashCode;

    static final int CONTEXT_LIMIT = 1;

    static final Fact ZERO = new Fact(null, null, null, Collections.emptyList());

    public Fact(Local local, SootFieldRef[] fields, Node target, List<Unit> contexts) {
        this.local = local;
        this.target = target;
        this.fields = fields;
        this.contexts = contexts != null ? contexts : Collections.emptyList();

        this.hashCode = computeHashCode(); // warn: initialize all the variables before computing the hashcode
    }

    public List<Unit> getContexts() {
        return this.contexts;
    }

    public SootFieldRef[] getFields() {
        return this.fields;
    }

    private String getContextsKey() {
        if (contexts.isEmpty()) {
            return "";
        }

        StringBuilder key = new StringBuilder();
        for (int i = 0; i < contexts.size(); i++) {
            int lineNum = contexts.get(i).getJavaSourceStartLineNumber();
            key.append(lineNum > 0 ? lineNum : "?");
            if (i < contexts.size() - 1) {
                key.append("|");
            }
        }
        return key.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (!(obj instanceof Fact))
            return false;
        Fact other = (Fact) obj;

        return Objects.equals(this.local, other.local) &&
                Objects.equals(this.target, other.target) &&
                Arrays.equals(this.fields, other.fields) &&
                Objects.equals(this.contexts, other.contexts);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    public int computeHashCode() {
        if (this == Fact.ZERO)
            return 31;

        return Objects.hash(
                local != null ? local.getName() : null,
                target != null ? target.get_id() : null,
                contexts)
                + Arrays.hashCode(fields);
    }

    @Override
    public String toString() {
        if (this == Fact.ZERO)
            return "ZERO_FACT";

        StringBuilder fieldsStr = new StringBuilder();
        if (fields != null && fields.length > 0) {
            fieldsStr.append("[");
            for (int i = 0; i < fields.length; i++) {
                fieldsStr.append(fields[i] != null ? fields[i].name() : "?");
                if (i < fields.length - 1) {
                    fieldsStr.append(", ");
                }
            }
            fieldsStr.append("]");
        } else {
            fieldsStr.append("[]");
        }

        String ctxKey = getContextsKey();
        String ctxStr = ctxKey.isEmpty() ? "" : " [ctx: " + ctxKey.replace('|', ',') + "]";

        return local + " " + fieldsStr + " " + target + ctxStr;
    }

    // Helper to push a new context (k=3)
    public List<Unit> pushContext(Unit callSite) {
        List<Unit> newCtx = new ArrayList<>(this.contexts);
        newCtx.add(0, callSite); // Add to front
        if (newCtx.size() > Fact.CONTEXT_LIMIT) {
            newCtx.remove(newCtx.size() - 1);
        }
        return Collections.unmodifiableList(newCtx);
    }

    // Helper to pop the context on return
    public List<Unit> popContext() {
        if (this.contexts.isEmpty())
            return this.contexts;
        List<Unit> newCtx = new ArrayList<>(this.contexts);
        newCtx.remove(0); // Remove the most recent call site
        return Collections.unmodifiableList(newCtx);
    }
}

class PointsToProblem
        extends DefaultJimpleIFDSTabulationProblem<Fact, InterproceduralCFG<Unit, SootMethod>> {

    IFDSSolver<Unit, Fact, SootMethod, InterproceduralCFG<Unit, SootMethod>> solver;

    public PointsToProblem(InterproceduralCFG<Unit, SootMethod> icfg) {
        super(icfg); // Pass the Call Graph wrapper to the parent
    }

    public void setSolver(IFDSSolver<Unit, Fact, SootMethod, InterproceduralCFG<Unit, SootMethod>> solver) {
        this.solver = solver;
    }

    @Override
    public Fact createZeroValue() {
        // Create your special dummy seed fact here
        return Fact.ZERO;
    }

    @Override
    public Map<Unit, Set<Fact>> initialSeeds() {
        // Start at the entry of the main method
        SootMethod main = Scene.v().getMainMethod();
        Unit start = main.getActiveBody().getUnits().getFirst();
        return Collections.singletonMap(start, Collections.singleton(zeroValue()));
    }

    @Override
    protected FlowFunctions<Unit, Fact, SootMethod> createFlowFunctionsFactory() {
        // This is where you implement the "getNormalFlowFunction"
        // logic we talked about earlier!
        return new FlowFunctions<Unit, Fact, SootMethod>() {
            @Override
            public FlowFunction<Fact> getCallFlowFunction(Unit callStmt, SootMethod callee) {
                // System.out.println("getCallFlowFunction " + callStmt);

                return source -> {
                    if (source == zeroValue())
                        return Collections.singleton(zeroValue());

                    Set<Fact> res = new HashSet<>();
                    Stmt stmt = (Stmt) callStmt;
                    InvokeExpr ie = stmt.getInvokeExpr();

                    var formalParameters = Helper.getParameterLocals(callee);

                    // 1. Handle the 'this' pointer (The receiver)
                    if (ie instanceof InstanceInvokeExpr) {
                        InstanceInvokeExpr iie = (InstanceInvokeExpr) ie;

                        // If our fact is about the variable 't', map it to 'this' in A()
                        if (source.local.equals(iie.getBase())) {
                            res.add(new Fact(callee.getActiveBody().getThisLocal(), source.fields,
                                    source.target, source.pushContext(callStmt)));
                        }
                    }

                    // Handle other parameters
                    for (int i = 0; i < ie.getArgCount(); i++) {
                        var callerArg = ie.getArg(i);

                        if (source.local.equals(callerArg)) {
                            res.add(new Fact(formalParameters.get(i), source.fields, source.target,
                                    source.pushContext(callStmt)));
                        }
                    }
                    return res;
                };
            }

            @Override
            public FlowFunction<Fact> getCallToReturnFlowFunction(Unit callSiteStmt, Unit returnSite) {
                // System.out.println("getCallToReturnFlowFunction " + arg0);
                return source -> {
                    if (source == zeroValue())
                        return Collections.singleton(source);

                    var res = new HashSet<Fact>();

                    Stmt stmt = (Stmt) callSiteStmt;

                    if (stmt instanceof AssignStmt) {
                        var lhs = ((AssignStmt) stmt).getLeftOp();

                        if (lhs instanceof Local && source.local.equals(lhs)) {
                            return Collections.emptySet();
                        }
                    }

                    res.add(source);

                    return res;
                };
            }

            @Override
            public FlowFunction<Fact> getNormalFlowFunction(Unit curr, Unit succ) {

                // System.out.println("getNormalFlowFunction " + curr);

                return source -> {
                    Set<Fact> res = new HashSet<>();

                    // 1. Keep the identity for existing facts (unless killed)
                    res.add(source);

                    var lineNumber = curr.getJavaSourceStartLineNumber();

                    if (curr instanceof AssignStmt) {
                        AssignStmt assignStmt = (AssignStmt) curr;
                        Value lhs = assignStmt.getLeftOp();
                        Value rhs = assignStmt.getRightOp();

                        if (lhs instanceof Local && source != zeroValue() && source.local.equals(lhs)) { // r0 = xx
                            res.remove(source);
                        }

                        if (source == zeroValue()) {
                            if (lhs instanceof Local && rhs instanceof NewExpr) { // i0 = new T();
                                var newExpr = (NewExpr) rhs;

                                res.add(new Fact(
                                        (Local) lhs,
                                        new SootFieldRef[0],
                                        Node.make_obj(newExpr.getType(), lineNumber),
                                        source.getContexts()));

                                // System.out.println(res);
                            }

                        } else { // handle non-zero fact
                            if (lhs instanceof Local && rhs instanceof Local) { // r0 = r1
                                if (source.local.equals(rhs)) {
                                    // Propagate the rhs fact to lhs
                                    res.add(new Fact((Local) lhs, source.fields, source.target, source.getContexts()));
                                }
                            } else if (lhs instanceof Local && rhs instanceof InstanceFieldRef) { // r0 = r1.f (LOAD)
                                var ifRef = (InstanceFieldRef) rhs;

                                if (source.local.equals(ifRef.getBase())
                                        && (source.getFields().length == 1)
                                        && ((source.getFields()[0]).equals(ifRef.getFieldRef()))) {

                                    res.add(new Fact(
                                            (Local) lhs,
                                            new SootFieldRef[] {}, source.target,
                                            source.getContexts()));
                                }

                            } else if (lhs instanceof InstanceFieldRef && rhs instanceof Local) { // r1.f = r0 (STORE)
                                var ifRef = (InstanceFieldRef) lhs;
                                SootFieldRef field = ifRef.getFieldRef();

                                if (source.local.equals(ifRef.getBase())
                                        && (source.getFields().length == 1)
                                        && ((source.getFields()[0]).equals(ifRef.getFieldRef()))) {
                                    res.remove(source); // kill r1.f
                                }

                                // if (objTargets.contains(source.target)) {
                                // if (source.fields.length == 0) {
                                // res.add(new Fact(source.local, new SootFieldRef[] { field }, source.target,
                                // source.getContexts()));
                                // }
                                // }

                                // set localVars
                                // remove localVars.a

                                // if (source.target.equals(getFact().target)) {
                                // var target = source.target;

                                // // var facts[] = getFactsCoonectedToObj( target(r1) );
                                // // kill references of facts.a
                                // // add All objects of rhs to fact.a
                                // }

                                if (source.local.equals(rhs)) {

                                    res.add(new Fact((Local) ifRef.getBase(),
                                            new SootFieldRef[] { field },
                                            source.target,
                                            source.getContexts()));

                                    // var objTargets = solver.ifdsResultsAt(curr).stream()
                                    // .filter(prevF -> prevF != zeroValue()
                                    // && prevF.local.equals(ifRef.getBase()) && prevF.fields.length == 0)
                                    // .map(f -> f.target)
                                    // .toList();

                                    // solver.ifdsResultsAt(curr).stream()
                                    // .filter(prevF -> prevF != zeroValue()
                                    // && (prevF.fields.length == 0)
                                    // && objTargets.stream()
                                    // .anyMatch(target -> prevF.target.equals(target)))
                                    // .forEach(f -> {
                                    // res.add(new Fact(
                                    // f.local,
                                    // new SootFieldRef[] { field },
                                    // source.target,
                                    // source.getContexts()));
                                    // });

                                }
                            }
                        }
                    }

                    return res;
                };
            }

            @Override
            public FlowFunction<Fact> getReturnFlowFunction(Unit callSiteStmt, SootMethod callee, Unit exitStmt,
                    Unit returnSite) {

                // System.out.println("getReturnFlowFunction " + arg0);

                return source -> {
                    if (source == zeroValue())
                        return Collections.singleton(source);

                    var res = new HashSet<Fact>();

                    if (exitStmt instanceof ReturnStmt && callSiteStmt instanceof AssignStmt) {
                        var assignStmt = (AssignStmt) callSiteStmt;
                        var returnStmt = (ReturnStmt) exitStmt;

                        var returnOp = returnStmt.getOp();

                        Value callerLhs = assignStmt.getLeftOp();

                        if (returnOp instanceof Local && callerLhs instanceof Local) {
                            if (source.local.equals(returnOp)) {
                                res.add(new Fact((Local) callerLhs, source.fields, source.target, source.popContext()));
                            }
                        } else {
                            // TODO: handle constant return values
                        }

                    }

                    return res;
                };
            }
        };
    }
}

class Helper {
    static public List<Local> getParameterLocals(SootMethod callee) {
        List<Local> parameterLocals = new ArrayList<>();

        if (!callee.hasActiveBody()) {
            return parameterLocals; // Return empty if no body exists
        }

        Body body = callee.getActiveBody();
        for (Unit u : body.getUnits()) {
            if (u instanceof IdentityStmt) {
                IdentityStmt is = (IdentityStmt) u;
                Value rightOp = is.getRightOp();

                // Check if the right side is a parameter reference (@parameterX)
                if (rightOp instanceof ParameterRef) {
                    Local l = (Local) is.getLeftOp();
                    parameterLocals.add(l);
                }
            } else if (!(u instanceof IdentityStmt)) {
                // Optimization: Once we hit a non-identity statement,
                // we've passed the parameter declarations.
                break;
            }
        }
        return parameterLocals;
    }
}

public class AnalysisTransformer extends SceneTransformer {

    private void propagateAliasField(
            Collection<Unit> startUnits,
            Set<Local> aliases,
            SootFieldRef field,
            Set<Node> rhsTargets,
            IFDSSolver<Unit, Fact, SootMethod, InterproceduralCFG<Unit, SootMethod>> solver,
            JimpleBasedInterproceduralCFG icfg,
            Map<Unit, Set<Fact>> extraFacts,
            Map<Unit, Set<Fact>> killedFacts) {

        Deque<Unit> queue = new ArrayDeque<>(startUnits);
        Set<Unit> visited = new HashSet<>();

        while (!queue.isEmpty()) {
            Unit curr = queue.poll();
            if (!visited.add(curr)) continue;

            Set<Fact> currFacts = new HashSet<>(solver.ifdsResultsAt(curr));
            Set<Fact> ck = killedFacts.get(curr);
            Set<Fact> ce = extraFacts.get(curr);
            if (ck != null) currFacts.removeAll(ck);
            if (ce != null) currFacts.addAll(ce);

            for (Fact f : currFacts) {
                if (f != Fact.ZERO && f.fields.length == 1
                        && f.fields[0].equals(field) && aliases.contains(f.local))
                    killedFacts.computeIfAbsent(curr, k -> new HashSet<>()).add(f);
            }

            for (Local alias : aliases) {
                for (Node rhsTarget : rhsTargets) {
                    extraFacts.computeIfAbsent(curr, k -> new HashSet<>())
                            .add(new Fact(alias, new SootFieldRef[] { field },
                                    rhsTarget, Collections.emptyList()));
                }
            }

            for (Unit succ : icfg.getSuccsOf(curr)) {
                if (!visited.contains(succ)) queue.add(succ);
            }
        }
    }

    private void computeAliasedFacts(
            IFDSSolver<Unit, Fact, SootMethod, InterproceduralCFG<Unit, SootMethod>> solver,
            JimpleBasedInterproceduralCFG icfg,
            Map<Unit, Set<Fact>> extraFacts,
            Map<Unit, Set<Fact>> killedFacts) {

        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                if (!sm.hasActiveBody()) continue;

                for (Unit u : sm.getActiveBody().getUnits()) {
                    if (!(u instanceof AssignStmt)) continue;
                    AssignStmt assign = (AssignStmt) u;
                    if (!(assign.getLeftOp() instanceof InstanceFieldRef)) continue;
                    if (!(assign.getRightOp() instanceof Local)) continue;

                    InstanceFieldRef ifRef = (InstanceFieldRef) assign.getLeftOp();
                    Local base = (Local) ifRef.getBase();
                    Local rhsLocal = (Local) assign.getRightOp();
                    SootFieldRef field = ifRef.getFieldRef();

                    // Corrected facts before this store (solver + prior alias patches)
                    Set<Fact> beforeFacts = new HashSet<>(solver.ifdsResultsAt(u));
                    Set<Fact> killed = killedFacts.get(u);
                    Set<Fact> extra = extraFacts.get(u);
                    if (killed != null) beforeFacts.removeAll(killed);
                    if (extra != null) beforeFacts.addAll(extra);

                    // Find base's target objects
                    Set<Node> baseTargets = new HashSet<>();
                    for (Fact f : beforeFacts) {
                        if (f != Fact.ZERO && f.fields.length == 0 && f.local.equals(base))
                            baseTargets.add(f.target);
                    }
                    if (baseTargets.isEmpty()) continue;

                    // Find rhs's target objects
                    Set<Node> rhsTargets = new HashSet<>();
                    for (Fact f : beforeFacts) {
                        if (f != Fact.ZERO && f.fields.length == 0 && f.local.equals(rhsLocal))
                            rhsTargets.add(f.target);
                    }
                    if (rhsTargets.isEmpty()) continue;

                    // Intra-procedural aliases: other locals in same scope pointing to same objects
                    Set<Local> intraAliases = new HashSet<>();
                    for (Fact f : beforeFacts) {
                        if (f != Fact.ZERO && f.fields.length == 0
                                && !f.local.equals(base) && baseTargets.contains(f.target))
                            intraAliases.add(f.local);
                    }

                    if (!intraAliases.isEmpty())
                        propagateAliasField(icfg.getSuccsOf(u), intraAliases, field, rhsTargets,
                                solver, icfg, extraFacts, killedFacts);

                    // Inter-procedural aliases: use context to find the call site, then
                    // find ALL caller locals pointing to the same object as base.
                    // This handles both parameter-mapped locals and LOAD-derived locals.
                    for (Fact f : beforeFacts) {
                        if (f != Fact.ZERO && f.fields.length == 0
                                && f.local.equals(base) && !f.getContexts().isEmpty()) {

                            Unit callSite = f.getContexts().get(0);
                            Node baseTarget = f.target;

                            // Facts at call site (in caller scope)
                            Set<Fact> callerFacts = new HashSet<>(solver.ifdsResultsAt(callSite));
                            Set<Fact> cck = killedFacts.get(callSite);
                            Set<Fact> cce = extraFacts.get(callSite);
                            if (cck != null) callerFacts.removeAll(cck);
                            if (cce != null) callerFacts.addAll(cce);

                            // All caller locals pointing to the same object as base
                            Set<Local> callerAliases = new HashSet<>();
                            for (Fact cf : callerFacts) {
                                if (cf != Fact.ZERO && cf.fields.length == 0
                                        && cf.target.equals(baseTarget))
                                    callerAliases.add(cf.local);
                            }

                            if (!callerAliases.isEmpty())
                                propagateAliasField(icfg.getReturnSitesOfCallAt(callSite),
                                        callerAliases, field, rhsTargets,
                                        solver, icfg, extraFacts, killedFacts);
                        }
                    }
                }
            }
        }
    }

    private Set<Fact> augmented(Set<Fact> base, Set<Fact> extra, Set<Fact> killed) {
        if ((extra == null || extra.isEmpty()) && (killed == null || killed.isEmpty()))
            return base != null ? base : Collections.emptySet();
        Set<Fact> result = new HashSet<>(base != null ? base : Collections.emptySet());
        if (killed != null) result.removeAll(killed);
        if (extra != null) result.addAll(extra);
        return result;
    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        CallGraph cg = Scene.v().getCallGraph();

        if (cg.size() <= 0) {
            throw new RuntimeException("Call graph is empty. Spark might not have run!");
        }

        // System.out.println("Total Edges in Call Graph: " + cg.size());

        JimpleBasedInterproceduralCFG icfg = new JimpleBasedInterproceduralCFG();
        PointsToProblem problem = new PointsToProblem(icfg);
        IFDSSolver<Unit, Fact, SootMethod, InterproceduralCFG<Unit, SootMethod>> solver = new IFDSSolver<>(problem);
        problem.setSolver(solver);

        // SootMethod main = Scene.v().getMainMethod();
        // for (Unit u : main.getActiveBody().getUnits()) {
        // if (((Stmt) u).containsInvokeExpr()) {
        // System.out.println("Call site: " + u);
        // System.out.println("Callees: " + icfg.getCalleesOfCallAt(u).size());
        // }
        // }

        solver.solve();

        Map<Unit, Set<Fact>> extraFacts = new HashMap<>();
        Map<Unit, Set<Fact>> killedFacts = new HashMap<>();
        computeAliasedFacts(solver, icfg, extraFacts, killedFacts);

        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                if (sm.hasActiveBody()) {
                    System.out.println("--- Method: " + sm.getSignature() + " ---");
                    for (Unit u : sm.getActiveBody().getUnits()) {
                        System.out.println("[Stmt] " + u);

                        // To get facts *after* the statement, we query the successors
                        List<Unit> succs = icfg.getSuccsOf(u);
                        if (succs.isEmpty()) {
                            Set<Fact> facts = augmented(solver.ifdsResultsAt(u), extraFacts.get(u), killedFacts.get(u));
                            for (Fact f : facts) {
                                if (f != Fact.ZERO)
                                    System.out.println("    => (before exit) " + f);
                            }
                        } else {
                            for (Unit succ : succs) {
                                Set<Fact> facts = augmented(solver.ifdsResultsAt(succ), extraFacts.get(succ), killedFacts.get(succ));
                                for (Fact f : facts) {
                                    if (f != Fact.ZERO)
                                        System.out.println("    => " + f);
                                }
                            }
                        }
                    }
                }
            }
        }

    }
}
