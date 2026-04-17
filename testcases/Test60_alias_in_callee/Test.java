class Node { Node child; }

public class Test {
    // Returns the same object — alias created in callee, returned to caller
    static Node makeAlias(Node x) { return x; }

    static void setField(Node a, Node val) {
        Node alias = makeAlias(a); // alias -> same obj as a
        alias.child = val;         // store via alias inside callee
        // Expected: a.child -> val AND any caller alias of a -> val
    }

    public static void main(String[] args) {
        Node n1  = new Node();  // obj1
        Node n2  = new Node();  // obj2 (used as val)

        setField(n1, n2);
        // Expected: n1.child -> obj2

        System.out.println(n1);
    }
}
