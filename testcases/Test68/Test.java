public class Test {
    public void A() {
        System.out.println("In A");
    }

    public void B() {
        System.out.println("In B");
    }

    public static void main(String[] args) {
        int counter = 0;
        Test test = new Test();
        for (int i = 0; i < 100000; i++) {
            if (counter % 1 == 0) {
                test.A();
            } else {
                test.B();
            }
            counter += i;
        }

        System.out.println("Hello World");
    }
}