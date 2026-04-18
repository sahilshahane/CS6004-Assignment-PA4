interface Strategy {
    void execute();
}

class FastStrategy implements Strategy {
    public void execute() {
        System.out.println("FastStrategy");
    }
}

class SecureStrategy implements Strategy {
    public void execute() {
        System.out.println("SecureStrategy");
    }
}

public class Test {

    static void runLogic(Strategy s) {
        s.execute();
    }

    public static void main(String[] args) {
        runLogic(new FastStrategy());
        runLogic(new SecureStrategy());
    }
}
