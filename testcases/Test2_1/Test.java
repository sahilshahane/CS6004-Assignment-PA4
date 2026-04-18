
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class Test {

    static void printAll(List<String> list) {
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            System.out.println(it.next());
        }
    }

    public static void main(String[] args) {
        List<String> arr = new ArrayList<>();

        arr.add("C");
        arr.add("O");
        arr.add("O");
        arr.add("L");

        printAll(arr);
    }

}
