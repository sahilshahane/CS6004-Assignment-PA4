package testcases.Test2;

interface Strategy {
    void execute();
}

class FastStrategy implements Strategy {
    public void execute() {
    }
}

class SecureStrategy implements Strategy {
    public void execute() {
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
