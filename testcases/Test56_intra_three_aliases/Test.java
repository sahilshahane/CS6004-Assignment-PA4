class Node { Node child; }

public class Test {
    static Node identity(Node x) { return x; }

    public static void main(String[] args) {
        Node n1 = new Node();       // obj1
        Node n2 = identity(n1);     // n2 -> obj1
        Node n3 = identity(n1);     // n3 -> obj1
        Node val = new Node();      // obj2

        n1.child = val;
        // Expected: n1.child, n2.child, n3.child all -> obj2

        System.out.println(n1);
        System.out.println(n2);
        System.out.println(n3);
    }
}
