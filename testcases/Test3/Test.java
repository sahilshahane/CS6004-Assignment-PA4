
class A {
    static int recursion_stopper = 0;
    public void dosome(A that) {
        if (recursion_stopper < 5) {
            recursion_stopper++;
            that.dosome(that);
        }
    }
}

public class Test {
    public static void main(String[] args) {
        A a = new A();
        a.dosome(a);
    }
}
