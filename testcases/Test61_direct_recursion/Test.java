class Node { Node child; }

public class Test {
    // Direct recursion: each call stores into n.child
    static void recurse(Node n, int count) {
        if (count > 0) {
            Node v = new Node();   // fresh obj each call
            n.child = v;
            recurse(n, count - 1);
        }
    }

    public static void main(String[] args) {
        Node n1 = new Node();  // obj1
        recurse(n1, 3);
        // Expected: n1.child -> some set of obj allocated inside recurse()
        System.out.println(n1);
    }
}
