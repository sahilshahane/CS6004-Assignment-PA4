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

class Tex1 extends T {
    public void A() {
        System.out.println("Print 3 something");
    }
}

class Tex2 extends T {
    public void A() {
        System.out.println("Print 4 something");
    }
}

class Tex3 extends T {
    public void A() {
        System.out.println("Print 4 something");
    }
}

class Tex4 extends T {
    public void A() {
        System.out.println("Print 4 something");
    }
}

public class Test {
    public static void main(String[] args) {
        T t;

        if ((System.out.hashCode()) > 01) {
            t = new T();
        } else {
            t = new Tex();
        }

        t.A();

        if ((System.out.hashCode()) > 01) {
            t = new Tex1();

            if ((System.out.hashCode()) > 01) {
                t = new Tex3();
            } else if ((System.out.hashCode()) > 01) {
                t = new Tex4();
            }

        } else if ((System.out.hashCode()) > 01) {
            t = new Tex2();
        }

        t = new T();
        t.A();
    }
}
