import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import jas.Var;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
import soot.toolkits.graph.BriefUnitGraph;
import soot.toolkits.graph.UnitGraph;

enum EscapeType {
    GLOBAL,
    ARG,
    NO_ESCAPE,
}

class AllocationObject {
    public boolean parameter = false;
    public boolean escaped = false;
    private EscapeType escape_type = EscapeType.NO_ESCAPE;
    public boolean instance_field_written = false; // true if any instance field of the object is writtten obj.f = 1

    public boolean is_scalar_replaceable = true;
    public boolean is_stack_allocatable = true;

    public final Integer creation_line_number;

    AllocationObject(Integer creation_line_number) {
        this.creation_line_number = creation_line_number;
    }

    void set_escape_type_no_priority(EscapeType _escape_type) {
        this.escaped = (_escape_type != EscapeType.NO_ESCAPE);
        this.escape_type = _escape_type;
        this.is_scalar_replaceable = _escape_type != EscapeType.GLOBAL;
    }

    void set_escape_type(EscapeType _escape_type) {
        if (this.escape_type == EscapeType.GLOBAL)
            return;
        if (this.escape_type == EscapeType.ARG)
            return;

        set_escape_type_no_priority(_escape_type);
    }

    void set_parameter(boolean param) {
        if (this.parameter)
            return;
        this.parameter = param;
    }

    EscapeType get_escape_type() {
        return this.escape_type;
    }

    public AllocationObject copy() {
        var newObj = new AllocationObject(this.creation_line_number);

        newObj.parameter = this.parameter;
        newObj.escaped = this.escaped;
        newObj.escape_type = this.escape_type;
        newObj.instance_field_written = this.instance_field_written;
        newObj.is_scalar_replaceable = this.is_scalar_replaceable;
        newObj.is_stack_allocatable = this.is_stack_allocatable;

        return newObj;
    }
}

enum NodeType {
    VARIABLE,
    OBJECT
}

abstract class _Edge {
    public Node points_to = null;

    public _Edge(Node points_to) {
        this.points_to = points_to;
    }

    public String toString() {
        return (points_to == null ? "" : points_to.toString());
    }
}

class VariableEdge extends _Edge {
    public VariableEdge(Node point_to) {
        super(point_to);
    }
}

class ObjectFieldEdge extends _Edge {
    private String id;

    private SootField fieldName;

    private ObjectFieldEdge() {
        super(null);
    }

    //
    public ObjectFieldEdge(SootFieldRef fieldRef, Node point_to) {
        super(point_to);

        var resolvedField = fieldRef.resolve();
        if (resolvedField == null)
            throw new RuntimeException("Field " + fieldRef + " cannot be resolved");

        fieldName = resolvedField;

        this.id = make_field_id__(this.fieldName);
    }

    private static String make_field_id__(SootField field) {
        return field.getDeclaringClass() + "::" + field.getName();
    }

    //
    public static String make_field_id(SootFieldRef fieldRef) {
        var resolvedField = fieldRef.resolve();
        if (resolvedField == null)
            throw new RuntimeException("Field " + fieldRef + " cannot be resolved");

        return make_field_id__(resolvedField);
    }

    public String get_id() {
        return id;
    }

    public SootField get_field() {
        return this.fieldName;
    }

    @Override
    public String toString() {
        return this.get_id() + " -> " + super.toString();
    }

    public ObjectFieldEdge make_copy() {
        var edge = new ObjectFieldEdge();

        edge.fieldName = this.fieldName;
        edge.id = this.id;
        edge.points_to = this.points_to;

        return edge;
    }

    public void set_point_to(Node point_to) {
        this.points_to = point_to;
    }
}

abstract class Node {
    public String id;
    private List<_Edge> edges = new Vector<>();

    public Node(String id) {
        this.id = id.trim();
    }

    @Override
    public String toString() {
        return this.id;
    }

    public List<_Edge> get_edges() {
        return this.edges;
    }

    public void add_edge(_Edge edge) {
        this.edges.add(edge);
    }

    public void set_edges(List<_Edge> replacementEdges) {
        this.edges = replacementEdges;
    }
}

class VariableNode extends Node {
    public VariableNode(String id) {
        super(id);
    }
}

class ObjectNode extends Node {
    public AllocationObject object;

    public ObjectNode(String id, AllocationObject object) {
        super(id);
        this.object = object;
    }

    static ObjectNode make(RefLikeType objType, Integer creation_line_number) {
        var obj = new AllocationObject(Integer.valueOf(creation_line_number));
        ObjectNode objectNode = new ObjectNode("O" + String.valueOf(creation_line_number), obj);
        return objectNode;
    }

    @Override
    public void add_edge(_Edge edge) {
        super.add_edge(edge);

        var objNodeOfEdge = ((ObjectNode) ((ObjectFieldEdge) edge).points_to);

        if (objNodeOfEdge.object.get_escape_type() == EscapeType.NO_ESCAPE) {
            objNodeOfEdge.object.set_escape_type(this.object.get_escape_type());
        }
    }
}

class PTGraph {
    // variable -> object
    // Set<Node> nodes = new HashSet<>();
    private HashMap<String, Node> nodes = new HashMap<>();

    private HashMap<String, Node> globals = new HashMap<>();

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();

        if (nodes.size() == 0) {
            s.append("[NO NODES]\n");
        }

        nodes.forEach((unit, node) -> {
            String nodeType = "[UNKNOWN]";

            if (node instanceof ObjectNode) {
                var objNode = ((ObjectNode) node);

                var isGlobalEsc = objNode.object.get_escape_type() == EscapeType.GLOBAL;
                var isArgEsc = objNode.object.get_escape_type() == EscapeType.ARG;

                nodeType = "[" + (isGlobalEsc ? "g" : (isArgEsc ? "a" : ""))
                        + (objNode.object.instance_field_written ? "w" : "")
                        + "OBJ]";
            } else if (node instanceof VariableNode) {
                nodeType = "[VAR]";
            }

            s.append(nodeType + node + " -> [ " + (node.get_edges().size() == 0 ? "*" : ""));

            for (var edge : node.get_edges()) {
                s.append(" " + edge);
            }

            s.append(" ]\n");
        });

        s.append("Globals : " + this.globals.size() + "\n");

        return s.toString();
    }

    public PTGraph makeCopy() {
        var newGraph = new PTGraph();

        // System.out.println(this.nodes);

        // make copy of Nodes
        this.nodes.forEach((nodeId, node) -> {
            Node newNode = null;

            // System.out.println("Processing : "+nodeId);

            if (node instanceof VariableNode) {
                newNode = new VariableNode(nodeId);
            } else if ((node instanceof ObjectNode)
                    && (((ObjectNode) node).object.get_escape_type() != EscapeType.GLOBAL)) {
                var newAllocationObject = ((ObjectNode) node).object;
                newNode = new ObjectNode(nodeId, newAllocationObject);
            }

            if (newNode != null)
                newGraph.nodes.put(newNode.id, newNode);
        });

        // copy references of global objects (For somereason, idk why but moving this
        // logic to below loop does not work and i get java.util.Concurrent.. error)
        this.globals.forEach((nodeId, node) -> {
            // don't create copies of global objects
            newGraph.nodes.put(node.id, new ObjectNode(node.id, ((ObjectNode) node).object));
        });

        // newGraph.nodes.putAll(this.globals);

        newGraph.globals = this.globals;

        // make copy of Edges
        this.nodes.forEach((nodeId, oldNode) -> {
            var newNode = newGraph.get_node(nodeId);

            for (var edge : oldNode.get_edges()) {
                Node newPointsToNode = newGraph.get_node(edge.points_to.id);

                if (newPointsToNode == null)
                    throw new RuntimeException("Edge's object ( " + edge.points_to.id + " ) not found");

                _Edge newEdge = null;

                if (edge instanceof ObjectFieldEdge) {
                    var edge_copy = ((ObjectFieldEdge) edge).make_copy();
                    edge_copy.set_point_to(newPointsToNode);
                    newEdge = edge_copy;
                } else if (edge instanceof VariableEdge) {
                    newEdge = new VariableEdge(newPointsToNode);
                }

                if (newEdge == null) {
                    throw new RuntimeException("FOUND NULL EDGE " + newPointsToNode);
                }

                if (newEdge.points_to == null) {
                    throw new RuntimeException(
                            "FOUND EDGE POINTER NULL (" + (newEdge) + ") POINTS_TO " + newPointsToNode);
                }

                newNode.add_edge(newEdge);
            }
        });

        return newGraph;
    }

    void combine(PTGraph otherGraph) {
        var otherGraph_copy = otherGraph.makeCopy();

        otherGraph_copy.nodes.forEach((nodeId, otherNode) -> {
            var currentGraphNode = this.nodes.get(nodeId);

            if (currentGraphNode == null) { // node is not present in the current graph, so create it
                this.nodes.put(nodeId, otherNode); // creates new node in graph
                return;
            }

            // after this currentGraphNode is existing so check if new edges have been added
            Set<String> thisNodeEdgeCache = new HashSet<>();

            for (var edge : currentGraphNode.get_edges()) {
                // check if the edge is already present
                thisNodeEdgeCache.add(edge.points_to.id);
            }

            for (var edge : otherNode.get_edges()) {
                if (thisNodeEdgeCache.contains(edge.points_to.id)) // skip duplicate edge
                    continue;

                currentGraphNode.add_edge(edge);
            }
        });
    }

    static String get_sf_node_id(SootField field) throws RuntimeException {
        if (!field.isStatic())
            throw new RuntimeException("Field " + field + " is not static");
        return "O_G_" + field.getSignature();
    }

    public Node get_node(String nodeId) {
        return this.nodes.get(nodeId);
    }

    public void add_node(Node node) {
        this.nodes.put(node.id, node);
    }

    public void add_global_node(Node rootNode) {
        this.add_node(rootNode);
        this.globals.put(rootNode.id, rootNode);

        if (rootNode instanceof ObjectNode) {
            ((ObjectNode) rootNode).object.set_escape_type(EscapeType.GLOBAL);

            // get all the child nodes as they have also escaped the global scope
            var decendants = Helper.getAllDecendantNodes(rootNode.id, this);

            // System.out.println(objNode + " Linked Nodes "+linkedNodes);

            // make all linked nodes of object type
            for (var childNode : decendants) {
                if (childNode instanceof ObjectNode) {
                    ((ObjectNode) childNode).object.set_escape_type(EscapeType.GLOBAL);
                    this.globals.put(childNode.id, childNode);
                }
            }
        }
    }

    public Set<String> getGlobalObjectIds() {
        return this.globals.keySet();
    }

    public Set<String> getAllNodeIds() {
        return this.nodes.keySet();
    }

    public Collection<Node> get_nodes() {
        return this.nodes.values();
    }

    public boolean containsNode(String nodeId) {
        return this.nodes.containsKey(nodeId);
    }

}

class DataFlowAnalResults {
    HashMap<Unit, PTGraph> inSet = new HashMap<>();
    HashMap<Unit, PTGraph> outSet = new HashMap<>();
    PTGraph exitSet = new PTGraph();
    Set<ObjectNode> returnValues = new HashSet<>();
    boolean returnNull = false;
    SootMethod method;
}

class InvokeExprAnalResults {
    List<DataFlowAnalResults> invokeResults = new ArrayList<>();
    HashMap<String, Set<ObjectNode>> parameters = new HashMap<>();
    PTGraph combinedExitSet = new PTGraph();
}

class ScalarReplaceableStats {
    // maps NodeId -> (function call line number they are called at)
    private HashMap<String, Set<String>> functionCallNumbers = new HashMap<>();

    public void add_func_call_lineNumber(String nodeId, String lineNumber) {
        if (!this.functionCallNumbers.containsKey(nodeId)) {
            functionCallNumbers.put(nodeId, new HashSet<>());
        }

        Set<String> lineNumberSet = functionCallNumbers.get(nodeId);
        lineNumberSet.add(lineNumber);
    }

    public Set<String> get_func_lineNumbers(String nodeId) {
        if (!this.functionCallNumbers.containsKey(nodeId)) {
            functionCallNumbers.put(nodeId, new HashSet<>());
        }

        return functionCallNumbers.get(nodeId);
    }
}

public class AnalysisTransformer extends SceneTransformer {
    static CallGraph cg;

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        // Store the call graph once as a static field...
        cg = Scene.v().getCallGraph();

        // This code lets us get the main method, our testcases will only have one start
        // point that is the main method
        // in the Test class...
        var entrypoints = Scene.v().getEntryPoints();
        assert (entrypoints.size() == 1);
        SootMethod entryMethod = entrypoints.get(0);

        handleMainMethod(entryMethod);
    }

    void handleMainMethod(SootMethod method) {
        var scalarReplaceableStats = new ScalarReplaceableStats();
        var globals = new PTGraph();

        // get all the global objects
        var globalNodes = Helper.getAllGlobalNodes();

        for (var node : globalNodes) {
            globals.add_global_node(node);
        }

        var result = Helper.performDataflowAnalysis(scalarReplaceableStats, method, null, globals, null);

        var objNodes = result.exitSet.get_nodes().stream()
                .filter(node -> (node instanceof ObjectNode))
                .sorted((n1, n2) -> {
                    var l1 = ((ObjectNode) n1).object.creation_line_number;
                    var l2 = ((ObjectNode) n2).object.creation_line_number;
                    return Integer.compare(l1, l2);
                }).toList();

        for (var node : objNodes) {
            if (!(node instanceof ObjectNode) || globals.containsNode(node.id))
                continue;

            var objNode = (ObjectNode) node;

            var lineNums = scalarReplaceableStats.get_func_lineNumbers(node.id)
                    .stream()
                    .sorted((numStr1, numStr2) -> {
                        return Integer.compare(Integer.valueOf(numStr1), Integer.valueOf(numStr2));
                    }).toList();

            var funcLineNumbers = objNode.object.is_scalar_replaceable ? String.join(",", lineNums) : "";

            var printObjID = "O" + String.valueOf(objNode.object.creation_line_number);

            System.out.println(printObjID + " = "
                    + (objNode.object.is_scalar_replaceable ? "Y" + "[" + funcLineNumbers + "]" : "N"));
        }
    }

}

class Helper {
    static DataFlowAnalResults performDataflowAnalysis(ScalarReplaceableStats scalarReplceStats, SootMethod method,
            HashMap<String, Set<ObjectNode>> parameters, PTGraph globals, PTGraph preContextGraph) {
        // System.out.println("Analyzing method : " + method + " " + "\nParameters : " +
        // parameters);

        HashMap<Unit, PTGraph> inSet = new HashMap<>();
        HashMap<Unit, PTGraph> outSet = new HashMap<>();
        var result = new DataFlowAnalResults();

        result.inSet = inSet;
        result.outSet = outSet;
        result.method = method;

        Body body = method.getActiveBody();

        // empty body
        if (body.getUnits().size() <= 0)
            return result;

        UnitGraph graph = new BriefUnitGraph(body);

        // System.out.println("precontext graph\n"+preContextGraph);

        var localVarGraph = globals != null ? globals : new PTGraph();

        // System.out.println("Processing method - "+method);

        for (var local : body.getLocals()) { // initialize all local variables

            var localType = local.getType();

            // if variable is not of primitive type
            if (!(localType instanceof RefLikeType)) {
                continue;
            }

            var localVarNode = new VariableNode(local.getName());
            localVarGraph.add_node(localVarNode);
        }

        // initialize In & Out Sets for all unit sets
        for (Unit u : body.getUnits()) {
            inSet.put(u, localVarGraph.makeCopy());
            outSet.put(u, new PTGraph());
        }

        { // dont let initialPTGraph be used anywhere else in the code

            var initialPTGraph = localVarGraph.makeCopy();

            if (preContextGraph != null) { // contains all parameter objects and desendant objects of parameters
                var preContextGraph_copy = preContextGraph.makeCopy();
                initialPTGraph.combine(preContextGraph_copy);
            }

            inSet.put(body.getUnits().getFirst(), initialPTGraph);

            // System.out.println("Initial graph\n"+initialPTGraph);
        }

        // only copies the local variables
        PTGraph exitOutset = localVarGraph.makeCopy();

        List<Local> returnLocalVars = new Vector<>();

        var parameterObjectIds = new HashSet<String>();

        // // build in & out sets for each unit stmt
        for (Unit u : body.getUnits()) {

            // System.out.println("Starting "+u);

            var lineNumber = u.getJavaSourceStartLineNumber();

            // System.out.println("processed "+ u);
            var combinedInSet = inSet.get(u);

            // System.out.println("combined_set is null : " + (combinedInSet == null ?
            // "True" : "False"));

            // // combine outSets of Predecessars
            for (var predUnit : graph.getPredsOf(u)) {
                var predOutSet = outSet.get(predUnit);
                // System.out.println(predOutSet.nodes);
                combinedInSet.combine(predOutSet);
            }

            inSet.put(u, combinedInSet);
            var uOutSet = combinedInSet.makeCopy();

            // only found at the beginin of body & specifies source of data
            // RHS is always ThisRef, ParameterRef, CaughtExceptionRef
            if (u instanceof IdentityStmt) {
                IdentityStmt stmt = (IdentityStmt) u;
                var rightOp = stmt.getRightOp();
                var leftOp = stmt.getLeftOp(); // leftOp is always Local for IdentityStmt

                var localVarNode = uOutSet.get_node(leftOp.toString());

                // check if we already have object edges from parameters
                if (parameters != null && (rightOp.getType() instanceof RefLikeType)
                        && (rightOp instanceof ParameterRef)
                        && (parameters.containsKey(String.valueOf(((ParameterRef) rightOp).getIndex())))) {
                    // System.out.println(preLocalEdges.get((((ParameterRef)rightOp).getIndex())) +
                    // " ASDSADASD "+ ((ParameterRef)rightOp).getIndex() + " " +preLocalEdges);

                    var paramValues = parameters.get(String.valueOf(((ParameterRef) rightOp).getIndex()));

                    if (paramValues != null) {
                        boolean containsAnyObjectNode = false;

                        for (var param : paramValues) {
                            if (param != null) {
                                containsAnyObjectNode = true;
                                break;
                            }
                        }

                        // foo(null)
                        // foo(Obj)
                        // then parameter will point to Obj
                        if (!containsAnyObjectNode) {
                            localVarNode.get_edges().clear(); // parameter is null so clear any connected edges
                        } else {
                            for (var paramVal : paramValues) {
                                if (paramVal != null) {
                                    var _objNode = uOutSet.get_node(paramVal.id);
                                    localVarNode.add_edge(new VariableEdge(_objNode));

                                    Helper.set_parameter_escape_status((ObjectNode) _objNode, uOutSet,
                                            parameterObjectIds);
                                }
                            }
                        }
                    }
                } else if (rightOp.getType() instanceof RefType) { // right-hand side is a object (rightOp = @this,
                                                                   // @parameter0: Obj)

                    if (rightOp instanceof ThisRef && parameters != null) {

                        var parameterReferences = parameters.get("this");

                        for (var paramObjNode : parameterReferences) {
                            var objNode = uOutSet.get_node(paramObjNode.id);
                            localVarNode.add_edge(new VariableEdge(objNode));

                            // dont escape the root (@this) object if the invokation is constructor
                            // as (@this) objects dont really escape in their own constructor
                            // but their children is considered as ARG_ESCAPE
                            if (method.isConstructor()) {
                                for (var childNodeEdge : objNode.get_edges()) {
                                    var childNode = (ObjectNode) childNodeEdge.points_to;
                                    Helper.set_parameter_escape_status(childNode, uOutSet, parameterObjectIds);
                                }
                            } else {
                                Helper.set_parameter_escape_status((ObjectNode) objNode, uOutSet, parameterObjectIds);
                            }
                        }
                    } else {
                        ObjectNode objectNode = ObjectNode.make((RefType) rightOp.getType(), lineNumber);

                        if (rightOp instanceof ParameterRef) {
                            objectNode.id = "P_" + objectNode.id;

                            set_parameter_escape_status(objectNode, preContextGraph, parameterObjectIds);
                        }

                        uOutSet.add_node(objectNode);

                        var edge = new VariableEdge(objectNode);
                        localVarNode.add_edge(edge);
                    }

                } else if (rightOp.getType() instanceof ArrayType) { // handle array
                    // ignore arrays as it's not in assignment

                    // ObjectNode objectNode = ObjectNode.make("A_"+rightOp.toString(),
                    // (ArrayType)rightOp.getType());
                    // if(rightOp instanceof ParameterRef){
                    // var param = (ParameterRef) rightOp;

                    // objectNode.id = "P_" + objectNode.id;
                    // objectNode.object.set_escape_type(EscapeType.ARG);
                    // objectNode.object.set_parameter(true);
                    // }
                    // uOutSet.nodes.put(objectNode.id, objectNode);
                    // var edge = new VariableEdge(objectNode);
                    // localVarNode.add_edge(edge);
                }
            } else if (u instanceof AssignStmt) {
                var stmt = (AssignStmt) u;
                var leftOp = stmt.getLeftOp();
                var rightOp = stmt.getRightOp();

                if ((leftOp instanceof Local) && (leftOp.getType() instanceof RefLikeType)) { // r0 = *
                    var lhsVarNode = uOutSet.get_node(leftOp.toString());

                    // clear previous edges
                    lhsVarNode.get_edges().clear();

                    if (rightOp instanceof Local) {
                        var rhsVarNode = uOutSet.get_node(rightOp.toString());

                        lhsVarNode.set_edges(rhsVarNode.get_edges());
                    } else if (rightOp instanceof NewExpr) { // r0 = new M()
                        var objectNode = ObjectNode.make((RefType) rightOp.getType(), lineNumber);

                        lhsVarNode.get_edges().clear(); // clear previous edges

                        uOutSet.add_node(objectNode);

                        lhsVarNode.add_edge(new VariableEdge(objectNode));
                    } else if (rightOp instanceof NewArrayExpr) { // r0 = new int[0] - only for single dimentional array
                        // ignore arrays as they will not be present in assignment
                    } else if (rightOp instanceof NewMultiArrayExpr) { // r0 = new int[10][10] - only for multi
                                                                       // dimentional array
                        // ignore arrays as they will not be present in assignment
                    } else if (rightOp instanceof CastExpr) {

                        // TODO: deep dive more into this
                        // as if we cast to higher hierarchy class then we can only access parent fields
                        // but if we again recast to it's original form then we can agin access the
                        // fields

                        var castExpr = (CastExpr) rightOp;

                        if (castExpr.getOp() instanceof Local) { // r0 = (Class) r1;
                            var rhsVarNode = uOutSet.get_node(castExpr.getOp().toString());
                            lhsVarNode.set_edges(rhsVarNode.get_edges());
                        }
                    } else if (rightOp instanceof InstanceFieldRef) { // r0 = r1.<M: (Object|Primitive) x>;
                        var rhs = (InstanceFieldRef) rightOp;
                        var fieldRef = rhs.getFieldRef();
                        var fieldType = rhs.getField().getType();

                        var rhsVarNode = uOutSet.get_node(((Local) rhs.getBase()).toString());

                        // sorted
                        var fieldId = ObjectFieldEdge.make_field_id(fieldRef);

                        if (fieldType instanceof RefLikeType) {
                            for (var objNodeEdge : rhsVarNode.get_edges()) {
                                var objNode = objNodeEdge.points_to;

                                for (var objFieldEdge : objNode.get_edges()) {
                                    if (((ObjectFieldEdge) objFieldEdge).get_id().equals(fieldId)) {
                                        lhsVarNode.add_edge(new VariableEdge(objFieldEdge.points_to));
                                    }
                                }
                            }
                        }
                    } else if (rightOp instanceof StaticFieldRef) { // r0 = Class.Field
                        var rhs = (StaticFieldRef) rightOp;
                        var field = rhs.getField();

                        if ((field.getDeclaringClass().isApplicationClass())
                                && (field.getType() instanceof RefLikeType)) {
                            var objectNode = uOutSet.get_node(PTGraph.get_sf_node_id(field));
                            // System.out.println(method+ " " +objectNode+ " "
                            // +PTGraph.get_sf_node_id(field));
                            // System.out.println(uOutSet.globals);
                            // System.out.println(objectNode==null);
                            var varEdge = new VariableEdge(objectNode);
                            lhsVarNode.add_edge(varEdge);
                        }

                    } else if (rightOp instanceof NullConstant) { // r0 = null
                        lhsVarNode.get_edges().clear();
                    } else if (rightOp instanceof InvokeExpr) { // r0 = foo(o2)
                        var invokeExpr = (InvokeExpr) rightOp;
                        var returnType = invokeExpr.getMethod().getReturnType();

                        lhsVarNode.get_edges().clear(); // clear all previous edges becoz it's value will come from
                                                        // invokedMethod's return value

                        var preCallContext = uOutSet.makeCopy();

                        var exprAnalResults = analyzeInvokeExpr(scalarReplceStats, (Stmt) u, invokeExpr, preCallContext,
                                globals, uOutSet);

                        if (exprAnalResults != null) {
                            boolean returnsAnyNonNullObject = false;

                            var returnedObjectIds = new HashSet<String>();

                            for (var invokeResult : exprAnalResults.invokeResults) {
                                if (invokeResult == null)
                                    continue;

                                // System.out.println("Before :\n"+uOutSet);

                                // update parameter objects if they are modified
                                for (var paramObjs : exprAnalResults.parameters.values()) {
                                    for (var paramObj : paramObjs) {
                                        if (paramObj == null)
                                            continue;
                                        returnedObjectIds.add(paramObj.id);
                                    }
                                }

                                // System.out.println("After :\n"+uOutSet);

                                if (invokeResult.returnValues.size() > 0)
                                    returnsAnyNonNullObject = true;

                                if (returnsAnyNonNullObject && returnType instanceof RefLikeType) { // method is
                                                                                                    // returning a
                                                                                                    // object / array
                                    var combinedExitSet_copy = exprAnalResults.combinedExitSet.makeCopy();

                                    // objs from return stmt (return $r0);
                                    for (var objId : returnedObjectIds) {
                                        if (objId == null)
                                            continue;

                                        var updatedNode = combinedExitSet_copy.get_node(objId);

                                        if (!preCallContext.containsNode(objId)) {
                                            // newly created object but returned
                                            ((ObjectNode) updatedNode).object.is_stack_allocatable = false;
                                            ((ObjectNode) updatedNode).object.is_scalar_replaceable = false;
                                        }

                                        uOutSet.add_node(updatedNode);
                                        var childNodes = getAllDecendantNodes(updatedNode.id, combinedExitSet_copy);

                                        // System.out.println("Returned Object Child ("+ updatedNode + "): " +
                                        // childNodes);

                                        for (var node : childNodes) {
                                            if (!preCallContext.containsNode(node.id)) {
                                                // newly created object but returned
                                                ((ObjectNode) node).object.is_stack_allocatable = false;
                                                ((ObjectNode) node).object.is_scalar_replaceable = false;
                                            }

                                            uOutSet.add_node(node);
                                        }

                                        lhsVarNode.add_edge(new VariableEdge(updatedNode));
                                    }
                                }
                            }

                            if (returnType instanceof RefLikeType && !returnsAnyNonNullObject) { // methods are
                                                                                                 // returning only null
                                lhsVarNode.get_edges().clear();
                            }
                        }
                    }
                } else if (leftOp instanceof InstanceFieldRef) { // r0.<CLass:Type> = r0 | constant;

                    var lhs = (InstanceFieldRef) leftOp;
                    var lhsVarNode = uOutSet.get_node(((Local) lhs.getBase()).toString());
                    var lhsFieldRef = lhs.getFieldRef();
                    var lhsFieldId = ObjectFieldEdge.make_field_id(lhsFieldRef);
                    var lhsField = lhs.getField();

                    // System.out.println("LHS FieldID : "+ lhsFieldId);

                    if (lhsField.getType() instanceof RefLikeType) { // the field being accessed should be of type
                                                                     // Object, not a primitive field

                        // from lhsVarNode get the objects they are pointing to
                        if (rightOp instanceof Local) { // r0.<CLass:Type> = r2
                            var rhsVarNode = uOutSet.get_node(((Local) rightOp).toString());

                            for (var lhsObjEdge : lhsVarNode.get_edges()) {

                                var lhsObjNode = (ObjectNode) (lhsObjEdge.points_to);

                                // remove all edges with the field being accessed
                                lhsObjNode.get_edges().removeIf((edge) -> {

                                    // System.out.println("EDGE FIELD ID : "+((ObjectFieldEdge) edge).get_id());

                                    if (((ObjectFieldEdge) edge).get_id().equals(lhsFieldId)) {
                                        // System.out.println("REMOVING FIELD ("+lhsObjNode+"): "+lhsFieldId);
                                    }

                                    return ((ObjectFieldEdge) edge).get_id().equals(lhsFieldId);
                                });

                                for (var rhsObjEdge : rhsVarNode.get_edges()) {
                                    var rhsObjNode = (ObjectNode) rhsObjEdge.points_to;

                                    lhsObjNode.add_edge(new ObjectFieldEdge(lhsFieldRef, rhsObjNode));
                                }
                            }
                        } else if (rightOp instanceof NullConstant) {

                            for (var objEdge : lhsVarNode.get_edges()) {

                                var objNode = (ObjectNode) (objEdge.points_to);

                                // remove all edges with the field being accessed
                                objNode.get_edges()
                                        .removeIf((edge) -> ((ObjectFieldEdge) edge).get_id().equals(lhsFieldId));
                            }
                        }
                    }

                    var TwoOrMoreObjects = (lhsVarNode.get_edges().size() > 1);

                    var containsAtleastOneParamObj = false;

                    // get all the objects lhsVarNode is pointing to and set the object written flag
                    for (var edge : lhsVarNode.get_edges()) {
                        if (edge.points_to instanceof ObjectNode) {
                            var lhsObjectNode = (ObjectNode) edge.points_to;

                            if (lhsObjectNode.object.is_scalar_replaceable) {
                                // (Obj1 | Obj2).instance_field = ...
                                lhsObjectNode.object.is_scalar_replaceable = !TwoOrMoreObjects;

                                if (lhsObjectNode.object.is_scalar_replaceable)
                                    lhsObjectNode.object.is_scalar_replaceable = lhsObjectNode.object
                                            .get_escape_type() != EscapeType.ARG;
                            }

                            if (!lhsObjectNode.object.is_scalar_replaceable) // object is not scalar replaceable because
                                                                             // a instance field is written
                                lhsObjectNode.object.instance_field_written = true;

                            if (parameterObjectIds.contains(lhsObjectNode.id)) {
                                containsAtleastOneParamObj = true;
                            }
                        }
                    }

                    if (containsAtleastOneParamObj && (rightOp instanceof Local)) {
                        var rhsVarNode = uOutSet.get_node(((Local) rightOp).toString());

                        for (var rhsObjNodeEdge : rhsVarNode.get_edges()) {
                            var rhsObjNode = (ObjectNode) rhsObjNodeEdge.points_to;

                            rhsObjNode.object.is_stack_allocatable = false;
                            rhsObjNode.object.is_scalar_replaceable = false;
                        }
                    }
                } else if (leftOp instanceof StaticFieldRef) { // Class.Staticfield = <obj> | null

                    var lhs = (StaticFieldRef) leftOp;
                    var lhsField = lhs.getField();

                    if (lhsField.getType() instanceof RefLikeType) { // the field being accessed should be of type
                                                                     // Object, not a primitive field

                        if (rightOp instanceof Local) { // we only care about CLass.StaticField = r0; where r0 is a
                                                        // object
                            var rhsVarNode = uOutSet.get_node(rightOp.toString());

                            // System.out.println(method + " " + u);

                            for (var objEdge : rhsVarNode.get_edges()) {
                                // object has escaped to global scope
                                uOutSet.add_global_node((ObjectNode) objEdge.points_to);
                            }
                        }
                        // if rightOp is null then we dont care as other thread can modify it
                    }
                }
            }

            // handling invoke calls (this.bar(o2))
            else if (u instanceof InvokeStmt) { // static Class.foo() or obj.foo() , does not store value in local
                                                // variable
                var invokeExpr = ((InvokeStmt) u).getInvokeExpr();

                var exprAnalResults = analyzeInvokeExpr(scalarReplceStats, (Stmt) u, invokeExpr, uOutSet, globals,
                        uOutSet);

                if (exprAnalResults != null) {
                    for (var invokeResult : exprAnalResults.invokeResults) {
                        if (invokeResult == null)
                            continue;

                        for (var paramObjs : exprAnalResults.parameters.values()) {

                            for (var paramObj : paramObjs) {
                                if (paramObj == null)
                                    continue;

                                // System.out.println(exprAnalResults.combinedExitSet.nodes.get(paramObj.id));

                                // update the root object
                                uOutSet.add_node(exprAnalResults.combinedExitSet.get_node(paramObj.id));

                                var childNodes = getAllDecendantNodes(paramObj.id, exprAnalResults.combinedExitSet);
                                // System.out.println("Param childrens ("+ (paramObj) +")
                                // "+invokeResult.method+" : " + childNodes);

                                // new connections or new objects might've been connected to parameterObject, so
                                // update the node
                                for (var node : childNodes) {
                                    uOutSet.add_node(node);
                                }
                            }
                        }
                    }
                    // no need to process returnNodes as
                }
            }

            // update the outset graph
            outSet.put(u, uOutSet);

            if (u instanceof ReturnStmt || u instanceof ReturnVoidStmt) {

                if (u instanceof ReturnStmt) {
                    var returnStmt = ((ReturnStmt) u);
                    var returnVal = returnStmt.getOp();

                    if (returnVal.getType() instanceof RefLikeType) { // return r0; r0 is returnVal

                        if (returnVal instanceof Local) {
                            returnLocalVars.add(((Local) returnVal));
                        } else if (returnVal instanceof NullConstant) {
                            result.returnNull = true;
                        }

                    }
                }

                exitOutset.combine(uOutSet);
            }

            // System.out.println(uOutSet);
            // System.out.println("Ended "+u);
        }

        // System.out.println("Exit Context\n" +exitOutset);

        result.exitSet = exitOutset;

        // if(preContextGraph != null){
        // exitOutset.get_nodes().forEach((node) -> {
        // if(!(node instanceof ObjectNode)) return;
        // // newly created objects are non-scalar replaceable as they escape the stack
        // if(!preContextGraph.containsNode(node.id)){
        // System.out.println("NEW NODE : " + node.id);

        // ((ObjectNode)node).object.is_scalar_replaceable = false;
        // }
        // });
        // }

        return result;
    }

    static HashMap<String, Set<ObjectNode>> getParameterValues(InvokeExpr invokeExpr, PTGraph uOutSet) {
        var invokingMethodPreLocalEdges = new HashMap<String, Set<ObjectNode>>(); // maps (argIndex -> ObjectEdges)

        // store information of @this
        if (invokeExpr instanceof InstanceInvokeExpr) {

            var instanceInvokeExpr = (InstanceInvokeExpr) invokeExpr;

            var base = instanceInvokeExpr.getBase();

            if (base instanceof Local && base.getType() instanceof RefLikeType) {
                var localVarNode = uOutSet.get_node(base.toString());

                Set<ObjectNode> objects = new HashSet<>();

                for (var objEdge : localVarNode.get_edges()) {
                    objects.add((ObjectNode) objEdge.points_to);
                }

                invokingMethodPreLocalEdges.put("this", objects);
            }

        }

        for (Integer i = 0; i < invokeExpr.getArgCount(); i++) {
            var arg = invokeExpr.getArg(i);

            if (arg.getType() instanceof RefLikeType) {
                // set of objects pointed by localVarNode
                var pointedObjects = new HashSet<ObjectNode>();

                if (arg instanceof Local) {
                    var localVarNode = uOutSet.get_node(((Local) arg).getName());

                    for (var objEdge : localVarNode.get_edges()) {
                        pointedObjects.add((ObjectNode) objEdge.points_to);
                    }
                } else if (arg instanceof NullConstant) {
                    pointedObjects.add(null);
                }

                invokingMethodPreLocalEdges.put(String.valueOf(i), pointedObjects);
            }
        }

        return invokingMethodPreLocalEdges;
    }

    static List<Node> getAllDecendantNodes(String rootNodeId, PTGraph graph) {
        Queue<Node> travelNodes = new LinkedList<>();
        List<Node> linkedNodes = new Vector<Node>();

        var alreadyTraversedNodeIds = new HashSet<String>();

        var rootNode = graph.get_node(rootNodeId);

        travelNodes.add(rootNode);

        while (!travelNodes.isEmpty()) {
            var travelNode = travelNodes.poll();

            linkedNodes.add(travelNode);
            // System.out.println("Added " + travelNode);

            // System.out.println(travelNode + " " + travelNode.edges);

            for (var edge : travelNode.get_edges()) {
                if (alreadyTraversedNodeIds.contains(edge.points_to.id)) {
                    // System.out.println("Already present " + travelNode);
                    continue;
                }

                travelNodes.add(edge.points_to);
                alreadyTraversedNodeIds.add(travelNode.id);
            }
        }

        // remove root as we only want childNodes
        linkedNodes.removeIf((n) -> n.id.equals(rootNode.id));

        return linkedNodes;
    }

    static List<Node> getAllGlobalNodes() {
        List<Node> globalNodes = new LinkedList<>();

        for (var c : Scene.v().getApplicationClasses()) {
            globalNodes.addAll(getAllStaticFieldObjNodes(c));
        }

        return globalNodes;
    }

    static List<ObjectNode> getAllStaticFieldObjNodes(SootClass c) { // only returns static fields which are of Object
                                                                     // type
        List<ObjectNode> objectNodes = new LinkedList<>();

        for (SootField field : c.getFields()) {
            if (field.isStatic() && (field.getType() instanceof RefLikeType)) {
                var obj = new AllocationObject(Integer.valueOf(field.getJavaSourceStartLineNumber()));
                obj.set_escape_type(EscapeType.GLOBAL);
                objectNodes.add(new ObjectNode(PTGraph.get_sf_node_id(field), obj));
            }
            ;
        }

        return objectNodes;
    }

    static InvokeExprAnalResults analyzeInvokeExpr(ScalarReplaceableStats scalarReplceStats, Stmt stmt,
            InvokeExpr invokeExpr, PTGraph __callerContextGraph, PTGraph globals, PTGraph postCallContext) {
        var _inkMethod = invokeExpr.getMethod();

        if (!_inkMethod.getDeclaringClass().isApplicationClass())
            return null;

        var hierarchy = Scene.v().getActiveHierarchy();


        // System.out.println("Got method (
        // "+hierarchy.resolveAbstractDispatch(_inkMethod.getDeclaringClass(),
        // _inkMethod)+" ): "+ _inkMethod);
        var preContextGraph = new PTGraph();
        var result = new InvokeExprAnalResults();
        var callerContextGraph_copy = __callerContextGraph.makeCopy();

        result.parameters = getParameterValues(invokeExpr, callerContextGraph_copy);

        if (!_inkMethod.isConstructor())
            for (var paramObjSet : result.parameters.values()) {
                for (var paramObj : paramObjSet) {
                    scalarReplceStats.add_func_call_lineNumber(paramObj.id,
                            String.valueOf(stmt.getJavaSourceStartLineNumber()));
                    ;
                }
            }

        result.combinedExitSet = new PTGraph();

        List<SootMethod> methods = null;

        if (invokeExpr instanceof SpecialInvokeExpr) {
            methods = new ArrayList<>();
            methods.add(hierarchy.resolveSpecialDispatch((SpecialInvokeExpr) invokeExpr, _inkMethod));
        } else {
            methods = hierarchy.resolveAbstractDispatch(_inkMethod.getDeclaringClass(), _inkMethod);
        }

        var not_arg_escaped_objectIds = new HashSet<String>();

        if (methods.size() > 0) // if no methods are executed then dont do extra work
            for (var node : callerContextGraph_copy.get_nodes()) {
                if (node instanceof ObjectNode) {
                    if (((ObjectNode) node).object.get_escape_type() != EscapeType.ARG) {
                        not_arg_escaped_objectIds.add(node.id);
                    }
                }
            }

        // System.out.println("Possible Method Invocation : "+ methods);

        for (var invokingMethod : methods) {

            Set<String> preContextObjectIds = new HashSet<>();

            result.parameters.forEach((paramIndex, paramObjs) -> {
                for (var paramObj : paramObjs) {
                    if (paramObj == null)
                        continue;

                    for (var childObj : getAllDecendantNodes(paramObj.id, callerContextGraph_copy)) {
                        preContextObjectIds.add(childObj.id); // add all child nodes of rootNode (paramObj)
                    }

                    preContextObjectIds.add(paramObj.id); // add root node as well
                }
            });

            for (var nodeId : preContextObjectIds) {
                preContextGraph.add_node(callerContextGraph_copy.get_node(nodeId));
            }

            var invokeResult = performDataflowAnalysis(scalarReplceStats, invokingMethod, result.parameters, globals,
                    preContextGraph);

            if (invokeResult != null) {
                // System.out.println("Method analysis complete " + invokingMethod);

                result.invokeResults.add(invokeResult);
                result.combinedExitSet.combine(invokeResult.exitSet.makeCopy());
            }

        }

        // not arg esc
        // arg esc

        var arg_escaped_object_ids = new HashSet<String>();

        if (methods.size() > 0) // if no methods are executed then dont do extra work
            for (var node : result.combinedExitSet.get_nodes()) {
                if (node instanceof ObjectNode) {
                    var alloObj = ((ObjectNode) node).object;

                    if (alloObj.is_scalar_replaceable && (alloObj.get_escape_type() == EscapeType.ARG)) {
                        arg_escaped_object_ids.add(node.id);
                    }
                }
            }

        arg_escaped_object_ids.retainAll(not_arg_escaped_objectIds);

        // AFWObjectIds.retainAll(ANObjectIds);

        for (var objNodeId : arg_escaped_object_ids) {
            ((ObjectNode) result.combinedExitSet.get_node(objNodeId)).object
                    .set_escape_type_no_priority(EscapeType.NO_ESCAPE);
        }

        var combinedExitSet_copy = result.combinedExitSet.makeCopy();

        for (var node : combinedExitSet_copy.get_nodes()) { // return r0;
            if (!(node instanceof ObjectNode))
                continue;

            if (!callerContextGraph_copy.containsNode(node.id)) { // add newly created nodes in the postCallGraph
                postCallContext.add_node(node);
            }
        }

        // System.out.println("combined exitset : "+result.combinedExitSet);

        return result;
    }

    static void set_node_escape_type(String NodeId, EscapeType type, PTGraph context) {
        var objNode = context.get_node(NodeId);

        if (objNode instanceof ObjectNode) {

            ((ObjectNode) objNode).object.set_escape_type(type);

            for (var child : getAllDecendantNodes(NodeId, context)) {
                if (child instanceof ObjectNode) {
                    ((ObjectNode) child).object.set_escape_type(type);
                }
            }
        }
    }

    static void set_parameter_escape_status(ObjectNode rootNode, PTGraph context, Set<String> parameterObjectIds) {
        rootNode.object.set_escape_type(EscapeType.ARG);
        rootNode.object.set_parameter(true);

        parameterObjectIds.add(rootNode.id);

        // the children also escapes
        for (var child : getAllDecendantNodes(rootNode.id, context)) {
            if (child instanceof ObjectNode) {
                ((ObjectNode) child).object.set_escape_type(EscapeType.ARG);
                ((ObjectNode) child).object.set_parameter(true);

                parameterObjectIds.add(child.id);
            }
        }
    }

}

// TODO: detect if a object is arg escape and during that time it is written
// then only mark it as non-scalar replaceable

// TODO: detect if a object is returned from a method to make it non-scalar
// replaceable

// TODO: Test InterfaceInvoke Expr

// TODO: for newly created objects
// make decendant objects of returning objects a