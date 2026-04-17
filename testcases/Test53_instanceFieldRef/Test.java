class T {
    T a;
}

public class Test {
    public static void main(String[] args) {
        T t = new T();
        t.a = new T();

        t.a.a = new T();

        T t3 = t.a;

        System.out.println(t3);

    }
}
