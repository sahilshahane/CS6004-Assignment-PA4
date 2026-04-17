class Node {
    Node child;
}

public class Test {

    // Inside here, a.child should already be known to point to obj2
    // and a.child.child should be set (but depth-1 only, so just a.child visible)
    static void process(Node a) {
        Node fetched = a.child;       // LOAD: fetched -> obj2
        fetched.child = new Node();   // STORE: obj2.child -> obj3
    }

    public static void main(String[] args) {
        Node n1 = new Node();   // obj1
        Node n2 = new Node();   // obj2

        n1.child = n2;          // n1.child -> obj2

        process(n1);
        // Inside process:
        //   a.child -> obj2          (propagated from caller field fact)
        //   fetched -> obj2          (from LOAD a.child)
        //   fetched.child -> obj3    (from STORE)
        // After return:
        //   n1.child -> obj2 still   (unchanged)
        //   n2.child -> obj3         (updated via fetched alias)

        System.out.println(n1);
        System.out.println(n2);
    }
}
