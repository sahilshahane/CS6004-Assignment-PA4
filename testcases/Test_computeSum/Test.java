class LongSummer {
    long sum = 0;

    void add(long a, long b, long c, long d, long e, long e1, long e2, long e3, long e4, long e5) {
        sum += a + b + c + d + e + e1 + e2 + e3 + e4 + e5;
    }
}

public class Test {

    public static void main(String[] args) {
        LongSummer s = new Summer();

        for (long i = 0; i < 10000; i++) {
            s.add(System
                    .nanoTime(), System.nanoTime(), System.nanoTime(), System.nanoTime(), System.nanoTime(),
                    System.nanoTime(), System.nanoTime(), System.nanoTime(), System.nanoTime(), System.nanoTime());
        }

        System.out.println("Total sum is : " + s.sum);
    }
}
