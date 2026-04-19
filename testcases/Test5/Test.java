// Source code is decompiled from a .class file using FernFlower decompiler (from Intellij IDEA).
class Test {

   static int recursion_stopper = 0;
   Test() {
   }

   public static void t1(int var0) {
        if (recursion_stopper < 5) {
            recursion_stopper++;
            t1(var0 + 1);
        }
   }

   public static void t2(int var0) {
   }

   public static void t2(String var0, int var1) {
   }

   public static void t2(int var0, String var1) {
      t1(var0);
   }

   public static int t3(int var0) {
      return var0 + 1;
   }

   public static void main(String[] var0) {
      byte var1 = 5;
      int var2 = 10;
      var2 += var1;
      t1(var2);
      t2(var1);
      t2(var1, "hello");
      int var3 = t3(var2);
      t3(var3);
      var2 = t3(var3);
   }
}
