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

    static void prime(T a) {
        a = new T();
        a.A();
    }

    static void call(T a) {
        a.A();
    }

    public static void main(String[] args) {
        T t = new Tex();
        prime(t);
        prime(new T());

        call(t);
        call(new T());
    }
}
