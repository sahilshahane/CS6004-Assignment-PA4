class Node { Node child; }

public class Test {
    static Node identity(Node x) { return x; }

    public static void main(String[] args) {
        Node n1 = new Node();    // obj1
        Node n2 = identity(n1);  // n2 -> obj1 (alias)
        Node v1 = new Node();    // obj2
        Node v2 = new Node();    // obj3

        if (args.length > 0) {
            n1.child = v1;  // branch A
        } else {
            n1.child = v2;  // branch B
        }
        // Expected at join: n1.child -> {obj2, obj3}, n2.child -> {obj2, obj3}
        System.out.println(n1);
        System.out.println(n2);
    }
}
