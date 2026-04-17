class T {
    public void A() {
        B();
    }

    public void B() {
        C();
    }

    public void C() {
        D();
    }

    public void D() {
        System.out.println("sike");
    }
}

public class Test {
    public static void main(String[] args) {
        var t = new T();
        t.A();
    }
}
