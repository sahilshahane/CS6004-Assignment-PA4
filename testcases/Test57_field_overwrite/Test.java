class Node { Node child; }

public class Test {
    static Node identity(Node x) { return x; }

    public static void main(String[] args) {
        Node n1 = new Node();   // obj1
        Node n2 = identity(n1); // n2 -> obj1 (alias)
        Node v1 = new Node();   // obj2
        Node v2 = new Node();   // obj3

        n1.child = v1;
        // n1.child -> obj2, n2.child -> obj2

        n1.child = v2;
        // n1.child -> obj3 (overwrite), n2.child -> obj3
        // old obj2 facts should be killed

        System.out.println(n1);
        System.out.println(n2);
    }
}
