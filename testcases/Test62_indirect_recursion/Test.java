class Node { Node child; }

public class Test {
    static void a(Node n, int count) {
        if (count > 0) {
            Node v = new Node();
            n.child = v;
            b(n, count - 1);
        }
    }

    static void b(Node n, int count) {
        if (count > 0) {
            Node w = new Node();
            n.child = w;
            a(n, count - 1);
        }
    }

    public static void main(String[] args) {
        Node n1 = new Node();  // obj1
        a(n1, 3);
        // Expected: n1.child -> objs allocated inside a() and b()
        System.out.println(n1);
    }
}
