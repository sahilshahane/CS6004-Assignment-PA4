class Node {
    Node child;
}

public class Test {

    // Forces n2 to be a distinct Jimple local pointing to the same object as n1
    static Node identity(Node x) {
        return x;
    }

    static void setChild(Node a, Node val) {
        a.child = val;
    }

    public static void main(String[] args) {
        Node n1 = new Node();    // obj1
        Node n2 = identity(n1); // n2 -> obj1 (distinct local, same target)
        Node c  = new Node();   // obj2

        setChild(n1, c);
        // Expected after call:
        //   n1.child -> obj2   (direct callerBase update)
        //   n2.child -> obj2   (alias update)

        System.out.println(n1);
        System.out.println(n2);
    }
}
