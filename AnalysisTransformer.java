import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;

import jas.Var;
import soot.*;
import soot.jimple.*;
import soot.jimple.toolkits.callgraph.CallGraph;
import soot.jimple.toolkits.callgraph.Edge;
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

    static Node make_obj(String id) {
        return new Node("O_" + id);
    }

    static Node make_var(String id) {
        return new Node("V_" + id);
    }\

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Node))
            return false;

        (Node)obj
    }
}

class Edge {
    Node node1;
    Node node2;

    private Edge(Node n1, Node n2) {
        this.node1 = n1;
        this.node2 = n2;
    }

    static Edge make(Node n1, Node n2) {
        return new Edge(n1, n2);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Edge))
            return false;

        return 
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
