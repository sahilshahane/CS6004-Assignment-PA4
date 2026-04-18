class T {
    public void A() {
        System.out.println("Print something");

    }
}

class Tex extends T {
    public void A() {
        System.out.println("Print 2 something");
    }
}

public class Test {
    public static void main(String[] args) {
        T t = new Tex();
        t.A();
    }
}
