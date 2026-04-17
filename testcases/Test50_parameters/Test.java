class T {
    public void A(T a1, T a2, T a3) {
        System.out.println("Print something");
    }
}

public class Test {
    public static void main(String[] args) {
        var t = new T();
        var obj1 = new T();
        var obj2 = new T();
        var obj3 = new T();

        t.A(obj1, obj2, obj3);
    }
}
