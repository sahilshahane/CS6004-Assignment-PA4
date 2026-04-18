class T {
    T f;
}

class Test {

    public static void main(String[] args) {
        T x = new T();
        T y = x; // alias
        T z = new T();
        y.f = z; // store through alias
        System.out.println(x.f);
    }
}