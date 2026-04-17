class Node { Node left; Node right; }

public class Test {
    static Node identity(Node x) { return x; }

    public static void main(String[] args) {
        Node n1 = new Node();    // obj1
        Node n2 = identity(n1); // n2 -> obj1 (alias)
        Node l  = new Node();   // obj2
        Node r  = new Node();   // obj3

        n1.left  = l;
        n1.right = r;
        // Expected:
        //   n1.left  -> obj2,  n2.left  -> obj2
        //   n1.right -> obj3,  n2.right -> obj3

        System.out.println(n1);
        System.out.println(n2);
    }
}
