class Node { Node child; }

public class Test {
    // Three-level call chain: main -> level1 -> level2 -> store
    static void level2(Node p, Node val) {
        p.child = val;
    }

    static void level1(Node q, Node val) {
        level2(q, val);
    }

    public static void main(String[] args) {
        Node n1 = new Node();  // obj1
        Node v  = new Node();  // obj2
        level1(n1, v);
        // Expected: n1.child -> obj2
        // NOTE: with k=1 context, propagation through 2 levels may be partial.
        System.out.println(n1);
    }
}
