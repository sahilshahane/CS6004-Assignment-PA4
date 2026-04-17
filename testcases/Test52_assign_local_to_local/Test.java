class T {

}

public class Test {
    public static void main(String[] args) {
        T t = new T();

        T t3 = new T();

        if (System.out instanceof Object) {
            t3 = t;
        }

        System.out.println(t3);

    }
}
