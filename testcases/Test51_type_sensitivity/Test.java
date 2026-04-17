class T {
    public void A() {
        System.out.println("Print something");

    }
}

class Tex extends T {

}

public class Test {
    public static void main(String[] args) {
        T t;

        if ((new Object()) instanceof Object) {
            t = new T();
        } else {
            t = new Tex();
        }

        t.A();
    }
}
