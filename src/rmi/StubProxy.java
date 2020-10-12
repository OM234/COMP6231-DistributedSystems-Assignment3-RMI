package rmi;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;

public class StubProxy implements InvocationHandler {

    Object target;
    Skeleton skeleton;

    public StubProxy(){
        target = null;
        skeleton = null;
    }

    public StubProxy(Object object, Skeleton skeleton){
        target = object;
        this.skeleton = skeleton;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        String name = method.getName();

        if(method.getName().equals("equals")) {
            return isEqual(args);
        }

        if(method.getName().equals("hashCode")) {
            return Objects.hash(skeleton);
        }

        if(method.getName().equals("toString")) {
            return (this.toString());
        }

        if (skeleton == null || target == null) {
            throw new RMIException("No skeleton or target");
        }

        try {
            return method.invoke(target, args);
        }
        catch (InvocationTargetException e) {

            throw e.getTargetException();
        }
//        catch (Exception e) {
//            e.printStackTrace();
//            throw new RMIException("Method does not exist");
//        }
        //return null;
    }

    private boolean isEqual(Object[] args) {
        //comparing an initialized object to null, so return false
        if(args[0] == null) {
            return false;
        }

        //If the other object has the same skeleton, return true
        if(args[0].equals(skeleton)) {
            return true;
        } else {
            return false;
        }
    }

//    public static <T> T withProxy(T target, Class<T> intf) {
//
//        return (T) Proxy.newProxyInstance(
//                intf.getClassLoader(),
//                new Class<?>[]{intf},
//                new StubProxy(target, 10));
//    }
}
