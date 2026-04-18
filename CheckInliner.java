import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.options.Options;
import soot.util.Chain;
import soot.jimple.toolkits.invoke.SiteInliner;
import java.util.*;
import soot.SceneTransformer;
import soot.*;
import soot.jimple.*;
import soot.jimple.InvokeStmt;

public class CheckInliner extends SceneTransformer {

    class MethodWrapper {
        SootMethod method;
        SootClass declaringClass;
        boolean couldBeInlined = true;

        public MethodWrapper(SootMethod method, SootClass declaringClass) {
            this.method = method;
            this.declaringClass = declaringClass;
        }
    }

    public void checkRecursionHelper(SootMethod method, HashMap<String, MethodWrapper> methodWrappers,
            LinkedList<MethodWrapper> callStack) {

        // add method to call stack
        MethodWrapper wrapper = methodWrappers.get(method.getSignature());
        callStack.add(wrapper);

        // iterate through all unit and check if there is a call to a method
        for (Unit unit : method.retrieveActiveBody().getUnits()) {
            if (unit instanceof InvokeStmt) {
                InvokeExpr invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
                SootMethod calledMethod = invokeExpr.getMethod();
                // check if called method is in methodWrappers
                if (methodWrappers.containsKey(calledMethod.getSignature())) {
                    MethodWrapper calledWrapper = methodWrappers.get(calledMethod.getSignature());
                    // check if called method is in call stack
                    if (callStack.contains(calledWrapper)) {
                        // if it is then there is a recursion and we can mark all methods in call stack
                        // as could not be inlined
                        for (MethodWrapper mw : callStack) {
                            mw.couldBeInlined = false;
                        }
                        // dont go ahead since none could be inlined
                        return;

                    } else {
                        // if it is not then we can continue checking for recursion in called method
                        checkRecursionHelper(calledMethod, methodWrappers, callStack);
                    }
                }
            }
        }

        // remove method from call stack , that will be the last element
        callStack.removeLast();

    }

    public void checkRecursion(SootMethod method, HashMap<String, MethodWrapper> methodWrappers) {
        // linkedlist to store call stack
        LinkedList<MethodWrapper> callStack = new LinkedList<>();

        // should pass a shallow copy of callstack
        checkRecursionHelper(method, methodWrappers, callStack);

    }

    @Override
    protected void internalTransform(String phaseName, Map<String, String> options) {
        // 🔹 Get all application classes
        Chain<SootClass> classes = Scene.v().getApplicationClasses();

        // store all methods in hashmap of methoddWrappers , that have a faster
        // searching time than soot's getMethodByName
        HashMap<String, MethodWrapper> methodWrappers = new HashMap<>();
        for (SootClass cls : classes) {
            System.out.println("Class: " + cls.getName());
            for (SootMethod method : cls.getMethods()) {
                // skip library methods
                if (method.isPhantom()) {
                    continue;
                }
                System.out.println("  Method: " + method.getSignature());
                System.out.println("  SubSignature: " + method.getSubSignature());
                if (method.isConcrete()) {
                    SootClass declaringClass = method.getDeclaringClass();
                    MethodWrapper wrapper = new MethodWrapper(method, declaringClass);
                    methodWrappers.put(method.getSignature(), wrapper);
                }
            }
        }

        // check for methods that can not be inlined
        for (MethodWrapper wrapper : methodWrappers.values()) {
            SootMethod method = wrapper.method;

            // check if method is recursive pass a shallow copy methodWrappers
            // if couldBeInlined is true , then only check , skips false
            if (wrapper.couldBeInlined) {
                checkRecursion(method, methodWrappers);
            }
        }

        // print all methods that can be inlined
        System.out.println("Methods that can be inlined:");
        for (MethodWrapper wrapper : methodWrappers.values()) {
            if (wrapper.couldBeInlined) {
                System.out.println(wrapper.method.getSignature());
            }
        }

        // print all methods that can not be inlined
        System.out.println("Methods that can not be inlined:");
        for (MethodWrapper wrapper : methodWrappers.values()) {
            if (!wrapper.couldBeInlined) {
                System.out.println(wrapper.method.getSignature());
            }
        }

        // inline all invokestatic if the method have true value for couldBeInlined
        for (SootClass cls : classes) {
            for (SootMethod method : cls.getMethods()) {
                if (method.isConcrete()) { // have code
                    Chain<Unit> units = method.retrieveActiveBody().getUnits();
                    Iterator<Unit> it = units.snapshotIterator();
                    while (it.hasNext()) {
                        Unit unit = it.next();

                        if (unit instanceof InvokeStmt) {
                            InvokeExpr invokeExpr = ((InvokeStmt) unit).getInvokeExpr();
                            SootMethod calledMethod = invokeExpr.getMethod();

                            if (invokeExpr instanceof StaticInvokeExpr) {
                                if (methodWrappers.containsKey(calledMethod.getSignature())
                                        && methodWrappers.get(calledMethod.getSignature()).couldBeInlined) {
                                    System.out.println("Can inline: " + calledMethod.getSignature() + " in "
                                            + method.getSignature());
                                    SootMethod targetMethod = invokeExpr.getMethod();
                                    Stmt stmt = (Stmt) unit;
                                    try {
                                        SiteInliner.inlineSite(targetMethod, stmt, method);
                                    } catch (Exception e) {
                                        System.err.println("[Inliner Error] Failed to inline site: " + e.getMessage());
                                    }
                                } else {
                                    System.out.println("Cannot inline: " + calledMethod.getSignature() + " in "
                                            + method.getSignature());
                                }
                            }
                        }
                        // assignment statement with invoke expression on the right hand side
                        else if (unit instanceof AssignStmt) {
                            Value rightOp = ((AssignStmt) unit).getRightOp();
                            if (rightOp instanceof InvokeExpr) {
                                InvokeExpr invokeExpr = (InvokeExpr) rightOp;
                                SootMethod calledMethod = invokeExpr.getMethod();

                                if (invokeExpr instanceof StaticInvokeExpr) {
                                    if (methodWrappers.containsKey(calledMethod.getSignature())
                                            && methodWrappers.get(calledMethod.getSignature()).couldBeInlined) {
                                        System.out.println("Can inline: " + calledMethod.getSignature() + " in "
                                                + method.getSignature());
                                        SootMethod targetMethod = invokeExpr.getMethod();
                                        Stmt stmt = (Stmt) unit;
                                        try {
                                            SiteInliner.inlineSite(targetMethod, stmt, method);
                                        } catch (Exception e) {
                                            System.err.println(
                                                    "[Inliner Error] Failed to inline site: " + e.getMessage());
                                        }
                                    } else {
                                        System.out.println("Cannot inline: " + calledMethod.getSignature() + " in "
                                                + method.getSignature());
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }

    }
}