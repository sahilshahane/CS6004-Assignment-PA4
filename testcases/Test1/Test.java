
class Event {
    public void dosome() {
    }
}

class SubEvent1 extends Event {
    public void dosome() {
        System.out.println("SubEvent1");
    }
}

class SubEvent2 extends Event {
    public void dosome() {
        System.out.println("SubEvent1");
    }
}

public class Test {
    public static void main(String[] args) {
        Event b;

        if ((new Object()) instanceof Object) {
            b = new SubEvent1();
            b.dosome();
        } else {
            b = new SubEvent2();
            b.dosome();
        }
    }
}
