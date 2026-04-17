import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import heros.FlowFunction;
import heros.FlowFunctions;
import heros.InterproceduralCFG;
import heros.flowfunc.Identity;
import heros.solver.IFDSSolver;
import jas.Var;
import soot.*;
import soot.jimple.*;
import soot.jimple.spark.SparkTransformer;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.ide.DefaultJimpleIFDSTabulationProblem;
import soot.jimple.toolkits.ide.icfg.JimpleBasedInterproceduralCFG;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

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
    Local local;
    SootField fields[];
    Node target;

    static Fact ZERO = new Fact(null, null, null);

    public Fact(Local n1, SootField[] fields, Node n2) {
        this.local = n1;
        this.target = n2;
        this.fields = fields;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;

        if (!(obj instanceof Fact))
            return false;

        return this.local.equals(((Fact) obj).local) &&
                this.target.equals(((Fact) obj).target) &&
                Arrays.equals(this.fields, ((Fact) obj).fields);
    }

    @Override
    public int hashCode() {
        return (31 * Objects.hash(local, target)) + Arrays.hashCode(fields);
    }

    @Override
    public String toString() {
        if (this == Fact.ZERO)
            return "ZERO_FACT";

        return local + " " + fields + " " + target;
    }
}

class PointsToProblem
        extends DefaultJimpleIFDSTabulationProblem<Fact, InterproceduralCFG<Unit, SootMethod>> {

    public PointsToProblem(InterproceduralCFG<Unit, SootMethod> icfg) {
        super(icfg); // Pass the Call Graph wrapper to the parent
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
                                    source.target));
                        }
                    }

                    // Handle other parameters
                    for (int i = 0; i < ie.getArgCount(); i++) {
                        var callerArg = ie.getArg(i);

                        if (source.local.equals(callerArg)) {
                            res.add(new Fact(formalParameters.get(i), source.fields, source.target));
                        }
                    }

                    return res;
                };
            }

            @Override
            public FlowFunction<Fact> getCallToReturnFlowFunction(Unit arg0, Unit arg1) {
                // System.out.println("getCallToReturnFlowFunction " + arg0);
                return Identity.v();
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

                        if (source == zeroValue()) {

                            if (lhs instanceof Local && rhs instanceof NewExpr) { // i0 = new T();
                                var newExpr = (NewExpr) rhs;

                                res.add(new Fact((Local) lhs, new SootField[0],
                                        Node.make_obj(newExpr.getType(), lineNumber)));
                                // System.out.println(res);
                            }
                        }

                        else { // handle non-zero fact
                            if (lhs instanceof Local && rhs instanceof Local) {
                                if (source.local.equals(rhs)) {
                                    res.add(new Fact((Local) lhs, source.fields, source.target));
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
                                res.add(new Fact((Local) callerLhs, source.fields, source.target));
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

        // SootMethod main = Scene.v().getMainMethod();
        // for (Unit u : main.getActiveBody().getUnits()) {
        // if (((Stmt) u).containsInvokeExpr()) {
        // System.out.println("Call site: " + u);
        // System.out.println("Callees: " + icfg.getCalleesOfCallAt(u).size());
        // }
        // }

        solver.solve();

        for (SootClass sc : Scene.v().getApplicationClasses()) {
            for (SootMethod sm : sc.getMethods()) {
                if (sm.hasActiveBody()) {
                    System.out.println("--- Method: " + sm.getSignature() + " ---");
                    for (Unit u : sm.getActiveBody().getUnits()) {
                        System.out.println("[Stmt] " + u);

                        // To get facts *after* the statement, we query the successors
                        List<Unit> succs = icfg.getSuccsOf(u);
                        if (succs.isEmpty()) {
                            // E.g., for ReturnStmt, we might just use the results at the stmt itself
                            // if there's no intra-procedural successor, though it technically represents
                            // "before"
                            Set<Fact> facts = solver.ifdsResultsAt(u);
                            if (facts != null) {
                                for (Fact f : facts) {
                                    if (f != Fact.ZERO) {
                                        System.out.println("    => (before exit) " + f);
                                    }
                                }
                            }
                        } else {
                            // Print facts reaching the successors
                            for (Unit succ : succs) {
                                Set<Fact> facts = solver.ifdsResultsAt(succ);
                                if (facts != null) {
                                    for (Fact f : facts) {
                                        if (f != Fact.ZERO) {
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

    }
}
