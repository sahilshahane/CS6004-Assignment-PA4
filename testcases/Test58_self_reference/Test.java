class Node { Node child; }

public class Test {
    public static void main(String[] args) {
        Node n1 = new Node();  // obj1
        n1.child = n1;         // n1.child -> obj1 (self-reference)

        // n1.child should point to obj1 (same as n1)
        System.out.println(n1);
    }
}
