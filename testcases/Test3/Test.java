package testcases.Test3;

class A {
    public void dosome(A that) {
        that.dosome(this);
    }
}

public class Test {
    public static void main(String[] args) {
        A a = new A();
        a.dosome(a);
    }
}
