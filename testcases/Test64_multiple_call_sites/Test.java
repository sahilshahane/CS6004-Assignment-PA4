class Node { Node child; }

public class Test {
    static void setChild(Node n, Node val) {
        n.child = val;
    }

    public static void main(String[] args) {
        Node a = new Node();  // obj1
        Node b = new Node();  // obj2
        Node v1 = new Node(); // obj3
        Node v2 = new Node(); // obj4

        setChild(a, v1);  // call site 1
        setChild(b, v2);  // call site 2

        // Expected: a.child -> obj3 only (NOT obj4)
        //           b.child -> obj4 only (NOT obj3)
        // No cross-contamination between call sites.
        System.out.println(a);
        System.out.println(b);
    }
}
