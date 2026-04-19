public class MyRuntimeMetrics {
    public static long staticCalls = 0;
    public static long instanceCalls = 0;

    public static void logStatic() {
        staticCalls++;
    }

    public static void logInstance() {
        instanceCalls++;
    }

    // Call this at program exit to see results
    public static void printReport() {
        System.out.println("Static Calls: " + staticCalls);
        System.out.println("Instance Calls: " + instanceCalls);
    }
}
