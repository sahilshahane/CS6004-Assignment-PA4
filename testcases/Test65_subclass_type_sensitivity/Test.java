class Base { Base f; }
class Sub extends Base { Base g; }

public class Test {
    public static void main(String[] args) {
        Sub  s  = new Sub();    // obj1 (Sub)
        Base b  = s;            // alias of obj1 as Base
        Base v1 = new Base();   // obj2
        Base v2 = new Base();   // obj3

        b.f = v1;       // obj1.f -> obj2 (via Base view)
        s.g = v2;       // obj1.g -> obj3 (Sub-only field)

        // Expected: s.f -> obj2, b.f -> obj2 (same object), s.g -> obj3
        System.out.println(s);
        System.out.println(b);
    }
}
